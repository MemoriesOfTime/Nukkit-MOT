package cn.nukkit.level.generator.populator.overworld;

import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.type.Populator;
import cn.nukkit.math.NukkitRandom;

public class PopulatorSnowLayers extends Populator {

    protected static final boolean[] coverableBiome = new boolean[256];
    protected static final boolean[] uncoverableBlock = new boolean[256];

    static {
        coverableBiome[EnumBiome.ICE_PLAINS.id] = true;
        coverableBiome[EnumBiome.ICE_PLAINS_SPIKES.id] = true;
        coverableBiome[EnumBiome.COLD_BEACH.id] = true;
        coverableBiome[EnumBiome.COLD_TAIGA.id] = true;
        coverableBiome[EnumBiome.COLD_TAIGA_HILLS.id] = true;
        coverableBiome[EnumBiome.COLD_TAIGA_M.id] = true;

        uncoverableBlock[SAPLING] = true;
        uncoverableBlock[WATER] = true;
        uncoverableBlock[STILL_WATER] = true;
        uncoverableBlock[LAVA] = true;
        uncoverableBlock[STILL_LAVA] = true;
        uncoverableBlock[BED_BLOCK] = true;
        uncoverableBlock[POWERED_RAIL] = true;
        uncoverableBlock[DETECTOR_RAIL] = true;
        uncoverableBlock[COBWEB] = true;
        uncoverableBlock[TALL_GRASS] = true;
        uncoverableBlock[DEAD_BUSH] = true;
        uncoverableBlock[PISTON_HEAD] = true;
        uncoverableBlock[DANDELION] = true;
        uncoverableBlock[RED_FLOWER] = true;
        uncoverableBlock[BROWN_MUSHROOM] = true;
        uncoverableBlock[RED_MUSHROOM] = true;
        uncoverableBlock[STONE_SLAB] = true;
        uncoverableBlock[TORCH] = true;
        uncoverableBlock[FIRE] = true;
        uncoverableBlock[OAK_WOOD_STAIRS] = true;
        uncoverableBlock[CHEST] = true;
        uncoverableBlock[REDSTONE_WIRE] = true;
        uncoverableBlock[WHEAT_BLOCK] = true;
        uncoverableBlock[SIGN_POST] = true;
        uncoverableBlock[WOODEN_DOOR_BLOCK] = true;
        uncoverableBlock[LADDER] = true;
        uncoverableBlock[RAIL] = true;
        uncoverableBlock[COBBLESTONE_STAIRS] = true;
        uncoverableBlock[WALL_SIGN] = true;
        uncoverableBlock[LEVER] = true;
        uncoverableBlock[STONE_PRESSURE_PLATE] = true;
        uncoverableBlock[IRON_DOOR_BLOCK] = true;
        uncoverableBlock[WOODEN_PRESSURE_PLATE] = true;
        uncoverableBlock[REDSTONE_ORE] = true;
        uncoverableBlock[UNLIT_REDSTONE_TORCH] = true;
        uncoverableBlock[REDSTONE_TORCH] = true;
        uncoverableBlock[STONE_BUTTON] = true;
        uncoverableBlock[SNOW_LAYER] = true; //existed
        uncoverableBlock[ICE] = true;
        uncoverableBlock[CACTUS] = true;
        uncoverableBlock[REEDS] = true;
        uncoverableBlock[FENCE] = true;
        uncoverableBlock[NETHER_PORTAL] = true;
        uncoverableBlock[CAKE_BLOCK] = true;
        uncoverableBlock[UNPOWERED_REPEATER] = true;
        uncoverableBlock[POWERED_REPEATER] = true;
        uncoverableBlock[TRAPDOOR] = true;
        uncoverableBlock[IRON_BARS] = true;
        uncoverableBlock[GLASS_PANE] = true;
        uncoverableBlock[PUMPKIN_STEM] = true;
        uncoverableBlock[MELON_STEM] = true;
        uncoverableBlock[VINE] = true;
        uncoverableBlock[FENCE_GATE] = true;
        uncoverableBlock[BRICK_STAIRS] = true;
        uncoverableBlock[STONE_BRICK_STAIRS] = true;
        uncoverableBlock[WATER_LILY] = true;
        uncoverableBlock[NETHER_BRICK_FENCE] = true;
        uncoverableBlock[NETHER_BRICKS_STAIRS] = true;
        uncoverableBlock[NETHER_WART_BLOCK] = true;
        uncoverableBlock[ENCHANTING_TABLE] = true;
        uncoverableBlock[BREWING_STAND_BLOCK] = true;
        uncoverableBlock[END_PORTAL] = true;
        uncoverableBlock[END_PORTAL_FRAME] = true;
        uncoverableBlock[DRAGON_EGG] = true;
        uncoverableBlock[ACTIVATOR_RAIL] = true;
        uncoverableBlock[COCOA] = true;
        uncoverableBlock[SANDSTONE_STAIRS] = true;
        uncoverableBlock[ENDER_CHEST] = true;
        uncoverableBlock[TRIPWIRE_HOOK] = true;
        uncoverableBlock[TRIPWIRE] = true;
        uncoverableBlock[SPRUCE_WOOD_STAIRS] = true;
        uncoverableBlock[BIRCH_WOOD_STAIRS] = true;
        uncoverableBlock[JUNGLE_WOOD_STAIRS] = true;
        uncoverableBlock[COBBLESTONE_WALL] = true;
        uncoverableBlock[FLOWER_POT_BLOCK] = true;
        uncoverableBlock[CARROT_BLOCK] = true;
        uncoverableBlock[POTATO_BLOCK] = true;
        uncoverableBlock[WOODEN_BUTTON] = true;
        uncoverableBlock[SKULL_BLOCK] = true;
        uncoverableBlock[ANVIL] = true;
        uncoverableBlock[TRAPPED_CHEST] = true;
        uncoverableBlock[LIGHT_WEIGHTED_PRESSURE_PLATE] = true;
        uncoverableBlock[HEAVY_WEIGHTED_PRESSURE_PLATE] = true;
        uncoverableBlock[UNPOWERED_COMPARATOR] = true;
        uncoverableBlock[POWERED_COMPARATOR] = true;
        uncoverableBlock[DAYLIGHT_DETECTOR] = true;
        uncoverableBlock[QUARTZ_STAIRS] = true;
        uncoverableBlock[WOODEN_SLABS] = true;
        uncoverableBlock[STAINED_GLASS_PANE] = true;
        uncoverableBlock[ACACIA_WOOD_STAIRS] = true;
        uncoverableBlock[DARK_OAK_WOOD_STAIRS] = true;
        uncoverableBlock[IRON_TRAPDOOR] = true;
        uncoverableBlock[CARPET] = true;
        uncoverableBlock[DOUBLE_PLANT] = true;
        uncoverableBlock[STANDING_BANNER] = true;
        uncoverableBlock[WALL_BANNER] = true;
        uncoverableBlock[DAYLIGHT_DETECTOR_INVERTED] = true;
        uncoverableBlock[RED_SANDSTONE_STAIRS] = true;
        uncoverableBlock[RED_SANDSTONE_SLAB] = true;
        uncoverableBlock[FENCE_GATE_SPRUCE] = true;
        uncoverableBlock[FENCE_GATE_BIRCH] = true;
        uncoverableBlock[FENCE_GATE_JUNGLE] = true;
        uncoverableBlock[FENCE_GATE_DARK_OAK] = true;
        uncoverableBlock[FENCE_GATE_ACACIA] = true;
        uncoverableBlock[190] = true; // HARD_GLASS_PANE
        uncoverableBlock[191] = true; // HARD_STAINED_GLASS_PANE
        uncoverableBlock[SPRUCE_DOOR_BLOCK] = true;
        uncoverableBlock[BIRCH_DOOR_BLOCK] = true;
        uncoverableBlock[JUNGLE_DOOR_BLOCK] = true;
        uncoverableBlock[ACACIA_DOOR_BLOCK] = true;
        uncoverableBlock[DARK_OAK_DOOR_BLOCK] = true;
        uncoverableBlock[ITEM_FRAME_BLOCK] = true;
        uncoverableBlock[CHORUS_FLOWER] = true;
        uncoverableBlock[202] = true; // COLORED_TORCH_RG
        uncoverableBlock[PURPUR_STAIRS] = true;
        uncoverableBlock[204] = true; // COLORED_TORCH_BP
        uncoverableBlock[UNDYED_SHULKER_BOX] = true;
        uncoverableBlock[ICE_FROSTED] = true;
        uncoverableBlock[END_ROD] = true;
        uncoverableBlock[END_GATEWAY] = true;
        uncoverableBlock[217] = true; // STRUCTURE_VOID
        uncoverableBlock[SHULKER_BOX] = true;
        uncoverableBlock[239] = true; // UNDERWATER_TORCH
        uncoverableBlock[CHORUS_PLANT] = true;
        uncoverableBlock[242] = true; // CAMERA
        uncoverableBlock[BEETROOT_BLOCK] = true;
        uncoverableBlock[PISTON_EXTENSION] = true;
    }

    @Override
    public void populate(ChunkManager level, int chunkX, int chunkZ, NukkitRandom random, FullChunk chunk) {
        for (int x = 0; x < 16; x++) {
            for (int z = 0; z < 16; z++) {
                if (coverableBiome[chunk.getBiomeId(x, z)]) {
                    int y = chunk.getHighestBlockAt(x, z);
                    if (y > 0 && y < 255 && !uncoverableBlock[chunk.getBlockId(x, y, z)]) {
                        int chance = random.nextBoundedInt(10);
                        chunk.setBlock(x, y + 1, z, SNOW_LAYER, chance < 6 ? 0 : chance == 6 ? 2 : 1);
                    }
                }
            }
        }
    }
}
