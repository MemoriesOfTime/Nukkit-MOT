package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityPillager;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.block.BlockTypes;
import cn.nukkit.level.generator.loot.PillagerOutpostChest;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.ActorSpawnTask;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.level.generator.task.LootSpawnTask;
import cn.nukkit.level.generator.template.ReadOnlyLegacyStructureTemplate;
import cn.nukkit.level.generator.template.ReadableStructureTemplate;
import cn.nukkit.level.generator.template.StructurePlaceSettings;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.utils.Utils;

import java.util.function.Consumer;

public class PopulatorPillagerOutpost extends Populator {
    protected static final ReadableStructureTemplate WATCHTOWER = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/watchtower.nbt"));
    protected static final ReadableStructureTemplate WATCHTOWER_OVERGROWN = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/watchtower_overgrown.nbt"));

    protected static final ReadableStructureTemplate[] FEATURES = new ReadableStructureTemplate[]{
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_cage1.nbt")),
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_cage2.nbt")),
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_logs.nbt")),
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_tent1.nbt")),
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_tent2.nbt")),
        new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/pillageroutpost/feature_targets.nbt"))
    };

    protected static final int SPACING = 32;
    protected static final int SEPARATION = 8;

    protected static void fillBase(final FullChunk chunk, final int baseY, final int startX, final int startZ, final int sizeX, final int sizeZ) {
        for (int x = startX; x < startX + sizeX; x++) {
            for (int z = startZ; z < startZ + sizeZ; z++) {
                final int baseId = chunk.getBlockId(x, baseY, z);
                final int baseMeta = chunk.getBlockData(x, baseY, z);

                switch (baseId) {
                    case COBBLESTONE, MOSS_STONE, LOG2, PLANKS, FENCE -> {
                        int y = baseY - 1;
                        int id = chunk.getBlockId(x, y, z);
                        while (BlockTypes.isPlantOrFluid[id] && y > 1) {
                            chunk.setBlock(x, y, z, baseId, baseMeta);
                            id = chunk.getBlockId(x, --y, z);
                        }
                    }
                }
            }
        }
    }

    protected static Consumer<CompoundTag> getBlockActorProcessor(final FullChunk chunk, final NukkitRandom random) {
        return nbt -> {
            if (nbt.getString("id").equals("StructureBlock")) {
                switch (nbt.getString("metadata")) {
                    case "topChest" -> {
                        final ListTag<CompoundTag> itemList = new ListTag<>("Items");
                        PillagerOutpostChest.get().create(itemList, random);
                        Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new LootSpawnTask(chunk.getProvider().getLevel(),
                            new BlockVector3(nbt.getInt("x"), nbt.getInt("y") - 1, nbt.getInt("z")), itemList), 2);
                    }
                    case "pillager" -> Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new ActorSpawnTask(chunk.getProvider().getLevel(),
                        Entity.getDefaultNBT(new Vector3(nbt.getInt("x") + 0.5, nbt.getInt("y"), nbt.getInt("z") + 0.5))
                            .putString("id", String.valueOf(EntityPillager.NETWORK_ID))), 2);
                    case "captain" -> Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new ActorSpawnTask(chunk.getProvider().getLevel(),
                        Entity.getDefaultNBT(new Vector3(nbt.getInt("x") + 0.5, nbt.getInt("y"), nbt.getInt("z") + 0.5))
                            .putString("id", String.valueOf(EntityPillager.NETWORK_ID))
                            .putBoolean("PatrolLeader", true)), 2);
                    case "cage" -> Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new ActorSpawnTask(chunk.getProvider().getLevel(),
                        Entity.getDefaultNBT(new Vector3(nbt.getInt("x") + 0.5, nbt.getInt("y"), nbt.getInt("z") + 0.5))
                            .putString("id", String.valueOf(EntityIronGolem.NETWORK_ID))), 2);
                }
            }
        };
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        final int biome = chunk.getBiomeId(7, 7);
        if ((biome == EnumBiome.PLAINS.id || biome == EnumBiome.DESERT.id || biome == EnumBiome.BEACH.id || biome == EnumBiome.TAIGA.id || biome == EnumBiome.ICE_PLAINS.id || biome == EnumBiome.SAVANNA.id || biome == EnumBiome.TAIGA_M.id || biome == EnumBiome.FOREST.id || biome == EnumBiome.BIRCH_FOREST.id || biome == EnumBiome.EXTREME_HILLS_PLUS.id)
            && chunkX == (chunkX < 0 ? chunkX - SPACING + 1 : chunkX) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)
            && chunkZ == (chunkZ < 0 ? chunkZ - SPACING + 1 : chunkZ) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)) {
            random.setSeed((chunkX >> 4 ^ chunkZ >> 4 << 4) ^ level.getSeed());
            random.nextInt();

            if (Utils.rand(1, 4) == 3) {
                final ReadableStructureTemplate template = WATCHTOWER;
                int y = chunk.getHighestBlockAt(0, 0);

                int blockId = chunk.getBlockId(0, y, 0);
                while (BlockTypes.isPlant[blockId] && y > 1) {
                    blockId = chunk.getBlockId(0, --y, 0);
                }

                final BlockVector3 vec = new BlockVector3(chunkX << 4, y, chunkZ << 4);
                template.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                    .setBlockActorProcessor(getBlockActorProcessor(chunk, random)));
                WATCHTOWER_OVERGROWN.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                    .setIntegrity(5)
                    .setIgnoreAir(true)
                    .setBlockActorProcessor(getBlockActorProcessor(chunk, random)));

                final BlockVector3 size = template.getSize();
                fillBase(level.getChunk(chunkX, chunkZ), y, 0, 0, size.x, size.z);

                if (random.nextBoolean()) {
                    tryPlaceFeature(level.getChunk(chunkX - 1, chunkZ - 1), random);
                }
                if (random.nextBoolean()) {
                    tryPlaceFeature(level.getChunk(chunkX - 1, chunkZ + 1), random);
                }
                if (random.nextBoolean()) {
                    tryPlaceFeature(level.getChunk(chunkX + 1, chunkZ - 1), random);
                }
                if (random.nextBoolean()) {
                    tryPlaceFeature(level.getChunk(chunkX + 1, chunkZ + 1), random);
                }
            }
        }
    }

    protected void tryPlaceFeature(final BaseFullChunk chunk, final NukkitRandom random) {
        final ReadableStructureTemplate template = FEATURES[random.nextBoundedInt(FEATURES.length)];
        final int seed = random.nextInt();

        if (!chunk.isGenerated()) {
            Server.getInstance().getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new CallbackableChunkGenerationTask<>(
                chunk.getProvider().getLevel(), chunk, this,
                populator -> populator.placeFeature(template, chunk, seed)));
        } else {
            placeFeature(template, chunk, seed);
        }
    }

    protected void placeFeature(final ReadableStructureTemplate template, final FullChunk chunk, final int seed) {
        final NukkitRandom random = new NukkitRandom(seed);

        final BlockVector3 size = template.getSize();
        final int x = random.nextBoundedInt(16 - size.x);
        final int z = random.nextBoundedInt(16 - size.z);
        final int y = chunk.getHighestBlockAt(x, z);

        template.placeInChunk(chunk, random, new BlockVector3((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z), new StructurePlaceSettings()
            .setBlockActorProcessor(getBlockActorProcessor(chunk, random)));
        fillBase(chunk, y, x, z, size.x, size.z);
    }
}
