package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.Server;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.Level;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.loot.ShipwreckMapChest;
import cn.nukkit.level.generator.loot.ShipwreckSupplyChest;
import cn.nukkit.level.generator.loot.ShipwreckTreasureChest;
import cn.nukkit.level.generator.populator.CallbackableTemplateStructurePopulator;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.level.generator.task.CallbackableChunkGenerationTask;
import cn.nukkit.level.generator.task.LootSpawnTask;
import cn.nukkit.level.generator.template.ReadOnlyLegacyStructureTemplate;
import cn.nukkit.level.generator.template.ReadableStructureTemplate;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;

import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;

public class PopulatorShipwreck extends Populator implements CallbackableTemplateStructurePopulator {
    public static final boolean[] FILTER = new boolean[512];
    protected static final ReadableStructureTemplate WITH_MAST = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swwithmast.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_FULL = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownfull.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_FRONTHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownfronthalf.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_BACKHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownbackhalf.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_FULL = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysfull.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_FRONTHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysfronthalf.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_BACKHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysbackhalf.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_FULL = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupfull.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_FRONTHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupfronthalf.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_BACKHALF = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupbackhalf.nbt"));
    protected static final ReadableStructureTemplate WITH_MAST_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swwithmastdegraded.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_FULL_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownfulldegraded.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_FRONTHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownfronthalfdegraded.nbt"));
    protected static final ReadableStructureTemplate UPSIDEDOWN_BACKHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swupsidedownbackhalfdegraded.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_FULL_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysfulldegraded.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_FRONTHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysfronthalfdegraded.nbt"));
    protected static final ReadableStructureTemplate SIDEWAYS_BACKHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swsidewaysbackhalfdegraded.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_FULL_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupfulldegraded.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_FRONTHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupfronthalfdegraded.nbt"));
    protected static final ReadableStructureTemplate RIGHTSIDEUP_BACKHALF_DEGRADED = new ReadOnlyLegacyStructureTemplate().load(Generator.loadNBT("structures/shipwreck/swrightsideupbackhalfdegraded.nbt"));
    protected static final ReadableStructureTemplate[] STRUCTURE_LOCATION_BEACHED = new ReadableStructureTemplate[]{
        WITH_MAST,
        SIDEWAYS_FULL,
        SIDEWAYS_FRONTHALF,
        SIDEWAYS_BACKHALF,
        RIGHTSIDEUP_FULL,
        RIGHTSIDEUP_FRONTHALF,
        RIGHTSIDEUP_BACKHALF,
        WITH_MAST_DEGRADED,
        RIGHTSIDEUP_FULL_DEGRADED,
        RIGHTSIDEUP_FRONTHALF_DEGRADED,
        RIGHTSIDEUP_BACKHALF_DEGRADED
    };
    protected static final ReadableStructureTemplate[] STRUCTURE_LOCATION_OCEAN = new ReadableStructureTemplate[]{
        WITH_MAST,
        UPSIDEDOWN_FULL,
        UPSIDEDOWN_FRONTHALF,
        UPSIDEDOWN_BACKHALF,
        SIDEWAYS_FULL,
        SIDEWAYS_FRONTHALF,
        SIDEWAYS_BACKHALF,
        RIGHTSIDEUP_FULL,
        RIGHTSIDEUP_FRONTHALF,
        RIGHTSIDEUP_BACKHALF,
        WITH_MAST_DEGRADED,
        UPSIDEDOWN_FULL_DEGRADED,
        UPSIDEDOWN_FRONTHALF_DEGRADED,
        UPSIDEDOWN_BACKHALF_DEGRADED,
        SIDEWAYS_FULL_DEGRADED,
        SIDEWAYS_FRONTHALF_DEGRADED,
        SIDEWAYS_BACKHALF_DEGRADED,
        RIGHTSIDEUP_FULL_DEGRADED,
        RIGHTSIDEUP_FRONTHALF_DEGRADED,
        RIGHTSIDEUP_BACKHALF_DEGRADED
    };
    protected static final int SPACING = 24;
    protected static final int SEPARATION = 4;

    static {
        FILTER[AIR] = true;
        FILTER[LOG] = true;
        FILTER[WATER] = true;
        FILTER[STILL_WATER] = true;
        FILTER[LAVA] = true;
        FILTER[STILL_LAVA] = true;
        FILTER[LEAVES] = true;
        FILTER[TALL_GRASS] = true;
        FILTER[DEAD_BUSH] = true;
        FILTER[DANDELION] = true;
        FILTER[RED_FLOWER] = true;
        FILTER[BROWN_MUSHROOM] = true;
        FILTER[RED_MUSHROOM] = true;
        FILTER[SNOW_LAYER] = true;
        FILTER[ICE] = true;
        FILTER[CACTUS] = true;
        FILTER[SUGARCANE_BLOCK] = true;
        FILTER[PUMPKIN] = true;
        FILTER[BROWN_MUSHROOM_BLOCK] = true;
        FILTER[RED_MUSHROOM_BLOCK] = true;
        FILTER[MELON_BLOCK] = true;
        FILTER[VINE] = true;
        FILTER[WATER_LILY] = true;
        FILTER[COCOA] = true;
        FILTER[LEAVES2] = true;
        FILTER[LOG2] = true;
        FILTER[PACKED_ICE] = true;
        FILTER[DOUBLE_PLANT] = true;
    }

    protected final Map<Long, Set<Long>> waitingChunks = Maps.newConcurrentMap();

