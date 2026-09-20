package cn.nukkit.network.session;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.NetherNetInterface;
import cn.nukkit.network.Network;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.DisconnectPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.login.NetworkSessionState;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.utils.BinaryStream;
import com.google.common.base.Preconditions;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.PlatformDependent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.cloudburstmc.netty.channel.nethernet.NetherNetChildChannel;

import java.net.InetAddress;
import java.util.*;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * NetherNet（WebRTC）传输承载的玩家会话。
 * <p>
 * 每个装配完成的数据通道消息即一个 batch，内容与 RakNet {@code 0xFE} 帧后的字节一致（协商后带压缩前缀）；
 * DTLS 已加密，Bedrock 加密握手在此永不启用。
 * <p>
 * Adapted from PowerNukkitX NetherNet support and RakNetPlayerSession.
 */
@Log4j2
public class NetherNetPlayerSession extends SimpleChannelInboundHandler<ByteBuf> implements NetworkPlayerSession {

    /** NetherNet clients are all on the newest RakNet protocol revision. */
    public static final int RAKNET_PROTOCOL = 11;

    private final NetherNetInterface server;
    private final NetherNetChildChannel channel;

    private final Queue<DataPacket> inbound = PlatformDependent.newSpscQueue();
    private final AtomicInteger queuedInboundPackets = new AtomicInteger();
    private final AtomicLong queuedInboundBytes = new AtomicLong();
    private final Queue<DataPacket> outbound = PlatformDependent.newMpscQueue();
    private final ScheduledFuture<?> tickFuture;
    private final NetworkSessionState state = new NetworkSessionState();

    private Player player;
    /** 事件循环线程写、主线程读，跨线程可见性必需。 Written on the event loop, read by the main thread. */
    private volatile String disconnectReason = null;
    /**
     * 传输激活过又失活即远端关闭。NetherNetChannel 感知远端关闭时先置 inactive 再 close()，
     * Netty 的 close 记账（wasActive=false）不会补发 channelInactive，须自行感知（见 networkTick）。
     * A once-active channel going inactive is a remote close: the library flips inactive before
     * close(), so Netty never fires channelInactive and the session must notice on its own.
     */
    private volatile boolean transportWasActive;

    private CompressionProvider compressionIn;
    private CompressionProvider compressionOut;
    private boolean compressionInitialized;

    private final ArrayDeque<Long> malformedBatches = new ArrayDeque<>();
    private long lastMalformedLogNanos;
    private long lastInboundThrottleLogNanos;
    private long throttledInboundBatches;
    private volatile boolean inboundThrottled;
    private double ingressBatchTokens = RakNetPlayerSession.MAX_INGRESS_BATCH_TOKENS;
    private double ingressCompressedBytes = RakNetPlayerSession.MAX_INGRESS_COMPRESSED_BYTES;
    private double ingressDecodeBytes = RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES;
    private double ingressFrameTokens = RakNetPlayerSession.MAX_INGRESS_FRAME_TOKENS;
    private long lastIngressRefillNanos;

