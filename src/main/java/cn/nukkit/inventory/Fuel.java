package cn.nukkit.inventory;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;

import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class Fuel {

    public static final Map<Integer, Short> duration = new TreeMap<>();
    public static final Map<String, Short> durationByNamespaceId = new TreeMap<>();

    static {
        duration.put(Item.COAL, (short) 1600);
        duration.put(Item.COAL_BLOCK, (short) 16000);
        registerBlocks((short) 4000, Item.DRIED_KELP_BLOCK);
        duration.put(Item.TRUNK, (short) 300);
        duration.put(Item.WOODEN_PLANKS, (short) 300);
        duration.put(Item.SAPLING, (short) 100);
        duration.put(Item.WOODEN_AXE, (short) 200);
        duration.put(Item.WOODEN_PICKAXE, (short) 200);
        duration.put(Item.WOODEN_SWORD, (short) 200);
        duration.put(Item.WOODEN_SHOVEL, (short) 200);
        duration.put(Item.WOODEN_HOE, (short) 200);
        duration.put(Item.STICK, (short) 100);
        duration.put(Item.FENCE, (short) 300);
        duration.put(Item.FENCE_GATE, (short) 300);
        duration.put(Item.FENCE_GATE_SPRUCE, (short) 300);
        duration.put(Item.FENCE_GATE_BIRCH, (short) 300);
        duration.put(Item.FENCE_GATE_JUNGLE, (short) 300);
        duration.put(Item.FENCE_GATE_ACACIA, (short) 300);
        duration.put(Item.FENCE_GATE_DARK_OAK, (short) 300);
        duration.put(Item.WOODEN_STAIRS, (short) 300);
        duration.put(Item.SPRUCE_WOOD_STAIRS, (short) 300);
        duration.put(Item.BIRCH_WOOD_STAIRS, (short) 300);
        duration.put(Item.JUNGLE_WOOD_STAIRS, (short) 300);
        duration.put(Item.TRAPDOOR, (short) 300);
        duration.put(Item.WORKBENCH, (short) 300);
        duration.put(Item.BOOKSHELF, (short) 300);
        duration.put(Item.CHEST, (short) 300);
        duration.put(Item.BUCKET, (short) 20000);
        duration.put(Item.LADDER, (short) 300);
        duration.put(Item.BOW, (short) 200);
        duration.put(Item.BOWL, (short) 200);
        duration.put(Item.WOOD2, (short) 300);

        registerBlocks((short) 300,
                Item.STRIPPED_OAK_LOG,
                Item.STRIPPED_DARK_OAK_LOG,
                Item.STRIPPED_ACACIA_LOG,
                Item.STRIPPED_JUNGLE_LOG,
                Item.STRIPPED_BIRCH_LOG,
                Item.STRIPPED_SPRUCE_LOG);

        duration.put(Item.WOODEN_PRESSURE_PLATE, (short) 300);
        duration.put(Item.ACACIA_WOOD_STAIRS, (short) 300);
        duration.put(Item.DARK_OAK_WOOD_STAIRS, (short) 300);
        duration.put(Item.TRAPPED_CHEST, (short) 300);
        duration.put(Item.DAYLIGHT_DETECTOR, (short) 300);
        duration.put(Item.DAYLIGHT_DETECTOR_INVERTED, (short) 300);
        duration.put(Item.JUKEBOX, (short) 300);
        duration.put(Item.NOTEBLOCK, (short) 300);
        duration.put(Item.WOOD_SLAB, (short) 300);
        duration.put(Item.DOUBLE_WOOD_SLAB, (short) 300);
        duration.put(Item.BOAT, (short) 1200);
        duration.put(Item.ACACIA_CHEST_BOAT, (short) 1200);
        duration.put(Item.BIRCH_CHEST_BOAT, (short) 1200);
        duration.put(Item.JUNGLE_CHEST_BOAT, (short) 1200);
        duration.put(Item.DARK_OAK_CHEST_BOAT, (short) 1200);
        duration.put(Item.MANGROVE_CHEST_BOAT, (short) 1200);
        duration.put(Item.OAK_CHEST_BOAT, (short) 1200);
        duration.put(Item.SPRUCE_CHEST_BOAT, (short) 1200);
        duration.put(Item.CHERRY_CHEST_BOAT, (short) 1200);
        duration.put(Item.PALE_OAK_CHEST_BOAT, (short) 1200);
        duration.put(Item.BAMBOO_CHEST_RAFT, (short) 1200);
        duration.put(Item.BLAZE_ROD, (short) 2400);
        duration.put(Item.BROWN_MUSHROOM_BLOCK, (short) 300);
        duration.put(Item.RED_MUSHROOM_BLOCK, (short) 300);
        duration.put(Item.FISHING_ROD, (short) 300);
        registerBlocks((short) 100,
                Item.WOODEN_BUTTON,
                Item.SPRUCE_BUTTON,
                Item.BIRCH_BUTTON,
                Item.JUNGLE_BUTTON,
                Item.ACACIA_BUTTON,
                Item.DARK_OAK_BUTTON,
                Item.MANGROVE_BUTTON,
                Item.BAMBOO_BUTTON,
                Item.CHERRY_BUTTON,
                Item.PALE_OAK_BUTTON);
        duration.put(Item.WOODEN_DOOR, (short) 200);
        duration.put(Item.SPRUCE_DOOR, (short) 200);
        duration.put(Item.BIRCH_DOOR, (short) 200);
        duration.put(Item.JUNGLE_DOOR, (short) 200);
        duration.put(Item.ACACIA_DOOR, (short) 200);
        duration.put(Item.DARK_OAK_DOOR, (short) 200);
        duration.put(Item.BAMBOO_DOOR, (short) 200);
        duration.put(Item.BANNER, (short) 300);
        duration.put(Item.CROSSBOW, (short) 200);
        duration.put(Item.DEAD_BUSH, (short) 100);
        registerBlocks((short) 50, Item.BAMBOO, Item.SCAFFOLDING);
        registerBlocks((short) 300,
                Item.BAMBOO_BLOCK,
                Item.STRIPPED_BAMBOO_BLOCK,
                Item.BAMBOO_MOSAIC,
                Item.BAMBOO_MOSAIC_SLAB,
                Item.BAMBOO_MOSAIC_STAIRS,
                Item.CHISELED_BOOKSHELF,
                Item.MANGROVE_ROOTS,
                Item.CARTOGRAPHY_TABLE,
                Item.FLETCHING_TABLE,
                Item.SMITHING_TABLE,
                Item.LOOM,
                Item.LECTERN,
                Item.COMPOSTER,
                Item.BARREL,
                Item.MANGROVE_LOG,
                Item.STRIPPED_MANGROVE_LOG,
                Item.MANGROVE_PLANKS,
                Item.MANGROVE_STAIRS,
                Item.MANGROVE_SLAB,
                Item.MANGROVE_PRESSURE_PLATE,
                Item.MANGROVE_FENCE,
                Item.MANGROVE_FENCE_GATE,
                Item.MANGROVE_TRAPDOOR,
                Item.MANGROVE_WOOD,
                Item.STRIPPED_MANGROVE_WOOD,
                Item.BAMBOO_PLANKS,
                Item.BAMBOO_STAIRS,
                Item.BAMBOO_SLAB,
                Item.BAMBOO_PRESSURE_PLATE,
                Item.BAMBOO_FENCE,
                Item.BAMBOO_FENCE_GATE,
                Item.BAMBOO_TRAPDOOR,
                Item.CHERRY_LOG,
                Item.STRIPPED_CHERRY_LOG,
                Item.CHERRY_PLANKS,
                Item.CHERRY_STAIRS,
                Item.CHERRY_SLAB,
                Item.CHERRY_PRESSURE_PLATE,
                Item.CHERRY_FENCE,
                Item.CHERRY_FENCE_GATE,
                Item.CHERRY_TRAPDOOR,
                Item.CHERRY_WOOD,
                Item.STRIPPED_CHERRY_WOOD,
                Item.PALE_OAK_LOG,
                Item.STRIPPED_PALE_OAK_LOG,
                Item.PALE_OAK_PLANKS,
                Item.PALE_OAK_STAIRS,
                Item.PALE_OAK_SLAB,
                Item.PALE_OAK_PRESSURE_PLATE,
                Item.PALE_OAK_FENCE,
                Item.PALE_OAK_FENCE_GATE,
                Item.PALE_OAK_TRAPDOOR,
                Item.PALE_OAK_WOOD,
                Item.STRIPPED_PALE_OAK_WOOD,
                Item.SPRUCE_PRESSURE_PLATE,
                Item.BIRCH_PRESSURE_PLATE,
                Item.JUNGLE_PRESSURE_PLATE,
                Item.ACACIA_PRESSURE_PLATE,
                Item.DARK_OAK_PRESSURE_PLATE,
                Item.SPRUCE_TRAPDOOR,
                Item.BIRCH_TRAPDOOR,
                Item.JUNGLE_TRAPDOOR,
                Item.ACACIA_TRAPDOOR,
                Item.DARK_OAK_TRAPDOOR);
        registerBlocks((short) 100,
                Item.AZALEA,
                Item.FLOWERING_AZALEA,
                Item.LEAF_LITTER,
                Item.SHORT_DRY_GRASS,
                Item.TALL_DRY_GRASS,
                Item.MANGROVE_PROPAGULE,
                Item.CHERRY_SAPLING,
                Item.PALE_OAK_SAPLING);
        duration.put(Item.SIGN, (short) 200);
        duration.put(Item.ACACIA_SIGN, (short) 200);
        duration.put(Item.BIRCH_SIGN, (short) 200);
        duration.put(Item.SPRUCE_SIGN, (short) 200);
        duration.put(Item.DARKOAK_SIGN, (short) 200);
        duration.put(Item.JUNGLE_SIGN, (short) 200);
        duration.put(Item.MANGROVE_SIGN, (short) 200);
        duration.put(Item.BAMBOO_SIGN, (short) 200);
        duration.put(Item.CHERRY_SIGN, (short) 200);
        duration.put(Item.PALE_OAK_SIGN, (short) 200);
        registerBlocks((short) 200,
                Item.OAK_HANGING_SIGN,
                Item.SPRUCE_HANGING_SIGN,
                Item.BIRCH_HANGING_SIGN,
                Item.JUNGLE_HANGING_SIGN,
                Item.ACACIA_HANGING_SIGN,
                Item.DARK_OAK_HANGING_SIGN,
                Item.MANGROVE_HANGING_SIGN,
                Item.BAMBOO_HANGING_SIGN,
                Item.CHERRY_HANGING_SIGN,
                Item.PALE_OAK_HANGING_SIGN,
                Item.PALE_OAK_DOOR);
        registerStringItems((short) 200,
                ItemNamespaceId.BAMBOO_DOOR_NAMESPACE_ID,
                ItemNamespaceId.CHERRY_DOOR,
                ItemNamespaceId.MANGROVE_DOOR,
                ItemNamespaceId.WOODEN_SPEAR);
        registerStringItems((short) 300, ItemNamespaceId.CHISELED_BOOKSHELF_NAMESPACE_ID);
    }

    private static void registerBlocks(short burnTime, int... blockIds) {
        for (int blockId : blockIds) {
            duration.put(blockId > 255 ? 255 - blockId : blockId, burnTime);
        }
    }

    private static void registerStringItems(short burnTime, String... namespaceIds) {
        for (String namespaceId : namespaceIds) {
            durationByNamespaceId.put(namespaceId.toLowerCase(Locale.ROOT), burnTime);
        }
    }

    public static Short getDuration(String namespaceId) {
        return durationByNamespaceId.get(namespaceId.toLowerCase(Locale.ROOT));
    }
}
