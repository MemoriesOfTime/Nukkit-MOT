package cn.nukkit.block;

import java.util.BitSet;

import static cn.nukkit.block.Block.MAX_BLOCK_ID;

/**
 * Check the type of block by known IDs groups
 * To avoid getting a block object when an id is present for checking the type
 */
public final class BlockTypes {
    private static final BitSet SLABS = new BitSet(MAX_BLOCK_ID);
    private static final BitSet STAIRS = new BitSet(MAX_BLOCK_ID);
    private static final BitSet PRESSURE_PLATES = new BitSet(MAX_BLOCK_ID);
    private static final BitSet BUTTONS = new BitSet(MAX_BLOCK_ID);
    private static final BitSet FENCES = new BitSet(MAX_BLOCK_ID);
    private static final BitSet FENCE_GATES = new BitSet(MAX_BLOCK_ID);
    private static final BitSet TRAPDOORS = new BitSet(MAX_BLOCK_ID);
    private static final BitSet DOORS = new BitSet(MAX_BLOCK_ID);

    private static final short[] SLAB_IDS = {
            BlockID.SLAB,
            BlockID.WOODEN_SLAB,
            BlockID.RED_SANDSTONE_SLAB,
            BlockID.STONE_SLAB3,
            BlockID.STONE_SLAB4,
            BlockID.SMOOTH_STONE,
            BlockID.CRIMSON_SLAB,
            BlockID.WARPED_SLAB,
            BlockID.BLACKSTONE_SLAB,
            BlockID.POLISHED_BLACKSTONE_BRICK_SLAB,
            BlockID.POLISHED_BLACKSTONE_SLAB,
            BlockID.CUT_COPPER_SLAB,
            BlockID.EXPOSED_CUT_COPPER_SLAB,
            BlockID.WEATHERED_CUT_COPPER_SLAB,
            BlockID.OXIDIZED_CUT_COPPER_SLAB,
            BlockID.WAXED_CUT_COPPER_SLAB,
            BlockID.WAXED_EXPOSED_CUT_COPPER_SLAB,
            BlockID.WAXED_WEATHERED_CUT_COPPER_SLAB,
            BlockID.COBBLED_DEEPSLATE_SLAB,
            BlockID.POLISHED_DEEPSLATE_SLAB,
            BlockID.DEEPSLATE_TILE_SLAB,
            BlockID.DEEPSLATE_BRICK_SLAB,
            BlockID.MUD_BRICK_SLAB,
            BlockID.MANGROVE_SLAB,
            BlockID.BAMBOO_SLAB,
            BlockID.BAMBOO_MOSAIC_SLAB,
            BlockID.CHERRY_SLAB,
            BlockID.TUFF_SLAB,
            BlockID.POLISHED_TUFF_SLAB,
            BlockID.TUFF_BRICK_SLAB,
            BlockID.PALE_OAK_SLAB,
            BlockID.RESIN_BRICK_SLAB
    };

