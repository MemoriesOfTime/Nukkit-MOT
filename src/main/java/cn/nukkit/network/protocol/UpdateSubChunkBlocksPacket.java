package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.BlockChangeEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

@ToString
public class UpdateSubChunkBlocksPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_SUB_CHUNK_BLOCKS_PACKET;

    public int chunkX;
    public int chunkY;
    public int chunkZ;

    public final List<BlockChangeEntry> standardBlocks = new ObjectArrayList<>();
    public final List<BlockChangeEntry> extraBlocks = new ObjectArrayList<>();

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        putVarInt(this.chunkX);
        putUnsignedVarInt(this.chunkY);
        putVarInt(this.chunkZ);
        this.putArray(this.standardBlocks, ((stream, entry) -> {
            putBlockVector3(entry.blockPos());
            putUnsignedVarInt(entry.runtimeID());
            putUnsignedVarInt(entry.updateFlags());
            putUnsignedVarLong(entry.messageEntityID());
            putUnsignedVarInt(entry.messageType().ordinal());
        }));
        this.putArray(this.extraBlocks, ((stream, entry) -> {
            putBlockVector3(entry.blockPos());
            putUnsignedVarInt(entry.runtimeID());
            putUnsignedVarInt(entry.updateFlags());
            putUnsignedVarLong(entry.messageEntityID());
            putUnsignedVarInt(entry.messageType().ordinal());
        }));
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
}
