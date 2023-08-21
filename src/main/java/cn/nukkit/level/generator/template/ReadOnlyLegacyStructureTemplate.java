package cn.nukkit.level.generator.template;

import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.task.ActorSpawnTask;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.IntTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.Comparator;
import java.util.function.Consumer;

public class ReadOnlyLegacyStructureTemplate extends AbstractLegacyStructureTemplate implements ReadableStructureTemplate {
    @Override
    public ReadableStructureTemplate load(final CompoundTag root) {
        clean();

        final ListTag<IntTag> size = root.getList("size", IntTag.class);
        this.size = new BlockVector3(size.get(0).data, size.get(1).data, size.get(2).data);

        final SimplePalette palette = new SimplePalette();

        final ListTag<CompoundTag> paletteTag = root.getList("palette", CompoundTag.class);
        for (int i = 0; i < paletteTag.size(); ++i) {
            final CompoundTag tag = paletteTag.get(i);
            palette.addMapping(new BlockEntry(tag.getInt("id"), tag.getInt("meta")), i);
        }

        final ListTag<CompoundTag> blocks = root.getList("blocks", CompoundTag.class);
        for (int i = 0; i < blocks.size(); ++i) {
            final CompoundTag block = blocks.get(i);
            final ListTag<IntTag> pos = block.getList("pos", IntTag.class);

            blockInfoList.add(new StructureBlockInfo(
                new BlockVector3(pos.get(0).data, pos.get(1).data, pos.get(2).data),
                palette.stateFor(block.getInt("state")),
                block.contains("nbt") ? block.getCompound("nbt") : null)
            );
        }

        blockInfoList.sort(Comparator.comparingInt(block -> block.pos.y));

        final ListTag<CompoundTag> entities = root.getList("entities", CompoundTag.class);
        for (int i = 0; i < entities.size(); ++i) {
            final CompoundTag entity = entities.get(i);
            if (entity.contains("nbt")) {
                final ListTag<DoubleTag> pos = entity.getList("pos", DoubleTag.class);
                final ListTag<IntTag> blockPos = entity.getList("blockPos", IntTag.class);

                entityInfoList.add(new StructureEntityInfo(
                    new Vector3(pos.get(0).data, pos.get(1).data, pos.get(2).data),
                    new BlockVector3(blockPos.get(0).data, blockPos.get(1).data, blockPos.get(2).data),
                    entity.getCompound("nbt")));
            }
        }

        return this;
    }

    @Override
    public void placeInChunk(final FullChunk chunk, final NukkitRandom random, final BlockVector3 position, final int integrity, final Consumer<CompoundTag> blockActorProcessor) {
        if (isInvalid()) {
            return;
        }

        for (final StructureBlockInfo blockInfo : blockInfoList) {
            final BlockEntry entry = blockInfo.state;

            if (entry.getId() == BlockID.AIR || integrity <= random.nextBoundedInt(100) && entry.getId() != BlockID.STRUCTURE_BLOCK) {
                continue;
            }

            System.out.println(entry.getId());

            final BlockVector3 vec = blockInfo.pos.add(0, position.y, 0);

            if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                chunk.setBlock(vec.x, vec.y, vec.z, entry.getId(), entry.getMeta());
            }

            if (blockInfo.nbt != null) {
                final CompoundTag nbt = blockInfo.nbt.clone();

                final BlockVector3 pos = blockInfo.pos.add(position);
                nbt.putInt("x", pos.x);
                nbt.putInt("y", pos.y);
                nbt.putInt("z", pos.z);

                if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                    Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
                }

                if (blockActorProcessor != null) {
                    blockActorProcessor.accept(nbt);
                }
            }
        }

        placeEntities(chunk.getProvider().getLevel(), position);

    }

    @Override
    public void placeInChunk(final FullChunk chunk, final NukkitRandom random, final BlockVector3 position, final StructurePlaceSettings settings) {
        if (isInvalid()) {
            return;
        }

        final boolean isIgnoreAir = settings.isIgnoreAir();
        final int integrity = settings.getIntegrity();
        final Consumer<CompoundTag> blockActorProcessor = settings.getBlockActorProcessor();
        final boolean isIntact = integrity >= 100;

        for (final StructureBlockInfo blockInfo : blockInfoList) {
            final BlockEntry entry = blockInfo.state;

            if (entry.getId() == BlockID.AIR && isIgnoreAir || !isIntact && integrity <= random.nextBoundedInt(100) && entry.getId() != BlockID.STRUCTURE_BLOCK) {
                continue;
            }

            final BlockVector3 vec = blockInfo.pos.add(position);

            if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                chunk.setBlock(vec.x & 0xf, vec.y, vec.z & 0xf, entry.getId(), entry.getMeta());
            } else if (!isIgnoreAir) {
                chunk.setBlock(vec.x & 0xf, vec.y, vec.z & 0xf, BlockID.AIR);
            }

            if (blockInfo.nbt != null) {
                final CompoundTag nbt = blockInfo.nbt.clone();

                nbt.putInt("x", vec.x);
                nbt.putInt("y", vec.y);
                nbt.putInt("z", vec.z);

                if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                    Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(chunk.getProvider().getLevel(), nbt));
                }

                if (blockActorProcessor != null) {
                    blockActorProcessor.accept(nbt);
                }
            }
        }

        if (!settings.isIgnoreEntities()) {
            placeEntities(chunk.getProvider().getLevel(), position);
        }

    }

    @Override
    public void placeInLevel(final ChunkManager level, final NukkitRandom random, final BlockVector3 position, final int integrity, final Consumer<CompoundTag> blockActorProcessor) {
        if (isInvalid()) {
            return;
        }

        final Level world = level.getChunk(position.x >> 4, position.z >> 4).getProvider().getLevel();

        for (final StructureBlockInfo blockInfo : blockInfoList) {
            final BlockEntry entry = blockInfo.state;

            if (entry.getId() == BlockID.AIR || integrity <= random.nextBoundedInt(100) && entry.getId() != BlockID.STRUCTURE_BLOCK) {
                continue;
            }

            final BlockVector3 vec = blockInfo.pos.add(position);

            if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                level.setBlockAt(vec.x, vec.y, vec.z, entry.getId(), entry.getMeta());
            }

            if (blockInfo.nbt != null) {
                final CompoundTag nbt = blockInfo.nbt.clone();

                nbt.putInt("x", vec.x);
                nbt.putInt("y", vec.y);
                nbt.putInt("z", vec.z);

                if (entry.getId() != BlockID.STRUCTURE_BLOCK) {
                    Server.getInstance().getScheduler().scheduleTask(new BlockActorSpawnTask(world, nbt));
                }

                if (blockActorProcessor != null) {
                    blockActorProcessor.accept(nbt);
                }
            }
        }

        placeEntities(world, position);
    }

    protected void placeEntities(final Level level, final BlockVector3 position) {
        for (final StructureEntityInfo entityInfo : entityInfoList) {
            final CompoundTag nbt = entityInfo.nbt.clone();

            final Vector3 pos = entityInfo.pos.add(position.x, position.y, position.z);

            final ListTag<DoubleTag> posTag = new ListTag<>("Pos");
            posTag.add(new DoubleTag("", pos.x));
            posTag.add(new DoubleTag("", pos.y));
            posTag.add(new DoubleTag("", pos.z));
            nbt.putList(posTag);

            Server.getInstance().getScheduler().scheduleTask(new ActorSpawnTask(level, nbt));
        }
    }
}
