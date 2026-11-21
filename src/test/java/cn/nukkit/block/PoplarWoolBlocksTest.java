package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDoorPoplar;
import cn.nukkit.item.ItemID;
import cn.nukkit.utils.DyeColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PoplarWoolBlocksTest {

    /** 标识符 → 方块 ID，与 block_ids.csv 一致；写死以免测试复用被校验的生产映射。 */
    private static final Object[][] POPLAR = {
            {"poplar_button", 1381}, {"poplar_fence", 1383}, {"poplar_fence_gate", 1384},
            {"poplar_hanging_sign", 1385}, {"stripped_poplar_log", 1386}, {"poplar_log", 1387},
            {"poplar_planks", 1388}, {"poplar_pressure_plate", 1389}, {"poplar_slab", 1390},
            {"poplar_double_slab", 1391}, {"poplar_stairs", 1392}, {"poplar_standing_sign", 1393},
            {"poplar_trapdoor", 1394}, {"poplar_wall_sign", 1395}, {"stripped_poplar_wood", 1396},
            {"poplar_wood", 1397}, {"poplar_sapling", 1398}, {"orange_poplar_leaves", 1399},
            {"red_poplar_leaves", 1400}, {"yellow_poplar_leaves", 1401}, {"poplar_shelf", 1402},
    };

    private static final String[] WOOL_COLORS = {
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
    };

    private static final int FIRST_WOOL_SLAB = 1420;
    private static final int FIRST_WOOL_DOUBLE_SLAB = 1436;
    /** 楼梯 ID 排列顺序与台阶/双台阶不同（沿用上游分配）。 */
    private static final int[] WOOL_STAIRS = {
            1405, // white
            1414, // light_gray
            1413, // gray
            1419, // black
            1418, // brown
            1404, // red
            1407, // orange
            1410, // yellow
            1411, // lime
            1417, // green
            1415, // cyan
            1409, // light_blue
            1406, // blue
            1416, // purple
            1408, // magenta
            1412, // pink
    };

    @BeforeAll
    static void initialize() {
        MockServer.init();
        Block.init();
    }

    @Test
    void poplarBlocksAreRegisteredAndAddressableByItemName() {
        for (Object[] entry : POPLAR) {
            String identifier = "minecraft:" + entry[0];
            int blockId = (Integer) entry[1];

            Block block = Block.get(blockId);
            assertFalse(block instanceof BlockUnknown, identifier + " is not registered in Blocks");
            assertEquals(blockId, block.getId(), identifier);

            Item item = Item.fromString(identifier);
            assertNotSame(Item.AIR, item.getId(), identifier + " must not resolve to air");
            assertEquals(blockId, item.getBlock().getId(), identifier + " must resolve to its block");
        }
    }

    @Test
    void poplarDoorItemUsesItsOwnNamespacedIdentifier() {
        Item door = Item.fromString("minecraft:poplar_door");
        assertInstanceOf(ItemDoorPoplar.class, door);
        assertEquals("minecraft:poplar_door", ((ItemDoorPoplar) door).getNamespaceId());
    }

    @Test
    void poplarSignItemIsItsOwnItemIdAndBackedByTheSignPostBlock() {
        Item sign = Item.fromString("minecraft:poplar_sign");
        assertEquals(ItemID.POPLAR_SIGN, sign.getId());
        assertEquals(BlockID.POPLAR_STANDING_SIGN, sign.getBlock().getId());
    }

    @Test
    void woolSlabsStairsAndDoubleSlabsAgreeOnTheirColour() {
        for (int i = 0; i < WOOL_COLORS.length; i++) {
            String identifier = "minecraft:" + WOOL_COLORS[i];
            int slabId = FIRST_WOOL_SLAB + i;
            int doubleSlabId = FIRST_WOOL_DOUBLE_SLAB + i;
            int stairsId = WOOL_STAIRS[i];

            BlockSlabWool slab = assertInstanceOf(BlockSlabWool.class, Block.get(slabId), identifier + "_wool_slab");
            BlockDoubleSlabWool doubleSlab = assertInstanceOf(BlockDoubleSlabWool.class,
                    Block.get(doubleSlabId), identifier + "_wool_double_slab");
            BlockStairsWool stairs = assertInstanceOf(BlockStairsWool.class, Block.get(stairsId),
                    identifier + "_wool_stairs");

            DyeColor colour = slab.getDyeColor();
            assertEquals(colour, doubleSlab.getDyeColor(), identifier + " double slab colour");
            assertEquals(colour, stairs.getDyeColor(), identifier + " stairs colour");
            assertEquals(slabId, doubleSlab.getSingleSlabId(), identifier + " double slab must drop its own slab");

            assertEquals(slabId, Item.fromString(identifier + "_wool_slab").getBlock().getId());
            assertEquals(doubleSlabId, Item.fromString(identifier + "_wool_double_slab").getBlock().getId());
            assertEquals(stairsId, Item.fromString(identifier + "_wool_stairs").getBlock().getId());
        }
    }

    @Test
    void woolBlockNamesKeepTheirColour() {
        assertEquals("White Wool Slab", Block.get(BlockID.WHITE_WOOL_SLAB).getName());
        assertEquals("Light Gray Wool Stairs", Block.get(BlockID.LIGHT_GRAY_WOOL_STAIRS).getName());
        assertEquals("Magenta Wool Double Slab", Block.get(BlockID.MAGENTA_WOOL_DOUBLE_SLAB).getName());
    }

    @Test
    void breakingAWoolDoubleSlabYieldsTwoSlabs() {
        Block doubleSlab = Block.get(BlockID.WHITE_WOOL_DOUBLE_SLAB);
        Item[] drops = doubleSlab.getDrops(Item.get(Item.WOODEN_PICKAXE));
        assertEquals(1, drops.length);
        assertEquals(BlockID.WHITE_WOOL_SLAB, drops[0].getBlock().getId());
        assertEquals(2, drops[0].getCount());
    }

    @Test
    void poplarLogAxisUsesTheWoodMetaLayout() {
        // 白杨原木沿用 BlockWood 的 pillar_axis 布局：y=0、x=4、z=8
        assertEquals(0, Block.get(BlockID.POPLAR_LOG, 0).getDamage());
        assertEquals(4, Block.get(BlockID.POPLAR_LOG, 4).getDamage());
        assertEquals(8, Block.get(BlockID.POPLAR_LOG, 8).getDamage());
        assertEquals("Poplar Log", Block.get(BlockID.POPLAR_LOG, 4).getName());
        assertTrue(Block.get(BlockID.POPLAR_WOOD) instanceof BlockWoodBark);
    }
}
