package cn.nukkit.level.format;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.util.PalettedBlockStorage;

import java.io.IOException;
import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface FullChunk extends Cloneable {

    int getX();

    int getZ();

    default void setPosition(int x, int z) {
        setX(x);
        setZ(z);
    }

    void setX(int x);

    void setZ(int z);

    long getIndex();

    LevelProvider getProvider();

    void setProvider(LevelProvider provider);

    int getFullBlock(int x, int y, int z);
    
    int getFullBlock(int x, int y, int z, int layer);

    default int getBlockRuntimeId(int protocolId, int x, int y, int z) {
        return this.getBlockRuntimeId(protocolId, x, y, z, 0);
    }

    default int getBlockRuntimeId(int protocolId, int x, int y, int z, int layer) {
        return GlobalBlockPalette.getOrCreateRuntimeId(protocolId, this.getBlockId(x, y, z, layer), this.getBlockData(x, y, z, layer));
    }

    default int[] getBlockState(int x, int y, int z) {
        return getBlockState(x, y, z, 0);
    }

    default int[] getBlockState(int x, int y, int z, int layer) {
        int full = getFullBlock(x, y, z, layer);
        return new int[] { full >> Block.DATA_BITS, full & Block.DATA_MASK };
    }

    Block getAndSetBlock(int x, int y, int z, Block block);
    Block getAndSetBlock(int x, int y, int z, int layer, Block block);

    default boolean setFullBlockId(int x, int y, int z, int fullId) {
        return setFullBlockId(x, y, z, 0, fullId >> Block.DATA_BITS);
    }

    default boolean setFullBlockId(int x, int y, int z, int layer, int fullId) {
        return setBlockAtLayer(x, y, z, layer, fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
    }

    boolean setBlock(int x, int y, int z, int blockId);

    boolean setBlockAtLayer(int x, int y, int z, int layer, int  blockId);

    boolean setBlock(int x, int y, int z, int  blockId, int  meta);

    boolean setBlockAtLayer(int x, int y, int z, int layer, int blockId, int  meta);

    int getBlockId(int x, int y, int z);

    int getBlockId(int x, int y, int z, int layer);

    void setBlockId(int x, int y, int z, int id);

    void setBlockId(int x, int y, int z, int layer, int id);

    int getBlockData(int x, int y, int z);

    int getBlockData(int x, int y, int z, int layer);

    void setBlockData(int x, int y, int z, int data);

    void setBlockData(int x, int y, int z, int layer, int data);

    int getBlockExtraData(int x, int y, int z);

    void setBlockExtraData(int x, int y, int z, int data);

    int getBlockSkyLight(int x, int y, int z);

    void setBlockSkyLight(int x, int y, int z, int level);

    int getBlockLight(int x, int y, int z);

    void setBlockLight(int x, int y, int z, int level);

    int getHighestBlockAt(int x, int z);

    int getHighestBlockAt(int x, int z, boolean cache);

    int getHeightMap(int x, int z);

    void setHeightMap(int x, int z, int value);

    void recalculateHeightMap();

    int recalculateHeightMapColumn(int chunkX, int chunkZ);

    void populateSkyLight();

    default public boolean has3dBiomes() {
        return false;
    }

    default PalettedBlockStorage getBiomeStorage(int y) {
        return null;
    }

    default int getBiomeId(int x, int y, int z) {
        return getBiomeId(x, z);
    }

    int getBiomeId(int x, int z);

    @Deprecated
    default void setBiomeIdAndColor(int x, int z, int idAndColor) {

    }

    default void setBiomeId(int x, int y, int z, int biomeId) {
        this.setBiomeId(x, z, biomeId);
    }

    default void setBiomeId(int x, int z, int biomeId)  {
        setBiomeId(x, z, (byte) biomeId);
    }

    default void setBiomeId(int x, int y, int z, byte biomeId) {
        this.setBiomeId(x, z, biomeId);
    }

    void setBiomeId(int x, int z, byte biomeId);

    default void setBiome(int x, int y, int z, cn.nukkit.level.biome.Biome biome) {
        setBiomeId(x, z, biome.getId());
    }

    default void setBiome(int x, int z, cn.nukkit.level.biome.Biome biome) {
        setBiomeId(x, z, (byte) biome.getId());
    }

    int getBiomeColor(int x, int z);

    void setBiomeColor(int x, int z, int r, int g, int b);

    boolean isLightPopulated();

    void setLightPopulated();

    void setLightPopulated(boolean value);

    boolean isPopulated();

    void setPopulated();

    void setPopulated(boolean value);

    boolean isGenerated();

    void setGenerated();

    void setGenerated(boolean value);

    void addEntity(Entity entity);

    void removeEntity(Entity entity);

    void addBlockEntity(BlockEntity blockEntity);

    void removeBlockEntity(BlockEntity blockEntity);

    Map<Long, Entity> getEntities();

    Map<Long, BlockEntity> getBlockEntities();

    BlockEntity getTile(int x, int y, int z);

    boolean isLoaded();

    boolean load() throws IOException;

    boolean load(boolean generate) throws IOException;

    boolean unload() throws Exception;

    boolean unload(boolean save) throws Exception;

    boolean unload(boolean save, boolean safe) throws Exception;

    void initChunk();

    byte[] getBiomeIdArray();

    int[] getBiomeColorArray();

    byte[] getHeightMapArray();

    byte[] getBlockIdArray(int layer);

    default byte[] getBlockIdArray() {
        return getBlockIdArray(0);
    }

    byte[] getBlockDataArray(int layer);

    default byte[] getBlockDataArray() {
        return getBlockDataArray(0);
    }

    Map<Integer, Integer> getBlockExtraDataArray();

    byte[] getBlockSkyLightArray();

    byte[] getBlockLightArray();

    byte[] toBinary();

    byte[] toFastBinary();

    boolean hasChanged();

    void setChanged();

    void setChanged(boolean changed);
}
