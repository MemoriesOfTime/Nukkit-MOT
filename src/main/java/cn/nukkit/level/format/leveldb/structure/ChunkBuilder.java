package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.serializer.ChunkDataLoader;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.tag.CompoundTag;
import com.google.common.base.Preconditions;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class ChunkBuilder {

    ChunkState state;
    @Getter
    int chunkZ;
    @Getter
    int chunkX;
    LevelDBProvider provider;
    ChunkSection[] sections;
    int[] heightMap;
    byte[] biome2d;
    PalettedBlockStorage[] biomes3d;
    boolean has3dBiomes;
    List<CompoundTag> entities;
    List<CompoundTag> blockEntities;
    CompoundTag extraData;

    private final List<ChunkDataLoader> chunkDataLoaders = new ObjectArrayList<>();

    private boolean dirty;

    public ChunkBuilder(int chunkX, int chunkZ, LevelDBProvider levelDBProvider) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.provider = Preconditions.checkNotNull(levelDBProvider, "levelProvider");
    }

    public ChunkBuilder chunkX(int chunkX) {
        this.chunkX = chunkX;
        return this;
    }

    public ChunkBuilder chunkZ(int chunkZ) {
        this.chunkZ = chunkZ;
        return this;
    }

    public ChunkBuilder state(ChunkState state) {
        this.state = state;
        return this;
    }

    public ChunkBuilder dataLoader(ChunkDataLoader chunkDataLoader) {
        if (chunkDataLoader == null) throw new NullPointerException();
        this.chunkDataLoaders.add(chunkDataLoader);
        return this;
    }

    public ChunkBuilder dirty() {
        this.dirty = true;
        return this;
    }

    public ChunkBuilder levelProvider(LevelDBProvider levelProvider) {
        this.provider = levelProvider;
        return this;
    }

    public DimensionData getDimensionData() {
        Preconditions.checkNotNull(provider);
        return provider.getLevel().getDimensionData();
    }

    public ChunkBuilder sections(ChunkSection[] sections) {
        this.sections = sections;
        return this;
    }

    public ChunkSection[] getSections() {
        return sections;
    }

    public ChunkBuilder heightMap(int[] heightMap) {
        this.heightMap = heightMap;
        return this;
    }

    public ChunkBuilder biome2d(byte[] biome2d) {
        this.biome2d = biome2d;
        return this;
    }

    public ChunkBuilder biomes3d(PalettedBlockStorage[] biomeStorage) {
        this.biomes3d = biomeStorage;
        this.has3dBiomes = true;
        return this;
    }

    public boolean hasBiome3d() {
        return has3dBiomes;
    }

    public ChunkBuilder entities(List<CompoundTag> entities) {
        this.entities = entities;
        return this;
    }

    public ChunkBuilder blockEntities(List<CompoundTag> blockEntities) {
        this.blockEntities = blockEntities;
        return this;
    }

    public ChunkBuilder extraData(CompoundTag extraData) {
        this.extraData = extraData;
        return this;
    }

    public LevelDBChunk build() {
        Preconditions.checkNotNull(provider);
        if (state == null) state = ChunkState.NEW;
        if (sections == null) sections = new ChunkSection[provider.getLevel().getDimensionData().getHeight()];
        if (heightMap == null) heightMap = new int[256];
        if (entities == null) entities = new ArrayList<>();
        if (blockEntities == null) blockEntities = new ArrayList<>();
        if (extraData == null) extraData = new CompoundTag();
        if (state == null) state = ChunkState.NEW;

        LevelDBChunk levelDBChunk = new LevelDBChunk(
                provider,
                chunkX,
                chunkZ,
                sections,
                heightMap,
                biome2d,
                biomes3d,
                entities,
                blockEntities,
                state
        );

        this.chunkDataLoaders.forEach(loader -> loader.initChunk(levelDBChunk, this.provider));

        if (this.dirty) {
            levelDBChunk.setChanged();
        }

        return levelDBChunk;
    }

    public boolean has3dBiomes() {
        return this.has3dBiomes;
    }

    public String debugString() {
        return this.provider.getName() + "(x=" + this.chunkX + ", z=" + this.chunkZ + ")";
    }
}
