package cn.nukkit.level.format;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.BinaryStream;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface ChunkSection {

    int getY();

    int getBlockId(int x, int y, int z);

    int getBlockId(int x, int y, int z, int layer);

    void setBlockId(int x, int y, int z, int id);

    boolean setFullBlockId(int x, int y, int z, int layer, int fullId);

    int getBlockData(int x, int y, int z);

    int getBlockData(int x, int y, int z, int layer);

    void setBlockData(int x, int y, int z, int data);

    void setBlockData(int x, int y, int z, int layer, int data);

    int getFullBlock(int x, int y, int z);

    default int[] getBlockState(int x, int y, int z) {
        return getBlockState(x, y, z, 0);
    }

    default int[] getBlockState(int x, int y, int z, int layer) {
        return new int[] {getBlockId(x, y, z, layer), getBlockData(x, y, z, layer)};
    }

    Block getAndSetBlock(int x, int y, int z, int layer, Block block);

    Block getAndSetBlock(int x, int y, int z, Block block);

    void setBlockId(int x, int y, int z, int layer, int id);

    boolean setFullBlockId(int x, int y, int z, int fullId);

    int getFullBlock(int x, int y, int z, int layer);

    boolean setBlock(int x, int y, int z, int blockId);

    boolean setBlock(int x, int y, int z, int blockId, int meta);

    boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId);

    boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId, int meta);

    int getBlockSkyLight(int x, int y, int z);

    void setBlockSkyLight(int x, int y, int z, int level);

    int getBlockLight(int x, int y, int z);

    void setBlockLight(int x, int y, int z, int level);

    byte[] getIdExtraArray(int layer);

    byte[] getIdArray(int layer);

    byte[] getIdArray();

    byte[] getDataArray();

    byte[] getDataArray(int layer);
    
    default byte[] getDataExtraArray() {
        return getDataExtraArray(0);
    }
    
    byte[] getDataExtraArray(int layer);

    default byte[][] getHyperDataArray() {
        return getHyperDataArray(0);
    }

    default byte[][] getHyperDataArray(int layer) {
        return new byte[0][];
    }

    byte[] getSkyLightArray();

    byte[] getLightArray();

    boolean isEmpty();

    boolean hasLayer(int layer);

    @Deprecated
    default byte[] getBytes(int protocolId) {
        Server.mvw("ChunkSection#getBytes(int) is deprecated, please use ChunkSection#getBytes(GameVersion) instead.");
        return getBytes(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode));
    }

    // for < 1.13 chunk format
    byte[] getBytes(GameVersion gameVersion);
    
    int getMaximumLayer();

    CompoundTag toNBT();

    @Deprecated
    default void writeTo(int protocol, BinaryStream stream) {
        writeTo(protocol, stream, false);
    }

    @Deprecated
    default void writeTo(int protocol, BinaryStream stream, boolean antiXray) {
        Server.mvw("ChunkSection#writeTo(int, BinaryStream, boolean) is deprecated, please use ChunkSection#writeTo(GameVersion, BinaryStream, boolean) instead.");
        writeTo(GameVersion.byProtocol(protocol, Server.getInstance().onlyNetEaseMode), stream, antiXray);
    }

    // for >= 1.13 chunk format
    void writeTo(GameVersion gameVersion, BinaryStream stream, boolean antiXray);

    ChunkSection copy();

    default int getContentVersion() {
        return 0;
    }

    default boolean compress() {
        return false;
    }

    default boolean isDirty() {
        return true;
    }

    default void setDirty() {

    }
}