    private static final short[] STAIRS_IDS = {
            BlockID.WOOD_STAIRS,
            BlockID.COBBLE_STAIRS,
            BlockID.BRICK_STAIRS,
            BlockID.STONE_BRICK_STAIRS,
            BlockID.NETHER_BRICKS_STAIRS,
            BlockID.SANDSTONE_STAIRS,
            BlockID.SPRUCE_WOOD_STAIRS,
            BlockID.BIRCH_WOOD_STAIRS,
            BlockID.JUNGLE_WOOD_STAIRS,
            BlockID.ACACIA_WOOD_STAIRS,
            BlockID.DARK_OAK_WOOD_STAIRS,
            BlockID.QUARTZ_STAIRS,
            BlockID.RED_SANDSTONE_STAIRS,
            BlockID.PURPUR_STAIRS,
            BlockID.PRISMARINE_STAIRS,
            BlockID.DARK_PRISMARINE_STAIRS,
            BlockID.PRISMARINE_BRICKS_STAIRS,
            BlockID.GRANITE_STAIRS,
            BlockID.DIORITE_STAIRS,
            BlockID.ANDESITE_STAIRS,
            BlockID.POLISHED_DIORITE_STAIRS,
            BlockID.POLISHED_ANDESITE_STAIRS,
            BlockID.MOSSY_STONE_BRICK_STAIRS,
            BlockID.SMOOTH_RED_SANDSTONE_STAIRS,
            BlockID.SMOOTH_SANDSTONE_STAIRS,
            BlockID.END_BRICK_STAIRS,
            BlockID.MOSSY_COBBLESTONE_STAIRS,
            BlockID.NORMAL_STONE_STAIRS,
            BlockID.RED_NETHER_BRICK_STAIRS,
            BlockID.SMOOTH_QUARTZ_STAIRS,
            BlockID.CRIMSON_STAIRS,
            BlockID.WARPED_STAIRS,
            BlockID.BLACKSTONE_STAIRS,
            BlockID.POLISHED_BLACKSTONE_BRICK_STAIRS,
            BlockID.POLISHED_BLACKSTONE_STAIRS,
            BlockID.CUT_COPPER_STAIRS,
            BlockID.EXPOSED_CUT_COPPER_STAIRS,
            BlockID.WEATHERED_CUT_COPPER_STAIRS,
            BlockID.OXIDIZED_CUT_COPPER_STAIRS,
            BlockID.WAXED_CUT_COPPER_STAIRS,
            BlockID.WAXED_EXPOSED_CUT_COPPER_STAIRS,
            BlockID.WAXED_WEATHERED_CUT_COPPER_STAIRS,
            BlockID.COBBLED_DEEPSLATE_STAIRS,
            BlockID.POLISHED_DEEPSLATE_STAIRS,
            BlockID.DEEPSLATE_TILE_STAIRS,
            BlockID.DEEPSLATE_BRICK_STAIRS,
            BlockID.MUD_BRICK_STAIRS,
            BlockID.MANGROVE_STAIRS,
            BlockID.BAMBOO_STAIRS,
            BlockID.BAMBOO_MOSAIC_STAIRS,
            BlockID.CHERRY_STAIRS,
            BlockID.TUFF_STAIRS,
            BlockID.POLISHED_TUFF_STAIRS,
            BlockID.TUFF_BRICK_STAIRS,
            BlockID.PALE_OAK_STAIRS,
            BlockID.RESIN_BRICK_STAIRS
    };

    private static final short[] PRESSURE_PLATE_IDS = {
            BlockID.STONE_PRESSURE_PLATE,
            BlockID.WOODEN_PRESSURE_PLATE,
            BlockID.LIGHT_WEIGHTED_PRESSURE_PLATE,
            BlockID.HEAVY_WEIGHTED_PRESSURE_PLATE,
            BlockID.ACACIA_PRESSURE_PLATE,
            BlockID.BIRCH_PRESSURE_PLATE,
            BlockID.DARK_OAK_PRESSURE_PLATE,
            BlockID.JUNGLE_PRESSURE_PLATE,
            BlockID.SPRUCE_PRESSURE_PLATE,
            BlockID.CRIMSON_PRESSURE_PLATE,
            BlockID.WARPED_PRESSURE_PLATE,
            BlockID.MANGROVE_PRESSURE_PLATE,
            BlockID.BAMBOO_PRESSURE_PLATE,
            BlockID.CHERRY_PRESSURE_PLATE,
            BlockID.PALE_OAK_PRESSURE_PLATE,
            BlockID.POLISHED_BLACKSTONE_PRESSURE_PLATE
    };

    private static final short[] BUTTON_IDS = {
            BlockID.STONE_BUTTON,
            BlockID.WOODEN_BUTTON,
            BlockID.ACACIA_BUTTON,
            BlockID.BIRCH_BUTTON,
            BlockID.DARK_OAK_BUTTON,
            BlockID.JUNGLE_BUTTON,
            BlockID.SPRUCE_BUTTON,
            BlockID.CRIMSON_BUTTON,
            BlockID.WARPED_BUTTON,
            BlockID.MANGROVE_BUTTON,
            BlockID.BAMBOO_BUTTON,
            BlockID.CHERRY_BUTTON,
            BlockID.PALE_OAK_BUTTON,
            BlockID.POLISHED_BLACKSTONE_BUTTON
    };

    private static final short[] FENCE_IDS = {
            BlockID.FENCE,
            BlockID.NETHER_BRICK_FENCE,
            BlockID.MANGROVE_FENCE,
            BlockID.BAMBOO_FENCE,
            BlockID.CHERRY_FENCE,
            BlockID.PALE_OAK_FENCE,
            BlockID.CRIMSON_FENCE,
            BlockID.WARPED_FENCE
    };

    private static final short[] FENCE_GATE_IDS = {
            BlockID.FENCE_GATE,
            BlockID.FENCE_GATE_OAK,
            BlockID.FENCE_GATE_SPRUCE,
            BlockID.FENCE_GATE_BIRCH,
            BlockID.FENCE_GATE_JUNGLE,
            BlockID.FENCE_GATE_DARK_OAK,
            BlockID.FENCE_GATE_ACACIA,
            BlockID.CRIMSON_FENCE_GATE,
            BlockID.WARPED_FENCE_GATE,
            BlockID.MANGROVE_FENCE_GATE,
            BlockID.BAMBOO_FENCE_GATE,
            BlockID.CHERRY_FENCE_GATE,
            BlockID.PALE_OAK_FENCE_GATE
    };

