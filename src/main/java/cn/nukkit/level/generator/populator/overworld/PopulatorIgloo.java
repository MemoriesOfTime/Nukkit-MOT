package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityZombieVillager;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.block.BlockTypes;
import cn.nukkit.level.generator.loot.IglooChest;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.ActorSpawnTask;
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

import java.util.function.Consumer;

public class PopulatorIgloo extends Populator {
    protected static final ReadableStructureTemplate IGLOO = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/igloo/igloo_top_no_trapdoor.nbt"));
    protected static final ReadableStructureTemplate IGLOO_WITH_TRAPDOOR = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/igloo/igloo_top_trapdoor.nbt"));

    protected static final ReadableStructureTemplate LADDER = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/igloo/igloo_middle.nbt"));
    protected static final ReadableStructureTemplate LABORATORY = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/igloo/igloo_bottom.nbt"));

    protected static final StructurePlaceSettings SETTINGS_LADDER = new StructurePlaceSettings().setIgnoreAir(true);

    protected static final int SPACING = 32;
    protected static final int SEPARATION = 8;

    protected static Consumer<CompoundTag> getBlockActorProcessor(final FullChunk chunk, final NukkitRandom random) {
        return nbt -> {
            if (nbt.getString("id").equals("StructureBlock")) {
                switch (nbt.getString("metadata")) {
                    case "chest" -> {
                        final ListTag<CompoundTag> itemList = new ListTag<>("Items");
                        IglooChest.get().create(itemList, random);
                        Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new LootSpawnTask(chunk.getProvider().getLevel(),
                                new BlockVector3(nbt.getInt("x"), nbt.getInt("y") - 1, nbt.getInt("z")), itemList), 2);
                    }
                    case "Villager" ->
                            Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new ActorSpawnTask(chunk.getProvider().getLevel(),
                                    Entity.getDefaultNBT(new Vector3(nbt.getInt("x") + 0.5, nbt.getInt("y"), nbt.getInt("z") + 0.5))
                                            .putString("id", String.valueOf(EntityVillager.NETWORK_ID))), 2);
                    case "Zombie Villager" ->
                            Server.getInstance().getScheduler().scheduleDelayedTask(InternalPlugin.INSTANCE, new ActorSpawnTask(chunk.getProvider().getLevel(),
                                    Entity.getDefaultNBT(new Vector3(nbt.getInt("x") + 0.5, nbt.getInt("y"), nbt.getInt("z") + 0.5))
                                            .putString("id", String.valueOf(EntityZombieVillager.NETWORK_ID))), 2);
                }
            }
        };
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        final int biome = chunk.getBiomeId(7, 7);
        if ((biome == EnumBiome.ICE_PLAINS.id || biome == EnumBiome.COLD_TAIGA.id || biome == EnumBiome.ICE_PLAINS_SPIKES.id)
                && chunkX == (chunkX < 0 ? chunkX - SPACING + 1 : chunkX) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)
                && chunkZ == (chunkZ < 0 ? chunkZ - SPACING + 1 : chunkZ) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)) {
            ReadableStructureTemplate template;
            final boolean hasLaboratory = random.nextBoolean();

            if (hasLaboratory) {
                template = IGLOO_WITH_TRAPDOOR;
            } else {
                template = IGLOO;
            }

            final BlockVector3 size = template.getSize();
            int sumY = 0;
            int blockCount = 0;

            for (int x = 0; x < size.x; x++) {
                for (int z = 2; z < size.z + 2; z++) {
                    int y = chunk.getHighestBlockAt(x, z);

                    int id = chunk.getBlockId(x, y, z);
                    while (BlockTypes.isPlant[id] && y > 64) {
                        id = chunk.getBlockId(x, --y, z);
                    }

                    sumY += Math.max(64, y);
                    blockCount++;
                }
            }

            final BlockVector3 vec = new BlockVector3(chunkX << 4, sumY / blockCount, (chunkZ << 4) + 2);
            template.placeInChunk(chunk, random, vec, StructurePlaceSettings.DEFAULT);

            if (hasLaboratory) {
                template = LADDER;
                final int yOffset = template.getSize().getY();
                vec.x += 2;
                vec.z += 4;

                for (int i = 0; i < random.nextBoundedInt(8) + 3; ++i) {
                    vec.y -= yOffset;

                    template.placeInChunk(chunk, random, vec, SETTINGS_LADDER);
                }

                template = LABORATORY;
                vec.x -= 2;
                vec.z -= 6;
                vec.y -= template.getSize().getY();

                template.placeInChunk(chunk, random, vec, new StructurePlaceSettings()
                        .setBlockActorProcessor(getBlockActorProcessor(chunk, random)));
            }
        }
    }
}
