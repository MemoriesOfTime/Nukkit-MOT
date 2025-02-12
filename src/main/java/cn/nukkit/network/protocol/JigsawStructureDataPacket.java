package cn.nukkit.network.protocol;

import cn.nukkit.nbt.tag.CompoundTag;
import lombok.ToString;

/**
 * @since v712
 */
@ToString
public class JigsawStructureDataPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.JIGSAW_STRUCTURE_DATA_PACKET;

    public CompoundTag nbt;

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.nbt = this.getTag();
    }

    @Override
    public void encode() {
        this.putNbtTag(this.nbt);
    }
}
