package cn.nukkit.level.generator.template;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;

import java.util.function.Consumer;

public interface ReadableStructureTemplate extends StructureTemplate {
    ReadableStructureTemplate load(CompoundTag root);

    void placeInChunk(FullChunk chunk, NukkitRandom random, BlockVector3 position, StructurePlaceSettings settings);

    void placeInChunk(FullChunk chunk, NukkitRandom random, BlockVector3 position, int integrity, Consumer<CompoundTag> blockActorProcessor);

    void placeInLevel(ChunkManager level, NukkitRandom random, BlockVector3 position, int integrity, Consumer<CompoundTag> blockActorProcessor);
}
