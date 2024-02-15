package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.tag.CompoundTag;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nukkit.level.format.leveldb.LevelDbConstants.SUB_CHUNK_2D_SIZE;

public class LevelDBChunk extends BaseChunk {
    protected PalettedBlockStorage[] biomes3d; //TODO

    protected boolean terrainGenerated;
    protected boolean terrainPopulated;

    protected boolean subChunksDirty;
    protected boolean heightmapOrBiomesDirty;

    public final Lock ioLock;

    private final DimensionData dimensionData;

    public LevelDBChunk(@Nullable LevelProvider level, int chunkX, int chunkZ) {
        this(level, chunkX, chunkZ, null, new int[SUB_CHUNK_2D_SIZE], null, null, null, null);
    }

    public LevelDBChunk(@Nullable LevelProvider level, int chunkX, int chunkZ, @Nullable ChunkSection[] sections,
                        @Nullable int[] heightmap, @Nullable byte[] biomes2d, @Nullable PalettedBlockStorage[] biomes3d,
                        @Nullable List<CompoundTag> entities, @Nullable List<CompoundTag> blockEntities) {
        this.ioLock = new ReentrantLock();
        this.provider = level;
        this.setPosition(chunkX, chunkZ);

        this.dimensionData = level == null ? DimensionEnum.OVERWORLD.getDimensionData() : level.getLevel().getDimensionData();
        int minSectionY = this.dimensionData.getMinSectionY();
        int maxSectionY = this.dimensionData.getMaxSectionY();
        this.sections = new LevelDBChunkSection[this.dimensionData.getHeight() >> 4];
        if (sections != null) {
            for (int i = minSectionY; i <= maxSectionY; i++) {
                int sectionsY = i + this.dimensionData.getSectionOffset();
                if (sectionsY >= sections.length || sections[sectionsY] == null) {
                    this.sections[sectionsY] = new LevelDBChunkSection(this, sectionsY);
                } else {
                    ChunkSection section = sections[sectionsY];
                    ((LevelDBChunkSection) section).setParent(this);
                    this.sections[sectionsY] = section;
                }
            }
        } else {
            for (int i = 0; i < this.sections.length; i++) {
                this.sections[i] = new LevelDBChunkSection(this, i);
            }
        }

        this.heightMap = new byte[SUB_CHUNK_2D_SIZE];
        if (heightmap != null && heightmap.length == SUB_CHUNK_2D_SIZE) {
            for (int i=0; i<heightmap.length; i++) {
                this.heightMap[i] = (byte) heightmap[i];
            }
        } else {
            Arrays.fill(this.heightMap, (byte)-1);
            this.recalculateHeightMap();
        }

        if (biomes2d != null && biomes2d.length == SUB_CHUNK_2D_SIZE) {
            this.biomes = biomes2d;
        } else {
            this.biomes = new byte[SUB_CHUNK_2D_SIZE];
        }
        this.biomes3d = biomes3d;

        this.NBTentities = entities;
        this.NBTtiles = blockEntities;
    }

    @Override
    public int getSectionOffset() {
        return this.dimensionData.getSectionOffset();
    }

    @Override
    public boolean isGenerated() {
        return this.terrainGenerated || this.terrainPopulated;
    }

    @Override
    public void setGenerated() {
        this.setGenerated(true);
    }

    @Override
    public void setGenerated(boolean state) {
        if (this.terrainGenerated != state) {
            this.terrainGenerated = state;
            this.setChanged();
        }
    }

    @Override
    public int[] getBiomeColorArray() {
        return new int[0];
    }

    @Override
    public boolean isPopulated() {
        return this.terrainPopulated;
    }

    @Override
    public void setPopulated() {
        this.setPopulated(true);
    }

    @Override
    public void setPopulated(boolean state) {
        if (this.terrainPopulated != state) {
            this.terrainPopulated = state;
            this.setChanged();
        }
    }

    @Override
    public boolean has3dBiomes() {
        return this.biomes3d != null && this.biomes3d.length > 0;
    }

    public PalettedBlockStorage getBiomeStorage(int y) {
        //TODO
        return null;
    }

    public int getBiomeId(int x, int y, int z) {
        return this.biomes[index2d(x, z)] & 0xff;
//        return this.biomes3d[0].get(x, 0, z);
    }

