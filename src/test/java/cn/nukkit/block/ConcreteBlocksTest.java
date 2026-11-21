package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.utils.DyeColor;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConcreteBlocksTest {

    private static final String[] COLORS = {
            "white", "light_gray", "gray", "black", "brown", "red", "orange", "yellow",
            "lime", "green", "cyan", "light_blue", "blue", "purple", "magenta", "pink"
    };

    private static final int FIRST_SLAB = 1470;
    private static final int FIRST_DOUBLE_SLAB = 1486;
    /** 楼梯 id 顺序与台阶不同（红打头），沿用原版分配。 */
    private static final int[] STAIRS = {
            1455, // white
            1464, // light_gray
            1463, // gray
            1469, // black
            1468, // brown
            1454, // red
            1457, // orange
            1460, // yellow
            1461, // lime
            1467, // green
            1465, // cyan
            1459, // light_blue
            1456, // blue
            1466, // purple
            1458, // magenta
            1462, // pink
    };

    @BeforeAll
    static void initialize() {
        MockServer.init();
        Block.init();
    }

    @Test
    void slabsStairsAndDoubleSlabsAgreeOnTheirColour() {
        for (int i = 0; i < COLORS.length; i++) {
            String identifier = "minecraft:" + COLORS[i];
            int slabId = FIRST_SLAB + i;
            int doubleSlabId = FIRST_DOUBLE_SLAB + i;
            int stairsId = STAIRS[i];

            BlockSlabConcrete slab = assertInstanceOf(BlockSlabConcrete.class, Block.get(slabId),
                    identifier + "_concrete_slab");
            BlockDoubleSlabConcrete doubleSlab = assertInstanceOf(BlockDoubleSlabConcrete.class,
                    Block.get(doubleSlabId), identifier + "_concrete_double_slab");
            BlockStairsConcrete stairs = assertInstanceOf(BlockStairsConcrete.class, Block.get(stairsId),
                    identifier + "_concrete_stairs");

            DyeColor colour = slab.getDyeColor();
            assertEquals(colour, doubleSlab.getDyeColor(), identifier + " double slab colour");
            assertEquals(colour, stairs.getDyeColor(), identifier + " stairs colour");
            assertEquals(slabId, doubleSlab.getSingleSlabId(), identifier + " double slab must drop its own slab");

            assertEquals(slabId, Item.fromString(identifier + "_concrete_slab").getBlock().getId());
            assertEquals(doubleSlabId, Item.fromString(identifier + "_concrete_double_slab").getBlock().getId());
            assertEquals(stairsId, Item.fromString(identifier + "_concrete_stairs").getBlock().getId());
        }
    }

    @Test
    void slabsNeighboursToTheirOwnFamilyOnly() {
        // 混色台阶不可合并：doubleSlab id 不同，BlockSlab.place 会拒绝
        assertNotSame(Block.get(BlockID.WHITE_CONCRETE_SLAB).getId(),
                Block.get(BlockID.RED_CONCRETE_SLAB).getId());
        assertEquals(BlockID.WHITE_CONCRETE_DOUBLE_SLAB,
                ((BlockSlab) Block.get(BlockID.WHITE_CONCRETE_SLAB)).doubleSlab);
        assertEquals(BlockID.RED_CONCRETE_DOUBLE_SLAB,
                ((BlockSlab) Block.get(BlockID.RED_CONCRETE_SLAB)).doubleSlab);
    }

    @Test
    void breakingNeedsAPickaxeAndYieldsTwoSlabs() {
        Block doubleSlab = Block.get(BlockID.WHITE_CONCRETE_DOUBLE_SLAB);
        Item[] byHand = doubleSlab.getDrops(Item.AIR_ITEM);
        assertEquals(0, byHand.length, "concrete must not drop without a pickaxe");

        Item[] byPickaxe = doubleSlab.getDrops(Item.get(Item.WOODEN_PICKAXE));
        assertEquals(1, byPickaxe.length);
        assertEquals(BlockID.WHITE_CONCRETE_SLAB, byPickaxe[0].getBlock().getId());
        assertEquals(2, byPickaxe[0].getCount());

        assertEquals(0, Block.get(BlockID.WHITE_CONCRETE_SLAB).getDrops(Item.AIR_ITEM).length);
        assertEquals(1, Block.get(BlockID.WHITE_CONCRETE_SLAB).getDrops(Item.get(Item.WOODEN_PICKAXE)).length);
        assertEquals(0, Block.get(BlockID.WHITE_CONCRETE_STAIRS).getDrops(Item.AIR_ITEM).length);
        assertEquals(1, Block.get(BlockID.WHITE_CONCRETE_STAIRS).getDrops(Item.get(Item.WOODEN_PICKAXE)).length);
    }

    @Test
    void everyConcreteBlockIsRegistered() {
        for (int id = 1454; id <= 1501; id++) {
            Block block = Block.get(id);
            assertFalse(block instanceof BlockUnknown, "unregistered concrete block id " + id);
            assertEquals(id, block.getId());
        }
    }

    @Test
    void blockNamesKeepTheirColour() {
        assertEquals("White Concrete Slab", Block.get(BlockID.WHITE_CONCRETE_SLAB).getName());
        assertEquals("Light Gray Concrete Stairs", Block.get(BlockID.LIGHT_GRAY_CONCRETE_STAIRS).getName());
        assertEquals("Magenta Concrete Double Slab", Block.get(BlockID.MAGENTA_CONCRETE_DOUBLE_SLAB).getName());
    }
}
