package cn.nukkit.network.protocol;

import cn.nukkit.math.BlockVector3;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class SubChunkRequestPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SUB_CHUNK_REQUEST_PACKET;

    public int dimension;
    public BlockVector3 subChunkPosition;
    /**
     * Position offsets relative to subChunkPosition, each encoded as 3 signed bytes.
     *
     * @since v486 (1.18.10)
     */
    public List<BlockVector3> positionOffsets = new ArrayList<>();

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.dimension = this.getVarInt();
        this.subChunkPosition = this.getSignedBlockPosition();
        if (this.protocol >= ProtocolInfo.v1_18_10) {
            int count = this.getLInt();
            for (int i = 0; i < count; i++) {
                this.positionOffsets.add(new BlockVector3(this.getByte(), this.getByte(), this.getByte()));
            }
        }
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