    private static final short[] TRAPDOOR_IDS = {
            BlockID.TRAPDOOR,
            BlockID.IRON_TRAPDOOR,
            BlockID.ACACIA_TRAPDOOR,
            BlockID.BIRCH_TRAPDOOR,
            BlockID.DARK_OAK_TRAPDOOR,
            BlockID.JUNGLE_TRAPDOOR,
            BlockID.SPRUCE_TRAPDOOR,
            BlockID.CRIMSON_TRAPDOOR,
            BlockID.WARPED_TRAPDOOR,
            BlockID.MANGROVE_TRAPDOOR,
            BlockID.BAMBOO_TRAPDOOR,
            BlockID.CHERRY_TRAPDOOR,
            BlockID.PALE_OAK_TRAPDOOR,
            BlockID.COPPER_TRAPDOOR,
            BlockID.EXPOSED_COPPER_TRAPDOOR,
            BlockID.WEATHERED_COPPER_TRAPDOOR,
            BlockID.OXIDIZED_COPPER_TRAPDOOR,
            BlockID.WAXED_COPPER_TRAPDOOR,
            BlockID.WAXED_EXPOSED_COPPER_TRAPDOOR,
            BlockID.WAXED_WEATHERED_COPPER_TRAPDOOR,
            BlockID.WAXED_OXIDIZED_COPPER_TRAPDOOR
    };

    private static final short[] DOOR_IDS = {
            BlockID.DOOR_BLOCK,
            BlockID.WOODEN_DOOR_BLOCK,
            BlockID.WOOD_DOOR_BLOCK,
            BlockID.IRON_DOOR_BLOCK,
            BlockID.SPRUCE_DOOR_BLOCK,
            BlockID.BIRCH_DOOR_BLOCK,
            BlockID.JUNGLE_DOOR_BLOCK,
            BlockID.ACACIA_DOOR_BLOCK,
            BlockID.DARK_OAK_DOOR_BLOCK,
            BlockID.CRIMSON_DOOR_BLOCK,
            BlockID.WARPED_DOOR_BLOCK,
            BlockID.MANGROVE_DOOR_BLOCK,
            BlockID.BAMBOO_DOOR,
            BlockID.CHERRY_DOOR_BLOCK,
            BlockID.PALE_OAK_DOOR,
            BlockID.COPPER_DOOR,
            BlockID.EXPOSED_COPPER_DOOR,
            BlockID.WEATHERED_COPPER_DOOR,
            BlockID.OXIDIZED_COPPER_DOOR,
            BlockID.WAXED_COPPER_DOOR,
            BlockID.WAXED_EXPOSED_COPPER_DOOR,
            BlockID.WAXED_WEATHERED_COPPER_DOOR,
            BlockID.WAXED_OXIDIZED_COPPER_DOOR
    };

    static {
        initBitSet(SLABS, SLAB_IDS);
        initBitSet(STAIRS, STAIRS_IDS);
        initBitSet(PRESSURE_PLATES, PRESSURE_PLATE_IDS);
        initBitSet(BUTTONS, BUTTON_IDS);
        initBitSet(FENCES, FENCE_IDS);
        initBitSet(FENCE_GATES, FENCE_GATE_IDS);
        initBitSet(TRAPDOORS, TRAPDOOR_IDS);
        initBitSet(DOORS, DOOR_IDS);
    }

    private static void initBitSet(BitSet bitSet, short[] ids) {
        for (short id : ids) {
            bitSet.set(id);
        }
    }

    public static boolean isWater(int id) {
        return id == BlockID.WATER || id == BlockID.STILL_WATER;
    }

    public static boolean isLava(int id) {
        return id == BlockID.LAVA || id == BlockID.STILL_LAVA;
    }

    public static boolean isSlab(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && SLABS.get(id);
    }

    public static boolean isStairs(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && STAIRS.get(id);
    }

    public static boolean isPressurePlate(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && PRESSURE_PLATES.get(id);
    }

    public static boolean isButton(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && BUTTONS.get(id);
    }

    public static boolean isFence(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && FENCES.get(id);
    }

    public static boolean isFenceGate(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && FENCE_GATES.get(id);
    }

    public static boolean isTrapdoor(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && TRAPDOORS.get(id);
    }

    public static boolean isDoor(int id) {
        return id >= 0 && id < MAX_BLOCK_ID && DOORS.get(id);
    }
}