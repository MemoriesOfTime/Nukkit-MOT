package cn.nukkit.level.format.leveldb.structure;

import cn.nukkit.level.DimensionData;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
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
    short[] heightMap;
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

    public ChunkBuilder heightMap(short[] heightMap) {
        this.heightMap = heightMap;
        return this;
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

    public Chunk build() {
        Preconditions.checkNotNull(levelProvider);
        if (state == null) state = ChunkState.NEW;
        if (sections == null) sections = new ChunkSection[levelProvider.getLevel().getDimensionData().getChunkSectionCount()];
        if (heightMap == null) heightMap = new short[256];
        if (entities == null) entities = new ArrayList<>();
        if (blockEntities == null) blockEntities = new ArrayList<>();
        if (extraData == null) extraData = new CompoundTag();
        return new Chunk(
                state,
                chunkX,
                chunkZ,
                levelProvider,
                sections,
                heightMap,
                entities,
                blockEntities,
                extraData
        );
    }

    public Chunk emptyChunk(int chunkX, int chunkZ) {
        Preconditions.checkNotNull(levelProvider);
        return new Chunk(chunkX, chunkZ, levelProvider);
    }

}