    public NetherNetPlayerSession(NetherNetInterface server, NetherNetChildChannel channel) {
        this.server = server;
        this.channel = channel;
        this.tickFuture = channel.eventLoop().scheduleAtFixedRate(this::networkTick, 0, 20, TimeUnit.MILLISECONDS);
        // 关闭完成即置 disconnectReason：channelInactive 因上述时序不可靠，closeFuture 是可靠信号源
        // The completed close marks the session disconnected: channelInactive is unreliable here,
        // while the close future always fires
        channel.closeFuture().addListener(future -> this.disconnect("transport:closed_by_remote_peer"));

        // RakNet 协议 11：NetworkSettings 协商前 batch 以原始格式到达
        // RakNet protocol 11: batches arrive raw until NetworkSettings negotiates compression
        this.compressionIn = CompressionProvider.NONE;
        this.compressionOut = CompressionProvider.NONE;

        long acceptedAt = System.nanoTime();
        this.lastIngressRefillNanos = acceptedAt;
        this.state.getConnection().setSessionCreatedNanos(acceptedAt);
        this.state.getConnection().setChildChannelAcceptedNanos(acceptedAt);
        this.state.getConnection().setRemoteAddress(String.valueOf(channel.remoteAddress()));
        this.state.getProtocol().setRaknetProtocol(RAKNET_PROTOCOL);
        this.state.getSecurity().setCompressionIn(this.compressionIn);
        this.state.getSecurity().setCompressionOut(this.compressionOut);
        this.state.getSecurity().setCompressionInitialized(false);
        this.state.getSecurity().setPrefixedCompression(true);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, ByteBuf payload) throws Exception {
        if (this.disconnectReason != null) {
            return;
        }
        int len = payload.readableBytes();
        if (len > RakNetPlayerSession.MAX_INBOUND_WIRE_BYTES) {
            log.warn("[{}] Closing session: inbound message of {} bytes exceeds hard limit {}",
                    this.playerLabel(), len, RakNetPlayerSession.MAX_INBOUND_WIRE_BYTES);
            this.disconnect("Too big packet");
            this.channel.close();
            return;
        }

        long ingressNowNanos = System.nanoTime();
        if (!this.reserveWireIngressBudget(len, ingressNowNanos)) {
            this.rejectWireIngress(len);
            return;
        }

        boolean ci = this.shouldUsePrefixedCompression();

        if (this.inboundThrottled) {
            this.dropInboundBatchBecauseThrottled(len);
            return;
        }
        if (!this.reserveDecodeIngressBudget(ingressNowNanos)) {
            this.dropInboundBatchBecauseThrottled(len);
            return;
        }

        byte[] packetBuffer = new byte[len];
        payload.readBytes(packetBuffer);

        RakNetPlayerSession.InboundBatchDecodeResult decoded = this.processInboundBatch(packetBuffer, ci);
        this.settleIngressBudget(decoded.workBytes(), decoded.framedPackets(), ingressNowNanos);
        if (decoded.success()) {
            this.enqueueDecodedBatch(decoded.packets());
            return;
        }
        if (this.isPlayingSession()) {
            if (!this.keepPlayingSessionAfterMalformedBatch(ingressNowNanos)) {
                this.disconnect("Sent malformed packet");
            }
            return;
        }

        InetAddress malformedAddress = this.channel.remoteAddress() instanceof java.net.InetSocketAddress address
                ? address.getAddress() : null;
        boolean blockMalformedAddress = RakNetPlayerSession.shouldBlockAddressAfterMalformed(
                this.state.getLogin().getPhase(), malformedAddress);
        this.disconnect("Sent malformed packet");
        Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
            try {
                this.channel.close();
                if (blockMalformedAddress && malformedAddress != null) {
                    this.server.blockAddress(malformedAddress, 60);
                }
            } catch (Throwable throwable) {
                if (Nukkit.DEBUG > 1) {
                    log.info("Error while closing channel", throwable);
                }
            }
        }, 10);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        if (!this.state.getConnection().isQueuedForPlayerCreation()) {
            this.server.queueSessionForPlayerCreation(this);
        }
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) {
        this.disconnect("transport:closed_by_remote_peer");
    }

    @Override
    public void disconnect(String reason) {
        if (this.disconnectReason != null) {
            return;
        }

        this.disconnectReason = reason;
        this.state.getLogin().setDisconnectCauseHint(reason);
        this.state.getLogin().setPhase(SessionLoginPhase.DISCONNECTED);
        if (this.tickFuture != null) {
            this.tickFuture.cancel(false);
        }

        // 稍作延迟，确保断连消息送达后再关闭
        // Give it a short time to make sure the disconnect message is delivered
        this.channel.eventLoop().schedule(() -> this.channel.close(), 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendPacket(DataPacket packet) {
        if (!this.channel.isActive()) {
            return;
        }
        this.outbound.offer(packet);
    }

    @Override
    public void sendImmediatePacket(DataPacket packet, Runnable callback, ImmediatePacketMode mode) {
        if (!this.channel.isActive()) {
            return;
        }

        switch (mode) {
            case QUEUED_FLUSH -> {
                this.sendPacket(packet);
                this.channel.eventLoop().execute(() -> {
                    this.networkTick();
                    callback.run();
                });
            }
            case DIRECT_WRITE -> this.channel.eventLoop().execute(() -> {
                try {
                    packet.tryEncode();
                    ChannelFuture future = this.sendSinglePacketNow(packet);
                    future.addListener(result -> {
                        if (result.isSuccess()) {
                            callback.run();
                        } else {
                            log.warn("Failed to send direct packet {} to {}",
                                    packet.getClass().getSimpleName(), this.channel.remoteAddress(), result.cause());
                            this.disconnect("Internal Server Error");
                        }
                    });
                } catch (Throwable throwable) {
                    log.warn("Failed to prepare direct packet {} for {}",
                            packet.getClass().getSimpleName(), this.channel.remoteAddress(), throwable);
                    this.disconnect("Internal Server Error");
                }
            });
        }
    }

    private void networkTick() {
        if (!this.channel.isActive()) {
            // 激活过的会话失活即远端关闭；未激活过的（握手期）交给登录超时，勿在此误杀
            // Inactive after once active is a remote close; never-active sessions belong to the
            // login timeout instead
            if (this.transportWasActive) {
                this.disconnect("transport:closed_by_remote_peer");
            }
            return;
        }
        this.transportWasActive = true;

        try {
            List<DataPacket> toBatch = new ObjectArrayList<>();
            DataPacket packet;
            while ((packet = this.outbound.poll()) != null) {
                if (packet instanceof DisconnectPacket) {
                    packet.tryEncode();
                    BinaryStream batched = new BinaryStream();
                    byte[] buf = packet.getBuffer();
                    batched.putUnsignedVarInt(buf.length);
                    batched.put(buf);

                    try {
                        this.sendPacket(this.compressionOut.compress(batched, Server.getInstance().networkCompressionLevel));
                    } catch (Exception e) {
                        log.error("Unable to compress disconnect packet", e);
                    }
                    return; // Disconnected
                } else if (packet instanceof BatchPacket) {
                    if (!toBatch.isEmpty()) {
                        this.sendPackets(toBatch);
                        toBatch.clear();
                    }

                    this.sendPacket(((BatchPacket) packet).payload);
                } else {
                    toBatch.add(packet);
                }
            }

            if (!toBatch.isEmpty()) {
                this.sendPackets(toBatch);
            }
        } catch (Throwable e) {
            log.error("[{}] Failed to tick NetherNetPlayerSession", this.channel.remoteAddress(), e);
        }
    }

    public RakNetPlayerSession.InboundDrain serverTick(int packetBudget, long byteBudget) {
        DataPacket packet;
        int handled = 0;
        long handledBytes = 0L;
        int allowedPackets = Math.min(RakNetPlayerSession.MAX_INBOUND_PACKETS_PER_SERVER_TICK, Math.max(packetBudget, 0));
        long allowedBytes = Math.min(RakNetPlayerSession.MAX_INBOUND_BYTES_PER_SERVER_TICK, Math.max(byteBudget, 0L));
        while (handled < allowedPackets
                && (packet = this.inbound.peek()) != null
                && handledBytes + packetBytes(packet) <= allowedBytes) {
            this.inbound.poll();
            this.queuedInboundPackets.decrementAndGet();
            int packetBytes = packetBytes(packet);
            this.queuedInboundBytes.addAndGet(-packetBytes);
            handled++;
            handledBytes += packetBytes;
            try {
                this.player.handleDataPacket(packet);
            } catch (Throwable e) {
                log.error(new FormattedMessage("An error occurred whilst handling {} for {}",
                        new Object[]{packet.getClass().getSimpleName(), this.player.getName()}, e));
            }
        }
        if (this.inboundThrottled
                && this.queuedInboundPackets.get() <= RakNetPlayerSession.INBOUND_LOW_WATER_PACKETS
                && this.queuedInboundBytes.get() <= RakNetPlayerSession.INBOUND_LOW_WATER_BYTES) {
            this.inboundThrottled = false;
        }
        return new RakNetPlayerSession.InboundDrain(handled, handledBytes);
    }

    private void sendPackets(Collection<DataPacket> packets) {
        BinaryStream batched = new BinaryStream();
        for (DataPacket packet : packets) {
            if (packet instanceof BatchPacket) {
                throw new IllegalArgumentException("Cannot batch BatchPacket");
            }
            packet.tryEncode();

            byte[] buf = packet.getBuffer();
            if (batched.getCount() + buf.length > 3145728) { // 3 * 1024 * 1024
                this.sendPackets(batched);
                batched = new BinaryStream();
            }
            batched.putUnsignedVarInt(buf.length);
            batched.put(buf);
        }

        this.sendPackets(batched);
    }

    private void sendPackets(BinaryStream batched) {
        try {
            this.sendPacket(this.compressionOut.compress(batched, Server.getInstance().networkCompressionLevel));
        } catch (Exception e) {
            log.error("Unable to compress batched packets", e);
        }
    }

    private ChannelFuture sendSinglePacketNow(DataPacket packet) throws Exception {
        BinaryStream batched = new BinaryStream();
        byte[] buf = packet.getBuffer();
        batched.putUnsignedVarInt(buf.length);
        batched.put(buf);
        return this.sendPacket(this.compressionOut.compress(batched, Server.getInstance().networkCompressionLevel));
    }

    /**
     * 写出一个压缩 batch：载荷即 RakNet {@code 0xFE} 帧后的字节（协商后为压缩前缀 + 压缩体）。
     * Writes one compressed batch: exactly the bytes RakNet puts behind its {@code 0xFE} frame id.
     */
    private ChannelFuture sendPacket(byte[] compressedPayload) {
        boolean ci = this.shouldUsePrefixedCompression();

        ByteBuf finalPayload = ByteBufAllocator.DEFAULT.directBuffer((ci ? 1 : 0) + compressedPayload.length);
        if (ci) {
            finalPayload.writeByte(this.compressionOut.getPrefix());
        }
        finalPayload.writeBytes(compressedPayload);

        return this.channel.writeAndFlush(finalPayload);
    }

    @Override
    public void setCompression(CompressionProvider compression) {
        Preconditions.checkNotNull(compression, "compression");
        this.compressionIn = compression;
        this.compressionOut = compression;
        this.compressionInitialized = true;
        this.state.getSecurity().setCompressionIn(compression);
        this.state.getSecurity().setCompressionOut(compression);
        this.state.getSecurity().setCompressionInitialized(true);
    }

    @Override
    public void setCompressionOut(CompressionProvider compression) {
        Preconditions.checkArgument(!this.state.getSecurity().isCompressionInitialized(), "compressionOut cannot be set after compression has been initialized");
        Preconditions.checkNotNull(compression, "compression");
        this.compressionOut = compression;
        this.state.getSecurity().setCompressionOut(compression);
    }

    @Override
    public CompressionProvider getCompression() {
        return this.compressionOut;
    }

    @Override
    public NetworkSessionState getState() {
        return this.state;
    }

    @Override
    public void beginLegacyInboundCompressionGraceWindow() {
        this.beginLegacyInboundCompressionGraceWindow(this.compressionIn);
    }

    @Override
    public void beginLegacyInboundCompressionGraceWindow(CompressionProvider compression) {
        Preconditions.checkNotNull(compression, "compression");
        this.state.getSecurity().setLegacyInboundCompression(compression);
        this.state.getSecurity().setLegacyInboundGraceWindow(true);
    }

    @Override
    public void endLegacyInboundCompressionGraceWindow() {
        this.state.getSecurity().setLegacyInboundGraceWindow(false);
        this.state.getSecurity().setLegacyInboundCompression(null);
    }

    public void setPlayer(Player player) {
        Preconditions.checkArgument(this.player == null && player != null);
        this.player = player;
        this.state.getConnection().setPlayerBound(true);
        this.state.getConnection().setPlayerBoundNanos(System.nanoTime());
        this.state.getProtocol().setGameVersion(player.getGameVersion());
        this.state.getLogin().setShouldLogin(player.shouldLogin());
    }

    @Override
    public Player getPlayer() {
        return this.player;
    }

    public NetherNetChildChannel getChannel() {
        return this.channel;
    }

    public String getDisconnectReason() {
        return this.disconnectReason;
    }

    public boolean isPendingLoginTimedOut(long nowNanos, int timeoutMillis) {
        return RakNetPlayerSession.isPendingLoginTimedOut(
                this.channel.isActive(),
                this.state.getLogin().getPhase(),
                this.state.getLogin().getLastActivityNanos(),
                this.state.getConnection().getChildChannelAcceptedNanos(),
                nowNanos,
                timeoutMillis
        );
    }

    public boolean isLoginPhaseTimedOut(long nowNanos, int timeoutMillis) {
        return RakNetPlayerSession.isLoginPhaseTimedOut(this.state.getLogin().getPhase(), this.state.getLogin().getLastActivityNanos(), nowNanos, timeoutMillis);
    }

    /**
     * NetherNet 上禁止 Bedrock 加密：会话已由 DTLS 承载，真实客户端会以明文回应握手导致断连。
     * Bedrock packet encryption must never run here: DTLS already secures the session.
     */
    @Override
    public void setEncryption(javax.crypto.SecretKey encryptionKey, javax.crypto.Cipher encryptionCipher, javax.crypto.Cipher decryptionCipher) {
        log.warn("Ignoring Bedrock packet encryption for {}: NetherNet already carries the session inside DTLS", this.playerLabel());
    }

    @Override
    public void beginLegacyInboundEncryptionGraceWindow() {
        // 本传输不启用加密，无需过渡窗口
        // Encryption never starts on this transport, no window to bridge
    }

    @Override
    public void endLegacyInboundEncryptionGraceWindow() {
    }

    @Override
    public long getPing() {
        return this.channel.getPing();
    }

    private int getBedrockProtocol() {
        if (this.player != null) {
            return this.player.protocol;
        }
        cn.nukkit.GameVersion gameVersion = this.state.getProtocol().getGameVersion();
        if (gameVersion != null) {
            return gameVersion.getProtocol();
        }
        return Integer.MAX_VALUE;
    }

    private boolean shouldUsePrefixedCompression() {
        int protocol = this.getBedrockProtocol();
        return this.compressionInitialized
                && protocol != Integer.MAX_VALUE
                && protocol >= ProtocolInfo.v1_20_60;
    }

    private String playerLabel() {
        return this.player == null ? String.valueOf(this.channel.remoteAddress()) : this.player.getName();
    }

    private RakNetPlayerSession.InboundBatchDecodeResult processInboundBatch(byte[] packetBuffer, boolean ci) {
        try {
            if (!ci) {
                List<DataPacket> decoded = new ObjectArrayList<>();
                Network.BatchProcessResult measured = this.server.getNetwork().processBatchMeasured(
                        packetBuffer, decoded, this.compressionIn, RAKNET_PROTOCOL, this.player, false);
                if (!measured.success()) {
                    this.logMalformedBatch(measured.failure(),
                            "[{}] Failed to decode batch packet ({} bytes, non-prefixed, compression={})",
                            this.playerLabel(), packetBuffer.length, this.compressionIn);
                    return RakNetPlayerSession.InboundBatchDecodeResult.failed(
                            measured.decompressedBytes(), measured.framedPackets(), measured.failure());
                }
                return RakNetPlayerSession.InboundBatchDecodeResult.legacy(
                        this.compressionIn, decoded, measured.decompressedBytes(), measured.framedPackets());
            }

            RakNetPlayerSession.InboundBatchDecodeResult result = RakNetPlayerSession.decodeInboundPrefixedBatch(
                    this.server.getNetwork(),
                    packetBuffer,
                    this.state.getSecurity().getLegacyInboundCompression(),
                    this.state.getSecurity().isLegacyInboundGraceWindow(),
                    RAKNET_PROTOCOL,
                    this.player
            );
            if (!result.success()) {
                this.logMalformedBatch(result.failure(),
                        "[{}] Failed to decode batch packet ({} bytes, prefixed, prefix=0x{}, compressionIn={})",
                        this.playerLabel(), packetBuffer.length,
                        packetBuffer.length > 0 ? Integer.toHexString(packetBuffer[0] & 0xFF) : "-",
                        this.compressionIn);
                return result;
            }

            this.compressionIn = result.compression();
            this.state.getSecurity().setCompressionIn(result.compression());
            if (result.prefixed()) {
                this.endLegacyInboundCompressionGraceWindow();
            }
            return result;
        } catch (Exception e) {
            log.error("[{}] Unable to process batch packet", this.playerLabel(), e);
            return RakNetPlayerSession.InboundBatchDecodeResult.failed(
                    (int) RakNetPlayerSession.DECODE_BYTES_RESERVATION, (int) RakNetPlayerSession.FRAME_TOKENS_RESERVATION, e);
        }
    }

    private boolean isPlayingSession() {
        return this.player != null
                && this.state.getLogin().getPhase().ordinal() >= SessionLoginPhase.LOGGED_IN.ordinal()
                && this.state.getLogin().getPhase() != SessionLoginPhase.DISCONNECTED;
    }

    boolean keepPlayingSessionAfterMalformedBatch(long nowNanos) {
        if (this.player == null
                || this.state.getLogin().getPhase().ordinal() < SessionLoginPhase.LOGGED_IN.ordinal()
                || this.state.getLogin().getPhase() == SessionLoginPhase.DISCONNECTED) {
            return false;
        }
        long cutoff = nowNanos - RakNetPlayerSession.MALFORMED_BATCH_WINDOW_NANOS;
        while (!this.malformedBatches.isEmpty() && this.malformedBatches.peekFirst() <= cutoff) {
            this.malformedBatches.removeFirst();
        }
        this.malformedBatches.addLast(nowNanos);
        return this.malformedBatches.size() <= RakNetPlayerSession.MAX_MALFORMED_BATCHES_WHILE_PLAYING;
    }

    boolean enqueueDecodedBatch(List<DataPacket> packets) {
        if (packets.isEmpty()) {
            return true;
        }
        long batchBytes = 0L;
        for (DataPacket packet : packets) {
            batchBytes += packetBytes(packet);
        }
        int queuedPackets = this.queuedInboundPackets.get();
        long queuedBytes = this.queuedInboundBytes.get();
        if (queuedPackets + packets.size() > RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS
                || queuedBytes + batchBytes > RakNetPlayerSession.MAX_QUEUED_INBOUND_BYTES) {
            this.inboundThrottled = true;
            this.logInboundThrottle(queuedPackets, queuedBytes, packets.size(), batchBytes);
            return false;
        }

        this.queuedInboundPackets.addAndGet(packets.size());
        this.queuedInboundBytes.addAndGet(batchBytes);
        this.inbound.addAll(packets);
        if (queuedPackets + packets.size() >= RakNetPlayerSession.MAX_QUEUED_INBOUND_PACKETS
                || queuedBytes + batchBytes >= RakNetPlayerSession.MAX_QUEUED_INBOUND_BYTES) {
            this.inboundThrottled = true;
        }
        return true;
    }

    boolean reserveWireIngressBudget(int wireBytes, long nowNanos) {
        this.refillIngressBudget(nowNanos);
        if (this.ingressBatchTokens < 1D
                || this.ingressCompressedBytes < wireBytes) {
            return false;
        }
        this.ingressBatchTokens -= 1D;
        this.ingressCompressedBytes -= wireBytes;
        return true;
    }

    boolean reserveDecodeIngressBudget(long nowNanos) {
        this.refillIngressBudget(nowNanos);
        if (this.ingressDecodeBytes < RakNetPlayerSession.DECODE_BYTES_RESERVATION
                || this.ingressFrameTokens < RakNetPlayerSession.FRAME_TOKENS_RESERVATION) {
            return false;
        }
        this.ingressDecodeBytes -= RakNetPlayerSession.DECODE_BYTES_RESERVATION;
        this.ingressFrameTokens -= RakNetPlayerSession.FRAME_TOKENS_RESERVATION;
        return true;
    }

    void settleIngressBudget(int decodedBytes, int framedPackets, long nowNanos) {
        this.refillIngressBudget(nowNanos);
        this.ingressDecodeBytes = Math.max(0D, Math.min(RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES,
                this.ingressDecodeBytes + RakNetPlayerSession.DECODE_BYTES_RESERVATION - decodedBytes));
        this.ingressFrameTokens = Math.max(0D, Math.min(RakNetPlayerSession.MAX_INGRESS_FRAME_TOKENS,
                this.ingressFrameTokens + RakNetPlayerSession.FRAME_TOKENS_RESERVATION - framedPackets));
    }

    private void refillIngressBudget(long nowNanos) {
        if (nowNanos <= this.lastIngressRefillNanos) {
            return;
        }
        double elapsedSeconds = (nowNanos - this.lastIngressRefillNanos) / 1_000_000_000D;
        this.lastIngressRefillNanos = nowNanos;
        this.ingressBatchTokens = Math.min(RakNetPlayerSession.MAX_INGRESS_BATCH_TOKENS,
                this.ingressBatchTokens + elapsedSeconds * RakNetPlayerSession.INGRESS_BATCH_TOKENS_PER_SECOND);
        this.ingressCompressedBytes = Math.min(RakNetPlayerSession.MAX_INGRESS_COMPRESSED_BYTES,
                this.ingressCompressedBytes + elapsedSeconds * RakNetPlayerSession.INGRESS_COMPRESSED_BYTES_PER_SECOND);
        this.ingressDecodeBytes = Math.min(RakNetPlayerSession.MAX_INGRESS_DECODE_BYTES,
                this.ingressDecodeBytes + elapsedSeconds * RakNetPlayerSession.INGRESS_DECODE_BYTES_PER_SECOND);
        this.ingressFrameTokens = Math.min(RakNetPlayerSession.MAX_INGRESS_FRAME_TOKENS,
                this.ingressFrameTokens + elapsedSeconds * RakNetPlayerSession.INGRESS_FRAME_TOKENS_PER_SECOND);
    }

    private void rejectWireIngress(int wireBytes) {
        log.warn("[{}] Wire ingress budget exhausted (wireBytes={}); closing",
                this.playerLabel(), wireBytes);
        this.disconnect("Too much inbound data");
        this.channel.close();
    }

    private static int packetBytes(DataPacket packet) {
        return Math.max(packet.getCount(), 1);
    }

    private void dropInboundBatchBecauseThrottled(int compressedBytes) {
        this.throttledInboundBatches++;
        long nowNanos = System.nanoTime();
        if (this.lastInboundThrottleLogNanos != 0L
                && nowNanos - this.lastInboundThrottleLogNanos < RakNetPlayerSession.DIAGNOSTIC_LOG_INTERVAL_NANOS) {
            return;
        }
        this.lastInboundThrottleLogNanos = nowNanos;
        log.warn("[{}] Inbound backpressure dropped batch (compressedBytes={}, queuedPackets={}, queuedBytes={}, droppedBatches={})",
                this.playerLabel(), compressedBytes, this.queuedInboundPackets.get(),
                this.queuedInboundBytes.get(), this.throttledInboundBatches);
    }

    private void logInboundThrottle(int queuedPackets, long queuedBytes, int batchPackets, long batchBytes) {
        this.throttledInboundBatches++;
        long nowNanos = System.nanoTime();
        if (this.lastInboundThrottleLogNanos != 0L
                && nowNanos - this.lastInboundThrottleLogNanos < RakNetPlayerSession.DIAGNOSTIC_LOG_INTERVAL_NANOS) {
            return;
        }
        this.lastInboundThrottleLogNanos = nowNanos;
        log.warn("[{}] Inbound backpressure rejected decoded batch (queuedPackets={}, queuedBytes={}, batchPackets={}, batchBytes={}, droppedBatches={})",
                this.playerLabel(), queuedPackets, queuedBytes, batchPackets, batchBytes,
                this.throttledInboundBatches);
    }

    private void logMalformedBatch(Throwable failure, String message, Object... arguments) {
        long nowNanos = System.nanoTime();
        if (this.lastMalformedLogNanos != 0L
                && nowNanos - this.lastMalformedLogNanos < RakNetPlayerSession.DIAGNOSTIC_LOG_INTERVAL_NANOS) {
            return;
        }
        this.lastMalformedLogNanos = nowNanos;
        Object[] withCause = Arrays.copyOf(arguments, arguments.length + 1);
        withCause[arguments.length] = failure == null ? "unknown" : failure.toString();
        log.warn(message + ", cause={}", withCause);
    }
}
