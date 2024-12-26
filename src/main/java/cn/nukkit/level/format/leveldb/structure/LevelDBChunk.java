package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.block.Block;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.util.BitArrayVersion;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.ByteTag;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.BinaryStream;
import cn.nukkit.utils.BlockUpdateEntry;
import cn.nukkit.utils.Zlib;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.*;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.SUB_CHUNK_2D_SIZE;

public class LevelDBChunk extends BaseChunk {

    private ChunkState state;

    protected PalettedBlockStorage[] biomes3d;

    protected boolean subChunksDirty;
    protected boolean heightmapOrBiomesDirty;

    private final Lock writeLock = new ReentrantLock();

    private final DimensionData dimensionData;

    public LevelDBChunk(@Nullable LevelProvider level, int chunkX, int chunkZ) {
        this(level, chunkX, chunkZ, new LevelDBChunkSection[0], new int[SUB_CHUNK_2D_SIZE], null, null, null, null, ChunkState.NEW);
    }

    public LevelDBChunk(@Nullable LevelProvider provider, int chunkX, int chunkZ, @NotNull ChunkSection[] sections,
                        @Nullable int[] heightmap, @Nullable byte[] biomes2d, @Nullable PalettedBlockStorage[] biomes3d,
                        @Nullable List<CompoundTag> entities, @Nullable List<CompoundTag> blockEntities, @NotNull ChunkState state) {
        this.provider = provider;
        this.setPosition(chunkX, chunkZ);

        this.dimensionData = provider == null ? DimensionData.LEGACY_DIMENSION : provider.getLevel().getDimensionData();
        int minSectionY = this.dimensionData.getMinSectionY();
        int maxSectionY = this.dimensionData.getMaxSectionY();
        this.sections = new LevelDBChunkSection[this.dimensionData.getHeight() >> 4];
        for (int i = minSectionY; i <= maxSectionY; i++) {
            int sectionsY = i + this.dimensionData.getSectionOffset();
            if (sectionsY >= sections.length || sections[sectionsY] == null) {
                this.sections[sectionsY] = new LevelDBChunkSection(this, i);
            } else {
                ChunkSection section = sections[sectionsY];
                ((LevelDBChunkSection) section).setParent(this);
                this.sections[sectionsY] = section;
            }
        }

        this.heightMap = new byte[SUB_CHUNK_2D_SIZE];
        if (heightmap != null && heightmap.length == SUB_CHUNK_2D_SIZE) {
            for (int i=0; i<heightmap.length; i++) {
                this.heightMap[i] = (byte) heightmap[i];
            }
        } else {
            Arrays.fill(this.heightMap, (byte) 255);
        }

        if (biomes2d != null && biomes2d.length == SUB_CHUNK_2D_SIZE) {
            this.biomes = biomes2d;
        } else {
            this.biomes = new byte[SUB_CHUNK_2D_SIZE];
        }
        this.biomes3d = biomes3d;

        this.NBTentities = entities;
        this.NBTtiles = blockEntities;

        this.state = state;
    }

    public void setNbtBlockEntities(List<CompoundTag> blockEntities) {
        this.NBTtiles = blockEntities;
    }

    public void setNbtEntities(List<CompoundTag> entities) {
        this.NBTentities = entities;
    }

    @Override
    public int getSectionOffset() {
        return this.dimensionData.getSectionOffset();
    }

    public void setState(@NotNull ChunkState state) {
        this.state = state;
    }

    public ChunkState getState() {
        return state;
    }

    @Override
    public boolean isGenerated() {
        return this.state.ordinal() >= ChunkState.GENERATED.ordinal();
    }

    @Override
    public void setGenerated() {
        this.setGenerated(true);
    }

    @Override
    public void setGenerated(boolean value) {
        if (this.isGenerated() == value) {
            return;
        }
        this.setChanged();

        if (value) {
            if (this.state.ordinal() < ChunkState.GENERATED.ordinal()) {
                this.setState(ChunkState.GENERATED);
            }
        } else if (this.state.ordinal() >= ChunkState.GENERATED.ordinal()) {
            this.setState(ChunkState.NEW);
        }
    }

    @Override
    public int[] getBiomeColorArray() {
        return new int[0];
    }

    @Override
    public boolean isPopulated() {
        return this.state.ordinal() >= ChunkState.POPULATED.ordinal();
    }

    @Override
    public void setPopulated() {
        this.setPopulated(true);
    }

    @Override
    public void setPopulated(boolean value) {
        if (this.isPopulated() == value) {
            return;
        }
        this.setChanged();

        if (value) {
            if (this.state.ordinal() < ChunkState.POPULATED.ordinal()) {
                this.setState(ChunkState.POPULATED);
            }
        } else if (this.state.ordinal() >= ChunkState.POPULATED.ordinal()) {
            this.setState(ChunkState.GENERATED);
        }
    }