    protected static Consumer<CompoundTag> getBlockActorProcessor(final FullChunk chunk, final NukkitRandom random) {
        return nbt -> {
            if (nbt.getString("id").equals("StructureBlock")) {
                switch (nbt.getString("metadata")) {
                    case "supplyChest":
                        ListTag<CompoundTag> itemList = new ListTag<>("Items");
                        ShipwreckSupplyChest.get().create(itemList, random);

                        Server.getInstance().getScheduler().scheduleDelayedTask(new LootSpawnTask(chunk.getProvider().getLevel(),
                            new BlockVector3(nbt.getInt("x"), nbt.getInt("y") - 1, nbt.getInt("z")), itemList), 2);
                        break;
                    case "mapChest":
                        itemList = new ListTag<>("Items");
                        ShipwreckMapChest.get().create(itemList, random);

                        Server.getInstance().getScheduler().scheduleDelayedTask(new LootSpawnTask(chunk.getProvider().getLevel(),
                            new BlockVector3(nbt.getInt("x"), nbt.getInt("y") - 1, nbt.getInt("z")), itemList), 2);
                        break;
                    case "treasureChest":
                        itemList = new ListTag<>("Items");
                        ShipwreckTreasureChest.get().create(itemList, random);

                        Server.getInstance().getScheduler().scheduleDelayedTask(new LootSpawnTask(chunk.getProvider().getLevel(),
                            new BlockVector3(nbt.getInt("x"), nbt.getInt("y") - 1, nbt.getInt("z")), itemList), 2);
                        break;
                }
            }
        };
    }

    @Override
    public void populate(final ChunkManager level, final int chunkX, final int chunkZ, final NukkitRandom random, final FullChunk chunk) {
        final int biome = chunk.getBiomeId(5, 5);
        if ((biome == EnumBiome.BEACH.id || biome >= 44 && biome <= 50)
            && chunkX == (chunkX < 0 ? chunkX - SPACING + 1 : chunkX) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)
            && chunkZ == (chunkZ < 0 ? chunkZ - SPACING + 1 : chunkZ) / SPACING * SPACING + random.nextBoundedInt(SPACING - SEPARATION)
        ) {
            final ReadableStructureTemplate template;

            if (biome == EnumBiome.BEACH.id) {
                template = STRUCTURE_LOCATION_BEACHED[random.nextBoundedInt(STRUCTURE_LOCATION_BEACHED.length)];
            } else {
                template = STRUCTURE_LOCATION_OCEAN[random.nextBoundedInt(STRUCTURE_LOCATION_OCEAN.length)];
            }

            final BlockVector3 size = template.getSize();
            int sumY = 0;
            int blockCount = 0;

            for (int x = 0; x < size.x && x < 16; x++) {
                for (int z = 0; z < size.z && z < 16; z++) {
                    int y = chunk.getHighestBlockAt(x, z);

                    int id = chunk.getBlockId(x, y, z);
                    while (FILTER[id] && y > 0) {
                        id = chunk.getBlockId(x, --y, z);
                    }

                    sumY += y;
                    blockCount++;
                }
            }

            final int y = sumY / blockCount;

            final int seed = random.nextInt();
            boolean isLarge = false;

            final Set<BaseFullChunk> chunks = Sets.newHashSet();
            final Set<Long> indexes = Sets.newConcurrentHashSet();

            if (size.x > 16) {
                isLarge = true;

                final BaseFullChunk ck = level.getChunk(chunkX + 1, chunkZ);
                if (!ck.isGenerated()) {
                    chunks.add(ck);
                    indexes.add(Level.chunkHash(ck.getX(), chunkZ));
                }
            }
            if (size.z > 16) {
                isLarge = true;

                final BaseFullChunk ck = level.getChunk(chunkX, chunkZ + 1);
                if (!ck.isGenerated()) {
                    chunks.add(ck);
                    indexes.add(Level.chunkHash(chunkX, ck.getZ()));
                }
            }

            if (!chunks.isEmpty()) {
                waitingChunks.put(Level.chunkHash(chunkX, chunkZ), indexes);
                for (final BaseFullChunk ck : chunks) {
                    Server.getInstance().getScheduler().scheduleAsyncTask(new CallbackableChunkGenerationTask<>(
                        chunk.getProvider().getLevel(), ck, this,
                        populator -> populator.generateChunkCallback(template, seed, level, chunkX, chunkZ, y, ck.getX(), ck.getZ())));
                }
                return;
            }

            if (isLarge) {
                placeInLevel(level, chunkX, chunkZ, template, seed, y);
            } else {
                random.setSeed(seed);

                final BlockVector3 vec = new BlockVector3(chunkX << 4, y, chunkZ << 4);
                template.placeInChunk(chunk, random, vec, 100, getBlockActorProcessor(chunk, random));
            }
        }
    }

    protected void placeInLevel(final ChunkManager level, final int chunkX, final int chunkZ, final ReadableStructureTemplate template, final int seed, final int y) {
        final NukkitRandom random = new NukkitRandom(seed);

        final BlockVector3 vec = new BlockVector3(chunkX << 4, y, chunkZ << 4);
        template.placeInLevel(level, random, vec, 100, getBlockActorProcessor(level.getChunk(chunkX, chunkZ), random));

        waitingChunks.remove(Level.chunkHash(chunkX, chunkZ));
    }

    @Override
    public void generateChunkCallback(final ReadableStructureTemplate template, final int seed, final ChunkManager level, final int startChunkX, final int startChunkZ, final int y, final int chunkX, final int chunkZ) {
        final Set<Long> indexes = waitingChunks.get(Level.chunkHash(startChunkX, startChunkZ));
        indexes.remove(Level.chunkHash(chunkX, chunkZ));
        if (indexes.isEmpty()) {
            placeInLevel(level, startChunkX, startChunkZ, template, seed, y);
        }
    }
}
