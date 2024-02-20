package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.util.PalettedBlockStorage;
import cn.nukkit.nbt.tag.CompoundTag;
import com.google.common.base.Preconditions;
import lombok.Getter;

import java.util.ArrayList;
import java.util.List;

public class ChunkBuilder {

    ChunkState state;
    @Getter
    int chunkZ;
    @Getter
    int chunkX;
    LevelProvider levelProvider;
    ChunkSection[] sections;
    int[] heightMap;
    byte[] biome2d;
    PalettedBlockStorage[] biomeStorage;
    boolean biome3d;
    List<CompoundTag> entities;
    List<CompoundTag> blockEntities;
    CompoundTag extraData;

    private ChunkBuilder() {

    }

    public ChunkBuilder(int chunkX, int chunkZ, LevelDBProvider levelDBProvider) {
        this.chunkX = chunkX;
        this.chunkZ = chunkZ;
        this.levelProvider = Preconditions.checkNotNull(levelDBProvider, "levelProvider");
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

    public ChunkBuilder levelProvider(LevelProvider levelProvider) {
        this.levelProvider = levelProvider;
        return this;
    }

    public DimensionData getDimensionData() {
        Preconditions.checkNotNull(levelProvider);
        return levelProvider.getLevel().getDimensionData();
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

    public ChunkBuilder biomeStorage(PalettedBlockStorage[] biomeStorage) {
        this.biomeStorage = biomeStorage;
        this.biome3d = true;
        return this;
    }

    public boolean hasBiome3d() {
        return biome3d;
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
        Preconditions.checkNotNull(levelProvider);
        if (state == null) state = ChunkState.NEW;
        if (sections == null) sections = new ChunkSection[levelProvider.getLevel().getDimensionData().getHeight()];
        if (heightMap == null) heightMap = new int[256];
        if (entities == null) entities = new ArrayList<>();
        if (blockEntities == null) blockEntities = new ArrayList<>();
        if (extraData == null) extraData = new CompoundTag();
        if (state == null) state = ChunkState.NEW;
        LevelDBChunk levelDBChunk = new LevelDBChunk(
                levelProvider,
                chunkX,
                chunkZ,
                sections,
                heightMap,
                biome2d,
                biomeStorage,
                entities,
                blockEntities,
                state
        );
        return levelDBChunk;
    }

}
