package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.BlockChangeEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

@ToString
public class UpdateSubChunkBlocksPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_SUB_CHUNK_BLOCKS_PACKET;

    public final int chunkX;
    public final int chunkY;
    public final int chunkZ;

    public final List<BlockChangeEntry> standardBlocks = new ObjectArrayList<>();
    public final List<BlockChangeEntry> extraBlocks = new ObjectArrayList<>();

    public UpdateSubChunkBlocksPacket(int chunkX, int chunkY, int chunkZ) {
        this.chunkX = chunkX;
        this.chunkY = chunkY;
        this.chunkZ = chunkZ;
    }

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {

    }

    @Override
    public void encode() {
        putVarInt(chunkX);
        putUnsignedVarInt(chunkY);
        putVarInt(chunkZ);
        putUnsignedVarInt(standardBlocks.size());
        for (final var each : standardBlocks) {
            putBlockVector3(each.blockPos());
            putUnsignedVarInt(each.runtimeID());
            putUnsignedVarInt(each.updateFlags());
            putUnsignedVarLong(each.messageEntityID());
            putUnsignedVarInt(each.messageType().ordinal());
        }
        putUnsignedVarInt(extraBlocks.size());
        for (final var each : extraBlocks) {
            putBlockVector3(each.blockPos());
            putUnsignedVarInt(each.runtimeID());
            putUnsignedVarInt(each.updateFlags());
            putUnsignedVarLong(each.messageEntityID());
            putUnsignedVarInt(each.messageType().ordinal());
        }
    }

    public int getChunkX() {
        return this.chunkX;
    }

    public int getChunkY() {
        return this.chunkY;
    }

    public int getChunkZ() {
        return this.chunkZ;
    }

    public List<BlockChangeEntry> getStandardBlocks() {
        return this.standardBlocks;
    }

    public List<BlockChangeEntry> getExtraBlocks() {
        return this.extraBlocks;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof UpdateSubChunkBlocksPacket that)) return false;
        return chunkX == that.chunkX && chunkY == that.chunkY && chunkZ == that.chunkZ && standardBlocks.equals(that.standardBlocks) && extraBlocks.equals(that.extraBlocks);
    }

    public int hashCode() {
        final int PRIME = 59;
        int result = 1;
        result = result * PRIME + this.chunkX;
        result = result * PRIME + this.chunkY;
        result = result * PRIME + this.chunkZ;
        result = result * PRIME + ((Object) this.standardBlocks).hashCode();
        result = result * PRIME + ((Object) this.extraBlocks).hashCode();
        return result;
    }
}
