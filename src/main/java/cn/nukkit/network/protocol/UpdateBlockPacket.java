package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class UpdateBlockPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_BLOCK_PACKET;

    public static final int FLAG_NONE = 0b0000;
    public static final int FLAG_NEIGHBORS = 0b0001;
    public static final int FLAG_NETWORK = 0b0010;
    public static final int FLAG_NOGRAPHIC = 0b0100;
    public static final int FLAG_PRIORITY = 0b1000;

    public static final int FLAG_ALL = 3; // FLAG_NEIGHBORS | FLAG_NETWORK
    public static final int FLAG_ALL_PRIORITY = 11; // FLAG_ALL | FLAG_PRIORITY

    public int x;
    public int z;
    public int y;
    public int blockId;
    public int blockData;
    public int blockRuntimeId;
    public int flags;
    public int dataLayer = 0;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        this.putBlockVector3(x, y, z);
        if (protocol > ProtocolInfo.v1_2_10) {
            this.putUnsignedVarInt(blockRuntimeId);
            this.putUnsignedVarInt(flags);
        } else {
            this.putUnsignedVarInt(blockId);
            this.putUnsignedVarInt(176 | blockData & 0xf); // (0xb << 4) | blockData & 0xf
        }
        if (protocol > ProtocolInfo.v1_2_13_11) {
            this.putUnsignedVarInt(dataLayer);
        }
    }

    public static class Entry {
        public final int x;
        public final int z;
        public final int y;
        public final int blockId;
        public final int blockData;
        public final int flags;

        public Entry(int x, int z, int y, int blockId, int blockData, int flags) {
            this.x = x;
            this.z = z;
            this.y = y;
            this.blockId = blockId;
            this.blockData = blockData;
            this.flags = flags;
        }
    }
}
