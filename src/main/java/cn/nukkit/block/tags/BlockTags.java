package cn.nukkit.block.tags;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

import java.util.*;

/**
 * This class is generated automatically, do not change it manually.
 */
public final class BlockTags {
    private static final Map<String, BlockTag> NAME_2_TAG = new HashMap<>();

    private static final Int2ObjectOpenHashMap<Set<BlockTag>> BLOCK_2_TAGS = new Int2ObjectOpenHashMap<>();

    public static final BlockTag ACACIA = register("minecraft:acacia", new LazyBlockTag("minecraft:acacia"));

    public static final BlockTag BIRCH = register("minecraft:birch", new LazyBlockTag("minecraft:birch"));

    public static final BlockTag CORNERABLE_STAIRS = register("minecraft:cornerable_stairs", new LazyBlockTag("minecraft:cornerable_stairs"));

    public static final BlockTag CROP = register("minecraft:crop", new LazyBlockTag("minecraft:crop"));

    public static final BlockTag DARK_OAK = register("minecraft:dark_oak", new LazyBlockTag("minecraft:dark_oak"));

    public static final BlockTag DIAMOND_PICK_DIGGABLE = register("minecraft:diamond_pick_diggable", new LazyBlockTag("minecraft:diamond_pick_diggable"));

    public static final BlockTag DIAMOND_TIER_DESTRUCTIBLE = register("minecraft:diamond_tier_destructible", new LazyBlockTag("minecraft:diamond_tier_destructible"));

    public static final BlockTag DIRT = register("minecraft:dirt", new LazyBlockTag("minecraft:dirt"));

    public static final BlockTag FERTILIZE_AREA = register("minecraft:fertilize_area", new LazyBlockTag("minecraft:fertilize_area"));

    public static final BlockTag GRASS = register("minecraft:grass", new LazyBlockTag("minecraft:grass"));

    public static final BlockTag GRAVEL = register("minecraft:gravel", new LazyBlockTag("minecraft:gravel"));

    public static final BlockTag IRON_PICK_DIGGABLE = register("minecraft:iron_pick_diggable", new LazyBlockTag("minecraft:iron_pick_diggable"));

    public static final BlockTag IRON_TIER_DESTRUCTIBLE = register("minecraft:iron_tier_destructible", new LazyBlockTag("minecraft:iron_tier_destructible"));

    public static final BlockTag IS_AXE_ITEM_DESTRUCTIBLE = register("minecraft:is_axe_item_destructible", new LazyBlockTag("minecraft:is_axe_item_destructible"));

    public static final BlockTag IS_HOE_ITEM_DESTRUCTIBLE = register("minecraft:is_hoe_item_destructible", new LazyBlockTag("minecraft:is_hoe_item_destructible"));

    public static final BlockTag IS_PICKAXE_ITEM_DESTRUCTIBLE = register("minecraft:is_pickaxe_item_destructible", new LazyBlockTag("minecraft:is_pickaxe_item_destructible"));

    public static final BlockTag IS_SHEARS_ITEM_DESTRUCTIBLE = register("minecraft:is_shears_item_destructible", new LazyBlockTag("minecraft:is_shears_item_destructible"));

    public static final BlockTag IS_SHOVEL_ITEM_DESTRUCTIBLE = register("minecraft:is_shovel_item_destructible", new LazyBlockTag("minecraft:is_shovel_item_destructible"));

    public static final BlockTag IS_SWORD_ITEM_DESTRUCTIBLE = register("minecraft:is_sword_item_destructible", new LazyBlockTag("minecraft:is_sword_item_destructible"));

    public static final BlockTag JUNGLE = register("minecraft:jungle", new LazyBlockTag("minecraft:jungle"));

    public static final BlockTag LOG = register("minecraft:log", new LazyBlockTag("minecraft:log"));

    public static final BlockTag METAL = register("minecraft:metal", new LazyBlockTag("minecraft:metal"));

    public static final BlockTag MOB_SPAWNER = register("minecraft:mob_spawner", new LazyBlockTag("minecraft:mob_spawner"));

    public static final BlockTag NOT_FEATURE_REPLACEABLE = register("minecraft:not_feature_replaceable", new LazyBlockTag("minecraft:not_feature_replaceable"));

    public static final BlockTag OAK = register("minecraft:oak", new LazyBlockTag("minecraft:oak"));

    public static final BlockTag ONE_WAY_COLLIDABLE = register("minecraft:one_way_collidable", new LazyBlockTag("minecraft:one_way_collidable"));

    public static final BlockTag PLANT = register("minecraft:plant", new LazyBlockTag("minecraft:plant"));

    public static final BlockTag PUMPKIN = register("minecraft:pumpkin", new LazyBlockTag("minecraft:pumpkin"));

    public static final BlockTag RAIL = register("minecraft:rail", new LazyBlockTag("minecraft:rail"));

    public static final BlockTag SAND = register("minecraft:sand", new LazyBlockTag("minecraft:sand"));

    public static final BlockTag SNOW = register("minecraft:snow", new LazyBlockTag("minecraft:snow"));

    public static final BlockTag SPRUCE = register("minecraft:spruce", new LazyBlockTag("minecraft:spruce"));

    public static final BlockTag STONE = register("minecraft:stone", new LazyBlockTag("minecraft:stone"));

    public static final BlockTag STONE_PICK_DIGGABLE = register("minecraft:stone_pick_diggable", new LazyBlockTag("minecraft:stone_pick_diggable"));

    public static final BlockTag STONE_TIER_DESTRUCTIBLE = register("minecraft:stone_tier_destructible", new LazyBlockTag("minecraft:stone_tier_destructible"));

    public static final BlockTag TEXT_SIGN = register("minecraft:text_sign", new LazyBlockTag("minecraft:text_sign"));

    public static final BlockTag TRAPDOORS = register("minecraft:trapdoors", new LazyBlockTag("minecraft:trapdoors"));

    public static final BlockTag WATER = register("minecraft:water", new LazyBlockTag("minecraft:water"));

    public static final BlockTag WOOD = register("minecraft:wood", new LazyBlockTag("minecraft:wood"));

    public static BlockTag register(String tagName, BlockTag blockTag) {
        if (NAME_2_TAG.containsKey(tagName)) {
            throw new IllegalArgumentException("Block tag " + tagName + " is already registered");
        }

        NAME_2_TAG.put(tagName, blockTag);

        for (int blockId : blockTag.getBlockId()) {
            BLOCK_2_TAGS.computeIfAbsent(blockId, t -> new HashSet<>()).add(blockTag);
        }

        return blockTag;
    }

    public static Set<BlockTag> getTagsSet(int blockId) {
        return BLOCK_2_TAGS.getOrDefault(blockId, Collections.emptySet());
    }

    public static BlockTag getTag(String tagName) {
        return NAME_2_TAG.get(tagName);
    }
}

