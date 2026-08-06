package cn.nukkit.network.session;

import cn.nukkit.Nukkit;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.network.CompressionProvider;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.protocol.*;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.utils.Binary;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.ThreadCache;
import com.google.common.base.Preconditions;
import com.nukkitx.natives.sha256.Sha256;
import com.nukkitx.natives.util.Natives;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.Unpooled;
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
        this.compressionIn = protocolVersion >= 11 ? CompressionProvider.NONE : (protocolVersion < 10 ? CompressionProvider.ZLIB : CompressionProvider.ZLIB_RAW);
        this.compressionOut = this.compressionIn;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext channelHandlerContext, RakMessage msg) throws Exception {
        //log.info("Session#channelRead0");
        ByteBuf buffer = msg.content();

        short packetId = buffer.readUnsignedByte();

        if(Server.getInstance().minimumProtocol <= ProtocolInfo.v1_2_0) {
            // Under 0.14
            // 不过有点问题，0.11以下版本中有一个packet是0x8e开头的，需要进行对应区分
            if ((byte) (packetId & 0xff) != (byte) 0x8e && (byte) (packetId & 0xff) != (byte) 0xfe) {
                if ((byte) (packetId & 0xff) == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_11_0).get(BatchPacket.class)) {
                    //log.info("0.11 数据包...");

                    DataPacket pk = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_11_0); // 0.11.0 数据包获取
                    byte[] packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);
                    pk.protocol = ProtocolInfo.v_0_11_0;
                    packetBuffer = Binary.appendBytes((byte) (packetId & 0xff), packetBuffer);
                    pk.setBuffer(packetBuffer, 1);
                    pk.decode();
                    this.inbound.offer(pk);
                    // 不能直接调用this.player, 因为player可能尚未创建出来
                    // this.player.handleDataPacket(pk);
                    return;
                } else if ((byte) (packetId & 0xff) == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_10_0).get(LoginPacket.class)) {
                    //log.info("0.10 数据包...");

                    DataPacket pk = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_10_0); // 0.10.0 数据包获取
                    byte[] packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);
                    pk.protocol = ProtocolInfo.v_0_10_0;
                    packetBuffer = Binary.appendBytes((byte) (packetId & 0xff), packetBuffer);
                    pk.setBuffer(packetBuffer, 1);
                    pk.decode();
                    this.inbound.offer(pk);
                    // 不能直接调用this.player, 因为player可能尚未创建出来
                    // this.player.handleDataPacket(pk);
                    return;
                } else if (this.player != null && this.player.confirmProtocol && this.player.protocol <= ProtocolInfo.v_0_11_0) {
                    //log.info("0.11 数据包...");
                    byte[] packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);

                    DataPacket pk;
                    if (this.player.protocol >= ProtocolInfo.v_0_11_0) {
                        pk = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_11_0);
                    } else {
                        pk = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_10_0);
                    }
                    if (pk != null) {
                        if (this.player != null && this.player.confirmProtocol) {
                            pk.protocol = this.player.protocol;
                        } else {
                            pk.protocol = ProtocolInfo.v_0_11_0;
                        }
                        packetBuffer = Binary.appendBytes((byte) (packetId & 0xff), packetBuffer);
                        pk.setBuffer(packetBuffer, 1);
                        pk.decode();
                        this.inbound.offer(pk);
                        // 不能直接调用this.player, 因为player可能尚未创建出来
                        // this.player.handleDataPacket(pk);
                    }
                    return;
                } else {
                    //log.info("0.12 数据包...");
                    byte[] packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);

                    DataPacket pk = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_14_3); //0.12.1 和 0.13.1 的 pid 和 0.14.3 类似
                    if (pk != null) {
                        if (this.player != null && this.player.confirmProtocol) {
                            pk.protocol = this.player.protocol;
                        } else {
                            pk.protocol = ProtocolInfo.v_0_12_1;
                        }
                        packetBuffer = Binary.appendBytes((byte) (packetId & 0xff), packetBuffer);
                        pk.setBuffer(packetBuffer, 1);
                        pk.decode();
                        this.inbound.offer(pk);
                        // 不能直接调用this.player, 因为player可能尚未创建出来
                        // this.player.handleDataPacket(pk);
                    }
                    return;
                }
            }

            // 0.14
            if ((byte) (packetId & 0xff) == (byte) 0x8e) {
                //log.info("0.14 数据包...");
                byte[] packetBuffer = new byte[buffer.readableBytes()];
                buffer.readBytes(packetBuffer);

                DataPacket pk = this.server.getNetwork().getPacket((byte) (packetBuffer[0] & 0xff), ProtocolInfo.v_0_14_3);
                if (pk != null) {
                    if (this.player != null && this.player.confirmProtocol) {
                        pk.protocol = this.player.protocol;
                    } else {
                        pk.protocol = ProtocolInfo.v_0_14_3;
                    }
                    pk.setBuffer(packetBuffer, 1);
                    pk.decode();
                    this.inbound.offer(pk);
                    // 不能直接调用this.player, 因为player可能尚未创建出来
                    // this.player.handleDataPacket(pk);
                } else {
                    //非 0.14.3 pid 列表包 ，如 0.11
                    DataPacket pk011 = this.server.getNetwork().getPacket((byte) (packetId & 0xff), ProtocolInfo.v_0_11_0); //查找 0.11.1 是否存在 0x83 为开头的 pid
                    pk011.protocol = ProtocolInfo.v_0_11_0;
                    packetBuffer = Binary.appendBytes((byte) (packetId & 0xff), packetBuffer);
                    pk.setBuffer(packetBuffer, 1);
                    pk.decode();
                    this.inbound.offer(pk);
                    // 不能直接调用this.player, 因为player可能尚未创建出来
                    // this.player.handleDataPacket(pk);
                }
                return;
            }
        }

        // 收到mc数据包
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

            // 玩家确认协议号后, 将根据协议号直接采用相应逻辑处理
            if(this.player != null && this.player.confirmProtocol){
                if(this.player.protocol < ProtocolInfo.v1_1_0){
                    packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);
                    // 0.15
                    DataPacket pk = this.server.getNetwork().getPacket((byte) (packetBuffer[0] & 0xff), this.player.protocol);
                    if(pk != null){
                        pk.protocol = this.player.protocol;
                        pk.setBuffer(packetBuffer, 1);
                        pk.decode();
                        this.player.handleDataPacket(pk);
                    }
                    return;
                }else{

                }
            }else{
                byte oldPacketId = (byte) (buffer.getUnsignedByte(1) & 0xff);
                // 0.15 - 0.16 第一个数据包直接发送BatchPacket或LoginPacket
                if(oldPacketId == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(LoginPacket.class)){
                    packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);
                    LoginPacket pk = new LoginPacket();
                    if(this.player!= null && this.player.confirmProtocol){
                        pk.protocol = this.player.protocol;
                    }else{
                        pk.protocol = ProtocolInfo.v_0_15_10;
                    }
                    pk.setBuffer(packetBuffer, 1);
                    pk.decode();
                    this.inbound.offer(pk);
                    return;
                }else if(oldPacketId == ProtocolInfo.oldProtocolInfo.get(ProtocolInfo.v_0_15_10).get(BatchPacket.class)){
                    packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);
                    BatchPacket pk = new BatchPacket();
                    pk.setBuffer(packetBuffer, 1);
                    int datasize = pk.getInt();
                    int offset = pk.getOffset();
                    int diff = pk.getBuffer().length - offset;
                    if( datasize == diff ){  // 0.15 包长度固定 4 字节
                        if (this.player != null && this.player.confirmProtocol) {
                            pk.protocol = this.player.protocol;
                        } else {
                            pk.protocol = ProtocolInfo.v_0_15_10;
                        }
                        log.info("收到: " + pk.getName() + "-id: 0x" + Integer.toHexString(pk.pid() & 0xff));
                        pk.setBuffer(packetBuffer, 1);
                        pk.decode();
                        List<DataPacket> packets = this.server.getNetwork().processBatch(pk, this.player);
                        if (packets != null && packets.size() > 0) {
                            for (DataPacket p1 : packets) {
                                inbound.offer(p1);
                            }
                        }
                        return;
                    }else{
                        if (this.player != null && this.player.confirmProtocol) {
                            pk.protocol = this.player.protocol;
                        } else {
                            pk.protocol = ProtocolInfo.v_0_16_0;
                        }
                        log.info("收到: " + pk.getName() + "-id: 0x" + Integer.toHexString(pk.pid() & 0xff));
                        pk.setBuffer(packetBuffer, 1);
                        pk.decode();
                        List<DataPacket> packets = this.server.getNetwork().processBatch(pk, this.player);
                        if (packets != null && packets.size() > 0) {
                            for (DataPacket p1 : packets) {
                                inbound.offer(p1);
                            }
                        }
                        return;
                    }
                }else if(oldPacketId == (byte) 0x78 && (byte) (buffer.getUnsignedByte(2) & 0xff) == (byte) 0xda){ //1.1的数据包，0xfe后面必定是 0x78 0xda这两个字节
                    // 1.1 batchpacket 处理
                    packetBuffer = new byte[buffer.readableBytes()];
                    buffer.readBytes(packetBuffer);

                    int protocol = ProtocolInfo.v1_1_0;

                    this.server.getNetwork().processBatch(packetBuffer, this.inbound, compressionIn, protocol, this.player);

                    return;
                }
            }

            boolean ci = false;
            if (this.compressionInitialized && this.player.protocol >= ProtocolInfo.v1_20_60) {
                ci = true;
            }

            if (this.decryptionCipher != null) {
                try {
                    ByteBuffer buf = buffer.nioBuffer();
                    this.decryptionCipher.update(buf, buf.duplicate());
                } catch (Exception e) {
                    log.error("Packet decryption failed for " + player.getName(), e);
                    return;
                }

                if (ci) {
                    this.compressionIn = CompressionProvider.byPrefix(buffer.readByte(), this.channel.config().getProtocolVersion());
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
                buffer.resetReaderIndex();

                packetBuffer = new byte[buffer.readableBytes() - 8];
            } else {
                if (ci) {
                    this.compressionIn = CompressionProvider.byPrefix(buffer.readByte(), this.channel.config().getProtocolVersion());
                }

                packetBuffer = new byte[buffer.readableBytes()];
            }

            buffer.readBytes(packetBuffer);

            try {
                this.server.getNetwork().processBatch(packetBuffer, this.inbound, compressionIn, this.channel.config().getProtocolVersion(), this.player);
            } catch (Exception e) {
                Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, () -> {
                    try {
                        InetAddress address = this.channel.remoteAddress().getAddress();
                        this.channel.unsafe().close(this.channel.voidPromise());
                        if (!address.isSiteLocalAddress()) {
                            this.server.blockAddress(address, 60);
                        }
                    } catch (Throwable throwable) {
                        if (Nukkit.DEBUG > 1) {
                            log.info("Error while closing channel", throwable);
                        }
                    }
                }, 10);
                this.disconnect("Sent malformed packet");
                log.error("[{}] Unable to process batch packet", (this.player == null ? this.channel.remoteAddress() : this.player.getName()), e);
            }
        } else if (Nukkit.DEBUG > 1) {
            // 如果没有数据标头, 即为0.14以下的版本
            log.info("Unknown EncapsulatedPacket: {}", packetId);
        }
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

        if(ProtocolInfo.v_0_13_2 < packet.protocol && packet.protocol <= ProtocolInfo.v_0_16_1){
            packet.tryEncode();
            byte[] buf = packet.getBuffer();
            ByteBuf finalPayload = ByteBufAllocator.DEFAULT.directBuffer(1 + buf.length);
            if(packet.protocol <= ProtocolInfo.v_0_14_3){
                finalPayload.writeByte(0x8e);
            }else{
                finalPayload.writeByte(0xfe);
            }
            finalPayload.writeBytes(buf);
            this.channel.writeAndFlush(finalPayload);
            return;
        } else if (ProtocolInfo.v_0_8_1 <= packet.protocol && packet.protocol <= ProtocolInfo.v_0_13_2) {
            packet.tryEncode();
            byte[] buf = packet.getBuffer();
            if (packet.protocol > ProtocolInfo.v_0_11_0){
                ByteBuf finalPayload = ByteBufAllocator.DEFAULT.directBuffer(buf.length);
                finalPayload.writeBytes(buf);
                this.channel.writeAndFlush(finalPayload);
                return;
            }else{
                ByteBuf finalPayload = ByteBufAllocator.DEFAULT.directBuffer(buf.length);
                finalPayload.writeBytes(buf);
                this.channel.writeAndFlush(finalPayload);
                return;
            }
        }

        if (!(packet instanceof BatchPacket)) {
            packet.tryEncode();
        }

        this.outbound.offer(packet);
    }

    @Override
    public void sendImmediatePacket(DataPacket packet, Runnable callback) {
        if (!this.channel.isActive()) {
            return;
        }

        this.sendPacket(packet);
        this.channel.eventLoop().execute(() -> {
            this.networkTick();
            callback.run();
        });
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
        // 低版本无法在此得到数据包
        DataPacket packet;
        while ((packet = this.inbound.poll()) != null) {
            try {
                this.player.handleDataPacket(packet);
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
            if (!packet.isEncoded) {
                throw new IllegalStateException("Packet should have already been encoded");
            }

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

    private void sendPacket(byte[] compressedPayload) {
        boolean ci = false;
        if (this.compressionInitialized && this.player.protocol >= ProtocolInfo.v1_20_60) {
            ci = true;
        }

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

        this.channel.writeAndFlush(finalPayload);
    }

    @Override
    public void setCompression(CompressionProvider compression) {
        Preconditions.checkNotNull(compression, "compression");
        this.compressionIn = compression;
        this.compressionOut = compression;
        this.compressionInitialized = true;
    }

    @Override
    public CompressionProvider getCompression() {
        return this.compressionOut;
    }

    public void setPlayer(Player player) {
        Preconditions.checkArgument(this.player == null && player != null);
        this.player = player;
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

    @Override
    public void setEncryption(SecretKey encryptionKey, Cipher encryptionCipher, Cipher decryptionCipher) {
        this.encryptionKey = encryptionKey;
        this.encryptionCipher = encryptionCipher;
        this.decryptionCipher = decryptionCipher;
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
}