    @Override
    public void setBiomeIdArray(byte[] biomeIdArray) {
        super.setBiomeIdArray(biomeIdArray);
        if (this.has3dBiomes()) {
            this.convertBiomesTo3d(biomeIdArray);
        }
        this.setHeightmapOrBiomesDirty();
        this.setChanged();
    }

    @Override
    public boolean has3dBiomes() {
        return this.biomes3d != null && this.biomes3d.length > 0;
    }

    @Override
    public PalettedBlockStorage getBiomeStorage(int y) {
        if (!this.has3dBiomes()) {
            throw new IllegalStateException("Chunk does not have 3D biomes");
        }

        int index = y + this.getSectionOffset();
        if (index >= this.biomes3d.length) {
            index = 0;
        }
        if (this.biomes3d[index] == null) {
            for (int i = index; i >= 0; i--) {
                if (this.biomes3d[i] != null) {
                    this.biomes3d[index] = this.biomes3d[i].copy();
                    break;
                }
            }
            if (this.biomes3d[index] == null) {
                this.biomes3d[index] = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, 0);
            }
        }
        return this.biomes3d[index];
    }

    @Override
    public int getBiomeId(int x, int y, int z) {
        if (this.has3dBiomes()) {
            return this.getBiomeStorage(y >> 4).getBlock(x, y & 0xf, z);
        }
        return super.getBiomeId(x, z);
    }

    @Override
    public int getBiomeId(int x, int z) {
        return this.getBiomeId(x, 0, z);
    }

    @Override
    public void setBiomeId(int x, int y, int z, int biomeId) {
        if (!this.has3dBiomes()) {
            this.convertBiomesTo3d(this.biomes);
        }
        this.getBiomeStorage(y >> 4).setBlock(x, y & 0xf, z, biomeId);
        this.setHeightmapOrBiomesDirty();
        this.setChanged();
    }

    @Override
    public void setBiomeId(int x, int z, int biomeId)  {
        for (int sectionIndex = 0; sectionIndex < this.sections.length; sectionIndex++) {
            int sectionStartY = (sectionIndex - this.getSectionOffset()) << 4;
            for (int y = 0; y < 16; y++) {
                this.setBiomeId(x, sectionStartY + y, z, biomeId);
            }
        }
    }

    @Override
    public void setBiomeId(int x, int y, int z, byte biomeId) {
        this.setBiomeId(x, y, z, biomeId & 0xff);
    }

    @Override
    public void setBiomeId(int x, int z, byte biomeId) {
        this.setBiomeId(x, z, biomeId & 0xff);
    }

    @Override
    public void setBiome(int x, int y, int z, cn.nukkit.level.biome.Biome biome) {
        this.setBiomeId(x, y, z, biome.getId());
    }

    @Override
    public void setBiome(int x, int z, cn.nukkit.level.biome.Biome biome) {
        setBiomeId(x, z, biome.getId());
    }

    protected void convertBiomesTo3d(byte[] biomes) {
        PalettedBlockStorage palettedBlockStorage = PalettedBlockStorage.createWithDefaultState(BitArrayVersion.V0, Biome.getBiomeIdOrCorrect(biomes[0] & 0xFF));
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                int biome = biomes[x << 4 | z] & 0xFF;
                for (int y = 0; y < 16; y++) {
                    palettedBlockStorage.setBlock(x, y, z, biome);
                }
            }
        }

        int sectionCount = this.provider == null ? this.sections.length : this.provider.getLevel().getDimensionData().getHeight() >> 4;
        PalettedBlockStorage[] storages = new PalettedBlockStorage[sectionCount];
        for (int i = 0; i < sectionCount; i++) {
            storages[i] = palettedBlockStorage.copy();
        }

        this.biomes3d = storages;
    }

    public void setBiomes3d(PalettedBlockStorage[] biomes3d) {
        this.biomes3d = biomes3d;
        this.setHeightmapOrBiomesDirty();
        this.setChanged();
    }

    public void setBiomes3d(int y, PalettedBlockStorage biomes3d) {
        if (!this.has3dBiomes()) {
            this.convertBiomesTo3d(this.biomes);
        }

        int index = y + this.getSectionOffset();
        if (index >= this.biomes3d.length) {
            index = 0;
        }

        this.biomes3d[index] = biomes3d;
        this.setHeightmapOrBiomesDirty();
        this.setChanged();
    }

    @Override
    public int getBlockSkyLight(int x, int y, int z) {
        ChunkSection section0 = this.getSection(y >> 4);
        if (!(section0 instanceof LevelDBChunkSection)) {
            return section0.getBlockSkyLight(x, y & 0x0f, z);
        }

        LevelDBChunkSection section = (LevelDBChunkSection) section0;
        if (section.skyLight != null) {
            return section.getBlockSkyLight(x, y & 0x0f, z);
        } else if (!section.hasSkyLight) {
            return 0;
        } else {
            int height = this.getHighestBlockAt(x, z);
            if (height < y) {
                return 15;
            } else if (height == y) {
                return Block.isBlockTransparentById(this.getBlockId(x, y, z)) ? 15 : 0;
            } else {
                return section.getBlockSkyLight(x, y & 0x0f, z);
            }
        }
    }

    @Override
    public int getBlockLight(int x, int y, int z) {
        ChunkSection section0 = this.getSection(y >> 4);

        if (!(section0 instanceof LevelDBChunkSection section)) {
            return section0.getBlockLight(x, y & 0x0f, z);
        }

        if (section.blockLight != null) {
            return section.getBlockLight(x, y & 0x0f, z);
        } else if (!section.hasBlockLight) {
            return 0;
        } else {
            return section.getBlockLight(x, y & 0x0f, z);
        }

    }

    @Override
    public void setHeightMap(int x, int z, int value) {
        super.setHeightMap(x, z, value);

        this.setHeightmapOrBiomesDirty();
        this.setChanged();
    }

    public void onSubChunkBlockChanged(LevelDBChunkSection subChunk, int x, int y, int z, int layer, int previousId, int newId) {
        assert previousId != newId;

        this.subChunksDirty = true;

        /*if (layer != 0) {
            return;
        }

        int previousBlockId = previousId >> Block.DATA_BITS;
        int newBlockId = newId >> Block.DATA_MASK;
        if (previousBlockId == newBlockId) {
            return;
        }

        boolean lightBlocking = Block.lightBlocking[newBlockId];
        if (lightBlocking == Block.lightBlocking[previousBlockId]) {
            return;
        }

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
                    *//*if (!Block.lightBlocking[subChunk.getBlockId(0, x, localY, z)]) {
                        continue;
                    }*//*
                    this.setHeightMap(x, z, ((subChunkY << 4) | localY) + 1);
                    return;
                }

                // thread safe?
                boolean hasColumn = false;
                SUB_CHUNKS:
                for (int chunkY = subChunkY - 1; chunkY >= 0; chunkY--) {
                    ChunkSection section = this.getSection(chunkY);
                    for (int localY = 15; localY >= 0; localY--) {
                        *//*if (!Block.lightBlocking[section.getBlockId(0, x, localY, z)]) {
                            continue;
                        }*//*
                        this.setHeightMap(x, z, ((chunkY << 4) | localY) + 1);
                        hasColumn = true;
                        break SUB_CHUNKS;
                    }
                }

                if (!hasColumn) {
                    this.setHeightMap(x, z, 0);
                }
            }
        }*/
    }

    public boolean isSubChunksDirty() {
        return this.subChunksDirty;
    }

    public void setAllSubChunksDirty() {
        this.subChunksDirty = true;

        for (ChunkSection chunkSection : this.sections) {
            if (chunkSection != null) {
                chunkSection.setDirty();
            }
        }
    }

    public boolean isHeightmapOrBiomesDirty() {
        return this.heightmapOrBiomesDirty;
    }

    public void setHeightmapOrBiomesDirty() {
        this.heightmapOrBiomesDirty = true;
    }

    @Override
    @Deprecated
    public byte[] toFastBinary() {
        CompoundTag chunk = chunkNBT();

        try {
            return NBTIO.write(chunk, ByteOrder.BIG_ENDIAN);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    @Deprecated
    public byte[] toBinary() {
        CompoundTag chunk = chunkNBT();

        try {
            return Zlib.deflate(NBTIO.write(chunk, ByteOrder.BIG_ENDIAN), 7);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private CompoundTag chunkNBT() {
        CompoundTag nbt = this.getNBT().copy();
        nbt.remove("BiomeColors");

        nbt.putInt("xPos", this.getX());
        nbt.putInt("zPos", this.getZ());

        ListTag<CompoundTag> sectionList = new ListTag<>("Sections");
        for (ChunkSection section : this.getSections()) {
            if (section instanceof EmptyChunkSection) {
                continue;
            }
            CompoundTag s = new CompoundTag();
            s.putByte("Y", (section.getY()));
            s.putByteArray("Blocks", section.getIdArray());
            s.putByteArray("Data", section.getDataArray());
            s.putByteArray("BlockLight", section.getLightArray());
            s.putByteArray("SkyLight", section.getSkyLightArray());
            sectionList.add(s);
        }
        nbt.putList(sectionList);

        nbt.putByteArray("Biomes", this.getBiomeIdArray());

        int[] heightInts = new int[256];
        byte[] heightBytes = this.getHeightMapArray();
        for (int i = 0; i < heightInts.length; i++) {
            heightInts[i] = heightBytes[i] & 0xFF;
        }
        nbt.putIntArray("HeightMap", heightInts);

        ArrayList<CompoundTag> entities = new ArrayList<>();
        for (Entity entity : this.getEntities().values()) {
            if (entity.canBeSavedWithChunk() && !entity.closed) {
                entity.saveNBT();
                entities.add(entity.namedTag);
            }
        }

        ListTag<CompoundTag> entityListTag = new ListTag<>("Entities");
        entityListTag.setAll(entities);
        nbt.putList(entityListTag);

        ArrayList<CompoundTag> tiles = new ArrayList<>();
        for (BlockEntity blockEntity : this.getBlockEntities().values()) {
            if (blockEntity.canSaveToStorage()) {
                blockEntity.saveNBT();
                tiles.add(blockEntity.namedTag);
            }
        }
        ListTag<CompoundTag> tileListTag = new ListTag<>("TileEntities");
        tileListTag.setAll(tiles);
        nbt.putList(tileListTag);

        Set<BlockUpdateEntry> entries = this.provider.getLevel().getPendingBlockUpdates(this);
        if (entries != null) {
            ListTag<CompoundTag> tileTickTag = new ListTag<>("TileTicks");
            long totalTime = this.provider.getLevel().getCurrentTick();

            for (BlockUpdateEntry entry : entries) {
                CompoundTag entryNBT = new CompoundTag()
                        .putString("i", entry.block.getSaveId())
                        .putInt("x", entry.pos.getFloorX())
                        .putInt("y", entry.pos.getFloorY())
                        .putInt("z", entry.pos.getFloorZ())
                        .putInt("t", (int) (entry.delay - totalTime))
                        .putInt("p", entry.priority);
                tileTickTag.add(entryNBT);
            }

            nbt.putList(tileTickTag);
        }

        BinaryStream extraData = new BinaryStream();
        Map<Integer, Integer> extraDataArray = this.getBlockExtraDataArray();
        extraData.putInt(extraDataArray.size());
        for (Map.Entry<Integer, Integer> entry : extraDataArray.entrySet()) {
            extraData.putInt(entry.getKey());
            extraData.putShort(entry.getValue());
        }
        nbt.putByteArray("ExtraData", extraData.getBuffer());

        CompoundTag chunk = new CompoundTag("");
        chunk.putCompound("Level", nbt);
        return chunk;
    }

    private CompoundTag getNBT() {
        CompoundTag tag = new CompoundTag();
        tag.put("LightPopulated", new ByteTag("LightPopulated", (byte) (this.isLightPopulated() ? 1 : 0)));
        tag.put("V", new ByteTag("V", (byte) 1));
        tag.put("TerrainGenerated", new ByteTag("TerrainGenerated", (byte) (this.isGenerated() ? 1 : 0)));
        tag.put("TerrainPopulated", new ByteTag("TerrainPopulated", (byte) (this.isPopulated() ? 1 : 0)));
        return tag;
    }

    @Override
    public boolean compress() {
        boolean result = super.compress();
        for (ChunkSection section : this.getSections()) {
            if (section instanceof LevelDBChunkSection && !section.isEmpty()) {
                result |= section.compress();
            }
        }
        return result;
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

        if (this.has3dBiomes()) {
            PalettedBlockStorage[] biomes = new PalettedBlockStorage[this.biomes3d.length];
            for (int i = 0; i < this.biomes3d.length; i++) {
                biomes[i] = this.biomes3d[i].copy();
            }
            chunk.setBiomes3d(biomes);
        }

        return chunk;
    }

    @Override
    public LevelDBChunk cloneForChunkSending() {
        LevelDBChunk chunk = (LevelDBChunk) super.cloneForChunkSending();

        for (int i = 0; i < chunk.sections.length; i++) {
            ChunkSection section = chunk.sections[i];
            if (section == null) {
                continue;
            }
            ((LevelDBChunkSection) section).setParent(chunk);
        }

        if (this.has3dBiomes()) {
            PalettedBlockStorage[] biomes = new PalettedBlockStorage[this.biomes3d.length];
            for (int i = 0; i < this.biomes3d.length; i++) {
                PalettedBlockStorage storage = this.biomes3d[i];
                if (storage != null) {
                    biomes[i] = storage.copy();
                }
            }
            chunk.setBiomes3d(biomes);
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

    public Lock writeLock() {
        return this.writeLock;
    }
}
