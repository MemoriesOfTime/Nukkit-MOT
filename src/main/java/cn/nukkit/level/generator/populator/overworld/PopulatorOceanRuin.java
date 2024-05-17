package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.block.BlockTypes;
import cn.nukkit.level.generator.loot.RuinBigChest;
import cn.nukkit.level.generator.loot.RuinSmallChest;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.BlockActorSpawnTask;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.level.generator.template.ReadOnlyLegacyStructureTemplate;
import cn.nukkit.level.generator.template.ReadableStructureTemplate;
import cn.nukkit.level.generator.template.StructurePlaceSettings;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.InternalPlugin;
import com.google.common.collect.Lists;

import java.util.List;
import java.util.function.Consumer;

public class PopulatorOceanRuin extends Populator {
    protected static final List<ChunkPosition> ADJACENT_CHUNKS = Lists.newArrayList(
            new ChunkPosition(-1, -1),
            new ChunkPosition(-1, 0),
            new ChunkPosition(-1, 1),
            new ChunkPosition(0, -1),
            new ChunkPosition(0, 1),
            new ChunkPosition(1, -1),
            new ChunkPosition(1, 0),
            new ChunkPosition(1, 1)
    );

    protected static final int SPACING = 20;
    protected static final int SEPARATION = 8;
    protected static final ReadableStructureTemplate[] WARM_RUINS = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm1.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm2.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm3.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm4.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm5.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm6.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm7.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin_warm8.nbt"))
    };
    protected static final ReadableStructureTemplate[] RUINS_BRICK = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin1_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin2_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin3_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin4_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin5_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin6_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin7_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin8_brick.nbt"))
    };
    protected static final ReadableStructureTemplate[] RUINS_CRACKED = { //70
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin1_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin2_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin3_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin4_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin5_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin6_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin7_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin8_cracked.nbt"))
    };
    protected static final ReadableStructureTemplate[] RUINS_MOSSY = { //50
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin1_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin2_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin3_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin4_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin5_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin6_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin7_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/ruin8_mossy.nbt"))
    };
    protected static final ReadableStructureTemplate[] BIG_WARM_RUINS = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin_warm4.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin_warm5.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin_warm6.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin_warm7.nbt"))
    };
    protected static final ReadableStructureTemplate[] BIG_RUINS_BRICK = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin1_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin2_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin3_brick.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin8_brick.nbt"))
    };
    protected static final ReadableStructureTemplate[] BIG_RUINS_MOSSY = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin1_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin2_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin3_cracked.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin8_cracked.nbt"))
    };
    protected static final ReadableStructureTemplate[] BIG_RUINS_CRACKED = {
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin1_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin2_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin3_mossy.nbt")),
            new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/ruin/big_ruin8_mossy.nbt"))
    };

    protected static Consumer<CompoundTag> getSmallRuinProcessor(final FullChunk chunk, final NukkitRandom random) {
        return nbt -> {
            if (nbt.getString("id").equals("StructureBlock") && "chest".equals(nbt.getString("metadata"))) {
                final BlockVector3 pos = new BlockVector3(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
                final CompoundTag tag = BlockEntity.getDefaultCompound(pos.asVector3(), BlockEntity.CHEST);

                final ListTag<CompoundTag> items = new ListTag<>("Items");
                RuinSmallChest.get().create(items, random);
                tag.putList(items);

                chunk.setBlock(pos.x & 0xf, pos.y, pos.z & 0xf, BlockID.CHEST, 2);
                Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new BlockActorSpawnTask(chunk.getProvider().getLevel(), tag), 2);
            }
        };
    }

    protected static Consumer<CompoundTag> getBigRuinProcessor(final FullChunk chunk, final NukkitRandom random) {
        return nbt -> {
            if (nbt.getString("id").equals("StructureBlock") && "chest".equals(nbt.getString("metadata"))) {
                final BlockVector3 pos = new BlockVector3(nbt.getInt("x"), nbt.getInt("y"), nbt.getInt("z"));
                final CompoundTag tag = BlockEntity.getDefaultCompound(pos.asVector3(), BlockEntity.CHEST);

                final ListTag<CompoundTag> items = new ListTag<>("Items");
                RuinBigChest.get().create(items, random);
                tag.putList(items);

                chunk.setBlock(pos.x & 0xf, pos.y, pos.z & 0xf, BlockID.CHEST, 2);
                Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new BlockActorSpawnTask(chunk.getProvider().getLevel(), tag), 2);
            }
        };
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        final int biome = chunk.getBiomeId(7, 7);
        if (biome >= 44 && biome <= 50
                && chunkX == (chunkX < 0 ? chunkX - SPACING + 1 : chunkX) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)
                && chunkZ == (chunkZ < 0 ? chunkZ - SPACING + 1 : chunkZ) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)) {
            final boolean isWarm = random.nextBoundedInt(10) < 4;
            final boolean isLarge = random.nextBoundedInt(100) <= 30;

            final ReadableStructureTemplate template;
            final int index;

            if (isWarm) {
                index = -1;
                if (isLarge) {
                    template = BIG_WARM_RUINS[random.nextBoundedInt(BIG_WARM_RUINS.length)];
                } else {
                    template = WARM_RUINS[random.nextBoundedInt(WARM_RUINS.length)];
                }
            } else if (isLarge) {
                index = random.nextBoundedInt(BIG_RUINS_BRICK.length);
                template = BIG_RUINS_BRICK[index];
            } else {
                index = random.nextBoundedInt(RUINS_BRICK.length);
                template = RUINS_BRICK[index];
            }

            placeRuin(template, chunk, random.nextInt(), isLarge, index);

            if (isLarge && random.nextBoundedInt(100) <= 90) {
                final List<ChunkPosition> adjacentChunks = Lists.newArrayList(ADJACENT_CHUNKS);
                for (int i = 0; i < random.nextRange(4, 8); i++) {
                    final ChunkPosition chunkPos = adjacentChunks.remove(random.nextBoundedInt(adjacentChunks.size()));
                    placeAdjacentRuin(level.getChunk(chunkX + chunkPos.x, chunkZ + chunkPos.z), random, isWarm);
                }
            }
        }
    }

    protected void placeAdjacentRuin(final BaseFullChunk chunk, final NukkitRandom random, final boolean isWarm) {
        final ReadableStructureTemplate template;
        final int index;

        if (isWarm) {
            template = WARM_RUINS[random.nextBoundedInt(WARM_RUINS.length)];
            index = -1;
        } else {
            index = random.nextBoundedInt(RUINS_BRICK.length);
            template = RUINS_BRICK[random.nextBoundedInt(RUINS_BRICK.length)];
        }

        final int seed = random.nextInt();

        if (!chunk.isGenerated()) {
            Server.getInstance().getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new CallbackableChunkGenerationTask<>(
                    chunk.getProvider().getLevel(), chunk, this,
                    populator -> populator.placeRuin(template, chunk, seed, false, index)));
        } else {
            placeRuin(template, chunk, seed, false, index);
        }
    }

    protected void placeRuin(final ReadableStructureTemplate template, final FullChunk chunk, final int seed, final boolean isLarge, final int index) {
        final NukkitRandom random = new NukkitRandom(seed);

        final BlockVector3 size = template.getSize();
        final int x = random.nextBoundedInt(16 - size.x);
        final int z = random.nextBoundedInt(16 - size.z);
        int y = 256;

        for (int cx = x; cx < x + size.x; cx++) {
            for (int cz = z; cz < z + size.z; cz++) {
                int h = chunk.getHighestBlockAt(cx, cz);

                int id = chunk.getBlockId(cx, h, cz);
                while (BlockTypes.isPlantOrFluid[id] && h > 1) {
                    id = chunk.getBlockId(cx, --h, cz);
                }

                y = Math.min(h, y);
            }
        }

        final BlockVector3 vec = new BlockVector3((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z);
        template.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                .setIgnoreAir(true)
                .setIntegrity(isLarge ? 90 : 80)
                .setBlockActorProcessor(isLarge ? getBigRuinProcessor(chunk, random) : getSmallRuinProcessor(chunk, random)));

        if (index != -1) {
            final ReadableStructureTemplate mossy;
            final ReadableStructureTemplate cracked;

            if (isLarge) {
                mossy = BIG_RUINS_MOSSY[index];
                cracked = BIG_RUINS_CRACKED[index];
            } else {
                mossy = RUINS_MOSSY[index];
                cracked = RUINS_CRACKED[index];
            }

            mossy.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                    .setIgnoreAir(true)
                    .setIntegrity(70)
                    .setBlockActorProcessor(isLarge ? getBigRuinProcessor(chunk, random) : getSmallRuinProcessor(chunk, random)));
            cracked.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                    .setIgnoreAir(true)
                    .setIntegrity(50)
                    .setBlockActorProcessor(isLarge ? getBigRuinProcessor(chunk, random) : getSmallRuinProcessor(chunk, random)));
        }
    }

    public record ChunkPosition(int x, int z) {
    }
}
