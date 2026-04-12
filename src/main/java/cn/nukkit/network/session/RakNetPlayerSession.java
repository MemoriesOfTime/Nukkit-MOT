package cn.nukkit.network.session;

import cn.nukkit.GameVersion;
import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.DisconnectPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.session.login.NetworkSessionState;
import cn.nukkit.network.session.login.SessionLoginPhase;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import com.google.common.base.Preconditions;
import com.nukkitx.natives.sha256.Sha256;
import com.nukkitx.natives.util.Natives;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.internal.PlatformDependent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.extern.log4j.Log4j2;
import org.apache.logging.log4j.message.FormattedMessage;
import org.cloudburstmc.netty.channel.raknet.RakChildChannel;
import org.cloudburstmc.netty.channel.raknet.packet.RakMessage;
import org.cloudburstmc.netty.handler.codec.raknet.common.RakSessionCodec;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
public class RakNetPlayerSession extends SimpleChannelInboundHandler<RakMessage> implements NetworkPlayerSession {

    private static final ThreadLocal<Sha256> HASH_LOCAL = ThreadLocal.withInitial(Natives.SHA_256);
    private static final ThreadLocal<byte[]> CHECKSUM_LOCAL = ThreadLocal.withInitial(() -> new byte[8]);

    private final RakNetInterface server;
    private final RakChildChannel channel;

    private final Queue<DataPacket> inbound = PlatformDependent.newSpscQueue();
    private final Queue<DataPacket> outbound = PlatformDependent.newMpscQueue();
    private final ScheduledFuture<?> tickFuture;
    private final NetworkSessionState state = new NetworkSessionState();

    private Player player;
    private String disconnectReason = null;

    private CompressionProvider compressionIn;
    private CompressionProvider compressionOut;
    private boolean compressionInitialized;

    private SecretKey encryptionKey;
    private Cipher encryptionCipher;
    private Cipher decryptionCipher;
    private final AtomicLong encryptCounter = new AtomicLong();
    private final AtomicLong decryptCounter = new AtomicLong();

