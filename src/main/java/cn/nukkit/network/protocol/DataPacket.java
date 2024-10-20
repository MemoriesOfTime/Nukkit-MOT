package cn.nukkit.network.protocol;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.network.Network;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.SnappyCompression;
import cn.nukkit.utils.Zlib;
import lombok.extern.log4j.Log4j2;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public abstract class DataPacket extends BinaryStream implements Cloneable {

    public int protocol = Integer.MAX_VALUE;

    public volatile boolean isEncoded = false;
    private int channel = Network.CHANNEL_NONE;

    public int packetId() {
        return ProtocolInfo.toNewProtocolID(this.pid());
    }

    public abstract byte pid();

    public abstract void decode();

    public abstract void encode();

    public final void tryEncode() {
        if (!this.isEncoded) {
            this.isEncoded = true;
            this.encode();
        }
    }

    @Override
    public DataPacket reset() {
        super.reset();

        if (protocol <= 274) {
            if (protocol >= ProtocolInfo.v1_2_0) {
                this.putByte(this.pid());
                this.putShort(0);
            } else {
                int packetId;
                try {
                    packetId = Server.getInstance().getNetwork().getPacketPool(protocol).getPacketId(this.getClass());
                } catch (IllegalArgumentException e) {
                    packetId = 0x6a; //使用1.1不存在的id，所有不支持的数据包
                    if (Nukkit.DEBUG > 1) {
                        log.warn("Unknown packet {} for protocol {}", this.getClass().getName(), protocol);
                    }
                }
                this.putByte((byte) (packetId & 0xff));
            }
        } else {
            this.putUnsignedVarInt(this.packetId());
        }

        return this;
    }

    @Deprecated
    public void setChannel(int channel) {
        this.channel = channel;
    }

    @Deprecated
    public int getChannel() {
        return channel;
    }

    public DataPacket clean() {
        this.setBuffer(null);
        this.setOffset(0);
        this.isEncoded = false;
        return this;
    }

    @Override
    public DataPacket clone() {
        try {
            DataPacket packet = (DataPacket) super.clone();
            packet.setBuffer(this.getBuffer()); // prevent reflecting same buffer instance
            packet.offset = this.offset;
            packet.count = this.count;
            return packet;
        } catch (CloneNotSupportedException e) {
            return null;
        }
    }

    public BatchPacket compress() {
        return compress(Server.getInstance().networkCompressionLevel);
    }

    public BatchPacket compress(int level) {
        BinaryStream stream = new BinaryStream();
        byte[] buf = this.getBuffer();
        stream.putUnsignedVarInt(buf.length);
        stream.put(buf);
        try {
            BatchPacket batched = new BatchPacket();
            if (Server.getInstance().useSnappy && protocol >= ProtocolInfo.v1_19_30_23) {
                batched.payload = SnappyCompression.compress(stream.getBuffer());
            } else if (protocol >= ProtocolInfo.v1_16_0) {
                batched.payload = Zlib.deflateRaw(stream.getBuffer(), level);
            } else {
                batched.payload = Zlib.deflatePre16Packet(stream.getBuffer(), level);
            }
            return batched;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