    public void setBiomeId(int x, int y, int z, byte biomeId) {
        int index = index2d(x, z);
        if (this.biomes[index] == biomeId) {
            return;
        }
        this.biomes[index] = biomeId;
//        this.biomes3d[0].set(x, 0, z, biomeId);

        this.heightmapOrBiomesDirty = true;
        this.setChanged();
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        return this.getHighestBlockAt(x, z) >= y ? 15 : 0;
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        return Block.light[this.getBlockId(0, x, y, z)];
    }

    @Override
    public void populateSkyLight() {
        //TODO
    }

    @Override
    public void setHeightMap(int x, int z, int value) {
        super.setHeightMap(x, z, value);

        this.heightmapOrBiomesDirty = true;
        this.setChanged();
    }

    public void onSubChunkBlockChanged(LevelDBChunkSection subChunk, int layer, int x, int y, int z, int previousId, int newId) {
        assert previousId != newId;

        subChunksDirty = true;

        if (layer != 0) {
            return;
        }

        int previousBlockId = previousId >> Block.DATA_BITS;
        int newBlockId = newId >> Block.DATA_MASK;
        if (previousBlockId == newBlockId) {
            return;
        }

        //TODO
        if (true) return;
        boolean lightBlocking = false;
        /*boolean lightBlocking = Block.lightBlocking[newBlockId];
        if (lightBlocking == Block.lightBlocking[previousBlockId]) {
            return;
        }*/

        if (lightBlocking) {
            int height = getHeightMap(x, z);
            int worldY = (subChunk.getY() << 4) | y;
            if (height <= worldY) {
                setHeightMap(x, z, worldY + 1);
            }
        } else {
            int subChunkY = subChunk.getY();
            int worldY = (subChunkY << 4) | y;
            int height = getHeightMap(x, z);
            if (height == (worldY + 1)) {
                for (int localY = y; localY >= 0; localY--) {
                    /*if (!Block.lightBlocking[subChunk.getBlockId(0, x, localY, z)]) {
                        continue;
                    }*/
                    this.setHeightMap(x, z, ((subChunkY << 4) | localY) + 1);
                    return;
                }

                // thread safe?
                boolean hasColumn = false;
                SUB_CHUNKS:
                for (int chunkY = subChunkY - 1; chunkY >= 0; chunkY--) {
                    ChunkSection section = this.getSection(chunkY);
                    for (int localY = 15; localY >= 0; localY--) {
                        /*if (!Block.lightBlocking[section.getBlockId(0, x, localY, z)]) {
                            continue;
                        }*/
                        this.setHeightMap(x, z, ((chunkY << 4) | localY) + 1);
                        hasColumn = true;
                        break SUB_CHUNKS;
                    }
                }

                if (!hasColumn) {
                    this.setHeightMap(x, z, 0);
                }
            }
        }
    }

    public boolean isSubChunksDirty() {
        return this.subChunksDirty;
    }

    public boolean isHeightmapOrBiomesDirty() {
        return this.heightmapOrBiomesDirty;
    }

    public void setHeightmapOrBiomesDirty() {
        this.heightmapOrBiomesDirty = true;
    }

    @Deprecated
    @Override
    public byte[] toBinary() {
        return new byte[0];
    }

    @Override
    public boolean compress() {
        super.compress();

        boolean dirty = false;
        for (int i = 0; i < SECTION_COUNT; i++) {
            dirty |= this.sections[i].compress();
        }
        this.subChunksDirty |= dirty;
        return dirty;
    }

    @Override
    public BaseChunk clone() {
        LevelDBChunk chunk = (LevelDBChunk) super.clone();

        for (int i = 0; i < chunk.sections.length; i++) {
            ChunkSection section = chunk.sections[i];
            if (section == null) {
                continue;
            }
            ((LevelDBChunkSection) section).setParent(chunk);
        }

        return chunk;
    }

    @SuppressWarnings("unused")
    public static LevelDBChunk getEmptyChunk(int chunkX, int chunkZ) {
        return getEmptyChunk(chunkX, chunkZ, null);
    }

    public static LevelDBChunk getEmptyChunk(int chunkX, int chunkZ, LevelProvider provider) {
        return new LevelDBChunk(provider, chunkX, chunkZ);
    }

    protected static int index2d(int x, int z) {
        int index = (z << 4) | x;
        if (index < 0 || index >= SUB_CHUNK_2D_SIZE) {
            throw new IllegalArgumentException("Invalid index: " + x + ", " + z );
        }
        return index;
    }
}