    public RakNetPlayerSession(RakNetInterface server, RakChildChannel channel) {
        this.server = server;
        this.channel = channel;
        this.tickFuture = channel.eventLoop().scheduleAtFixedRate(this::networkTick, 0, 20, TimeUnit.MILLISECONDS);


        int protocolVersion = channel.config().getProtocolVersion();
        if (protocolVersion == 8 && Server.getInstance().netEaseMode) {
            this.compressionIn = CompressionProvider.NETEASE_UNKNOWN;
        } else {
            this.compressionIn = protocolVersion >= 11 ? CompressionProvider.NONE : (protocolVersion < 10 ? CompressionProvider.ZLIB : CompressionProvider.ZLIB_RAW);
        }
        this.compressionOut = this.compressionIn;
        long acceptedAt = System.nanoTime();
        this.state.getConnection().setSessionCreatedNanos(acceptedAt);
        this.state.getConnection().setChildChannelAcceptedNanos(acceptedAt);
        this.state.getConnection().setRemoteAddress(String.valueOf(channel.remoteAddress()));
        this.state.getConnection().setRakCookieMode(String.valueOf(Server.getInstance().rakCookieMode));
        this.state.getProtocol().setRaknetProtocol(protocolVersion);
        this.state.getSecurity().setCompressionIn(this.compressionIn);
        this.state.getSecurity().setCompressionOut(this.compressionOut);
        this.state.getSecurity().setCompressionInitialized(false);
        this.state.getSecurity().setPrefixedCompression(protocolVersion >= 11);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RakMessage msg) throws Exception {
        ByteBuf buffer = msg.content();
        short packetId = buffer.readUnsignedByte();
        if (packetId == 0xfe) {
            int len = buffer.readableBytes();
            if (len > 12582912) {
                Server.getInstance().getLogger().error("Received too big packet: " + len);
                if (this.player != null) {
                    this.player.close("Too big packet");
                }
                return;
            }

            byte[] packetBuffer;
            boolean ci = this.shouldUsePrefixedCompression();

            if (this.decryptionCipher != null) {
                try {
                    ByteBuffer buf = buffer.nioBuffer();
                    this.decryptionCipher.update(buf, buf.duplicate());
                } catch (Exception e) {
                    log.error("Packet decryption failed for {}", player.getName(), e);
                    return;
                }

                // Verify the checksum
                buffer.markReaderIndex();
                int trailerIndex = buffer.writerIndex() - 8;
                byte[] checksum = CHECKSUM_LOCAL.get();
                try {
                    buffer.readerIndex(trailerIndex);
                    buffer.readBytes(checksum);
                } catch (Exception e) {
                    this.disconnect("Bad checksum");
                    log.debug("Unable to verify checksum", e);
                    return;
                }
                ByteBuf payload = buffer.slice(1, trailerIndex - 1);
                long count = this.decryptCounter.getAndIncrement();
                byte[] expected = this.calculateChecksum(count, payload);
                for (int i = 0; i < 8; i++) {
                    if (checksum[i] != expected[i]) {
                        this.disconnect("Invalid checksum");
                        log.debug("Encrypted packet {} has invalid checksum (expected {}, got {})",
                                count, Binary.bytesToHexString(expected), Binary.bytesToHexString(checksum));
                        return;
                    }
                }
                if (this.state.getSecurity().isLegacyInboundEncryptionGraceWindow()) {
                    this.endLegacyInboundEncryptionGraceWindow();
                }
                buffer.resetReaderIndex();

                packetBuffer = new byte[buffer.readableBytes() - 8];
            } else {
                packetBuffer = new byte[buffer.readableBytes()];
            }

            buffer.readBytes(packetBuffer);

            if (!this.processInboundBatch(packetBuffer, ci)) {
                this.disconnect("Sent malformed packet");
                Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                    try {
                        InetAddress address = this.channel.remoteAddress().getAddress();
                        this.channel.unsafe().close(this.channel.voidPromise());
                        if (shouldBlockAddressAfterMalformed(this.state.getLogin().getPhase(), address)) {
                            this.server.blockAddress(address, 60);
                        }
                    } catch (Throwable throwable) {
                        if (Nukkit.DEBUG > 1) {
                            log.info("Error while closing channel", throwable);
                        }
                    }
                }, 10);
            }
        } else if (Nukkit.DEBUG > 1) {
            log.info("Unknown EncapsulatedPacket: {}", packetId);
        }
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
        this.disconnect("Disconnected from Server"); // TODO: timeout reason
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

        // Give it a short time to make sure cancel message is delivered
        this.channel.eventLoop().schedule(() -> this.channel.close(), 10, TimeUnit.MILLISECONDS);
    }

    @Override
    public void sendPacket(DataPacket packet) {
        if (!this.channel.isActive()) {
            return;
        }

        if (packet.protocol != this.player.protocol) {
            log.warn("Wrong protocol used for {}! expected {} got{}", packet.getClass().getSimpleName(), this.player.protocol, packet.protocol);
        }

        this.outbound.offer(packet);
    }

    @Override
    public void sendImmediatePacket(DataPacket packet, Runnable callback) {
        this.sendImmediatePacket(packet, callback, ImmediatePacketMode.QUEUED_FLUSH);
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
            return;
        }

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
            log.error("[{}] Failed to tick RakNetPlayerSession", this.channel.remoteAddress(), e);
        }
    }

    public void serverTick() {
        DataPacket packet;
        while ((packet = this.inbound.poll()) != null) {
            try {
                Level level = this.player != null ? this.player.level : null;
                if (level != null && level.isParallelTickEnabled()) {
                    level.addSyncPacketToQueue(this.player, packet);
                } else {
                    this.player.handleDataPacket(packet);
                }
            } catch (Throwable e) {
                log.error(new FormattedMessage("An error occurred whilst handling {} for {}",
                        new Object[]{packet.getClass().getSimpleName(), this.player.getName()}, e));
            }
        }
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

    /**
     * Compresses and sends a single packet immediately.
     * Compression prefix handling is delegated to {@link #sendPacket(byte[])}.
     */
    private ChannelFuture sendSinglePacketNow(DataPacket packet) throws Exception {
        BinaryStream batched = new BinaryStream();
        byte[] buf = packet.getBuffer();
        batched.putUnsignedVarInt(buf.length);
        batched.put(buf);
        return this.sendPacket(this.compressionOut.compress(batched, Server.getInstance().networkCompressionLevel));
    }

    private ChannelFuture sendPacket(byte[] compressedPayload) {
        boolean ci = this.shouldUsePrefixedCompression();

        ByteBuf finalPayload = ByteBufAllocator.DEFAULT.directBuffer((ci ? 10 : 9) + compressedPayload.length); // prefix(1)+id(1)+encryption(8)+data
        finalPayload.writeByte(0xfe);

        if (this.encryptionCipher != null) {
            try {
                byte[] fullPayload = ci ? new byte[compressedPayload.length + 1] : compressedPayload;
                if (ci) {
                    fullPayload[0] = this.compressionOut.getPrefix();
                    System.arraycopy(compressedPayload, 0, fullPayload, 1, compressedPayload.length);
                }
                ByteBuf compressed = Unpooled.wrappedBuffer(fullPayload);
                ByteBuffer trailer = ByteBuffer.wrap(this.calculateChecksum(this.encryptCounter.getAndIncrement(), compressed));
                ByteBuffer outBuffer = finalPayload.internalNioBuffer(1, compressed.readableBytes() + 8);
                ByteBuffer inBuffer = compressed.internalNioBuffer(compressed.readerIndex(), compressed.readableBytes());
                this.encryptionCipher.update(inBuffer, outBuffer);
                this.encryptionCipher.update(trailer, outBuffer);
                finalPayload.writerIndex(finalPayload.writerIndex() + compressed.readableBytes() + 8);
            } catch (Exception e) {
                log.error("Packet encryption failed for {}", player.getName(), e);
            }
        }else {
            if (ci) {
                finalPayload.writeByte(this.compressionOut.getPrefix());
            }

            finalPayload.writeBytes(compressedPayload);
        }

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
        if (!this.shouldUsePrefixedCompressionAfterNegotiation()) {
            return;
        }
        Preconditions.checkNotNull(compression, "compression");
        this.state.getSecurity().setLegacyInboundCompression(compression);
        this.state.getSecurity().setLegacyInboundGraceWindow(true);
    }

    @Override
    public void endLegacyInboundCompressionGraceWindow() {
        this.state.getSecurity().setLegacyInboundGraceWindow(false);
        this.state.getSecurity().setLegacyInboundCompression(null);
    }

    @Override
    public void beginLegacyInboundEncryptionGraceWindow() {
        this.state.getSecurity().setLegacyInboundEncryptionGraceWindow(true);
    }

    @Override
    public void endLegacyInboundEncryptionGraceWindow() {
        this.state.getSecurity().setLegacyInboundEncryptionGraceWindow(false);
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

    public RakChildChannel getChannel() {
        return this.channel;
    }

    public String getDisconnectReason() {
        return this.disconnectReason;
    }

    public boolean isPendingLoginTimedOut(long nowNanos, int timeoutMillis) {
        return isPendingLoginTimedOut(
                this.channel.isActive(),
                this.state.getLogin().getPhase(),
                this.state.getLogin().getLastActivityNanos(),
                this.state.getConnection().getChildChannelAcceptedNanos(),
                nowNanos,
                timeoutMillis
        );
    }

    public boolean isLoginPhaseTimedOut(long nowNanos, int timeoutMillis) {
        return isLoginPhaseTimedOut(this.state.getLogin().getPhase(), this.state.getLogin().getLastActivityNanos(), nowNanos, timeoutMillis);
    }

    @Override
    public void setEncryption(SecretKey encryptionKey, Cipher encryptionCipher, Cipher decryptionCipher) {
        this.encryptionKey = encryptionKey;
        this.encryptionCipher = encryptionCipher;
        this.decryptionCipher = decryptionCipher;
        this.state.getSecurity().setEncryptionEnabled(encryptionKey != null && encryptionCipher != null && decryptionCipher != null);
    }

    @Override
    public long getPing() {
        RakSessionCodec codec = channel.rakPipeline().get(RakSessionCodec.class);
        if (codec == null) {
            return -1;
        }
        return codec.getPing();
    }

    private byte[] calculateChecksum(long count, ByteBuf payload) {
        Sha256 hash = HASH_LOCAL.get();
        ByteBuf counterBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
        try {
            counterBuf.writeLongLE(count);
            ByteBuffer keyBuffer = ByteBuffer.wrap(this.encryptionKey.getEncoded());
            hash.update(counterBuf.internalNioBuffer(0, 8));
            hash.update(payload.internalNioBuffer(payload.readerIndex(), payload.readableBytes()));
            hash.update(keyBuffer);
            byte[] digested = hash.digest();
            return Arrays.copyOf(digested, 8);
        } finally {
            counterBuf.release();
            hash.reset();
        }
    }

    private int getBedrockProtocol() {
        GameVersion gameVersion = this.state.getProtocol().getGameVersion();
        if (gameVersion != null) {
            return gameVersion.getProtocol();
        }
        if (this.player != null) {
            return this.player.protocol;
        }
        return Integer.MAX_VALUE;
    }

    private boolean shouldUsePrefixedCompression() {
        int protocol = this.getBedrockProtocol();
        return this.state.getSecurity().isCompressionInitialized()
                && protocol != Integer.MAX_VALUE
                && protocol >= ProtocolInfo.v1_20_60;
    }

    private boolean shouldUsePrefixedCompressionAfterNegotiation() {
        int protocol = this.getBedrockProtocol();
        return protocol != Integer.MAX_VALUE && protocol >= ProtocolInfo.v1_20_60;
    }

    private boolean processInboundBatch(byte[] packetBuffer, boolean ci) {
        try {
            if (!ci) {
                return this.server.getNetwork().processBatch(packetBuffer, this.inbound, this.compressionIn, this.channel.config().getProtocolVersion(), this.player);
            }

            InboundBatchDecodeResult result = decodeInboundPrefixedBatch(
                    this.server.getNetwork(),
                    packetBuffer,
                    this.state.getSecurity().getLegacyInboundCompression(),
                    this.state.getSecurity().isLegacyInboundGraceWindow(),
                    this.channel.config().getProtocolVersion(),
                    this.player
            );
            if (!result.success()) {
                return false;
            }

            this.compressionIn = result.compression();
            this.state.getSecurity().setCompressionIn(result.compression());
            if (result.prefixed()) {
                this.endLegacyInboundCompressionGraceWindow();
            }
            this.inbound.addAll(result.packets());
            return true;
        } catch (Exception e) {
            log.error("[{}] Unable to process batch packet", (this.player == null ? this.channel.remoteAddress() : this.player.getName()), e);
            return false;
        }
    }

    static InboundBatchDecodeResult decodeInboundPrefixedBatch(Network network, byte[] packetBuffer, CompressionProvider legacyCompression,
                                                               boolean allowLegacyFallback, int raknetProtocol, Player player) {
        if (packetBuffer.length == 0) {
            return InboundBatchDecodeResult.failed();
        }

        CompressionProvider prefixedCompression = tryResolveCompressionByPrefix(packetBuffer[0], raknetProtocol);
        if (prefixedCompression != null && packetBuffer.length > 1) {
            List<DataPacket> prefixedPackets = new ObjectArrayList<>();
            // Skip the 1-byte compression prefix; copy cost is negligible vs decompression
            byte[] prefixedPayload = Arrays.copyOfRange(packetBuffer, 1, packetBuffer.length);
            if (network.processBatch(prefixedPayload, prefixedPackets, prefixedCompression, raknetProtocol, player)) {
                return InboundBatchDecodeResult.prefixed(prefixedCompression, prefixedPackets);
            }
        }

        if (allowLegacyFallback && legacyCompression != null) {
            List<DataPacket> legacyPackets = new ObjectArrayList<>();
            if (network.processBatch(packetBuffer, legacyPackets, legacyCompression, raknetProtocol, player)) {
                return InboundBatchDecodeResult.legacy(legacyCompression, legacyPackets);
            }
        }
        return InboundBatchDecodeResult.failed();
    }

    private static CompressionProvider tryResolveCompressionByPrefix(byte prefix, int raknetProtocol) {
        try {
            return CompressionProvider.byPrefix(prefix, raknetProtocol);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    static boolean isLoginPhaseTimedOut(SessionLoginPhase phase, long lastActivityNanos, long nowNanos, int timeoutMillis) {
        if (timeoutMillis <= 0 || phase == SessionLoginPhase.LOGGED_IN || phase == SessionLoginPhase.DISCONNECTED) {
            return false;
        }
        return nowNanos - lastActivityNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    static boolean isPendingLoginTimedOut(boolean channelActive, SessionLoginPhase phase, long lastActivityNanos,
                                          long childChannelAcceptedNanos, long nowNanos, int timeoutMillis) {
        if (channelActive) {
            return isLoginPhaseTimedOut(phase, lastActivityNanos, nowNanos, timeoutMillis);
        }
        if (timeoutMillis <= 0 || phase == SessionLoginPhase.LOGGED_IN || phase == SessionLoginPhase.DISCONNECTED) {
            return false;
        }
        return nowNanos - childChannelAcceptedNanos >= TimeUnit.MILLISECONDS.toNanos(timeoutMillis);
    }

    static boolean shouldBlockAddressAfterMalformed(SessionLoginPhase phase, InetAddress address) {
        return address != null
                && !address.isSiteLocalAddress()
                && phase.ordinal() >= SessionLoginPhase.LOGIN_RECEIVED.ordinal();
    }

    record InboundBatchDecodeResult(boolean success, CompressionProvider compression, boolean prefixed, List<DataPacket> packets) {

        static InboundBatchDecodeResult failed() {
            return new InboundBatchDecodeResult(false, null, false, List.of());
        }

        static InboundBatchDecodeResult prefixed(CompressionProvider compression, List<DataPacket> packets) {
            return new InboundBatchDecodeResult(true, compression, true, packets);
        }

        static InboundBatchDecodeResult legacy(CompressionProvider compression, List<DataPacket> packets) {
            return new InboundBatchDecodeResult(true, compression, false, packets);
        }
    }

}
