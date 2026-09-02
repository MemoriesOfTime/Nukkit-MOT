package cn.nukkit.block;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockCollisionShapeTest {

    @BeforeAll
    static void initializeBlocks() {
        Block.init();
    }

    @Test
    void enchantingTableIsTwelvePixelsHigh() {
        Block table = Block.get(BlockID.ENCHANTING_TABLE);
        table.setComponents(10d, 20d, 30d);

        assertEquals(20.75d, table.getCollisionBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void grassPathIsFifteenPixelsHigh() {
        Block path = Block.get(BlockID.GRASS_PATH);
        path.setComponents(10d, 20d, 30d);

        assertEquals(20d + 15d / 16d, path.getBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void farmlandIsFifteenPixelsHigh() {
        Block farmland = Block.get(BlockID.FARMLAND);
        farmland.setComponents(10d, 20d, 30d);

        assertEquals(20d + 15d / 16d, farmland.getBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void soulSandIsFourteenPixelsHigh() {
        Block soulSand = Block.get(BlockID.SOUL_SAND);
        soulSand.setComponents(10d, 20d, 30d);

        assertEquals(20d + 14d / 16d, soulSand.getBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void loneGlassPaneStaysACentrePost() {
        Block pane = Block.get(BlockID.GLASS_PANE);
        pane.setComponents(0d, 0d, 0d);

        assertEquals(7d / 16d, pane.getBoundingBox().getMinX(), 1e-12);
        assertEquals(9d / 16d, pane.getBoundingBox().getMaxX(), 1e-12);
        assertEquals(7d / 16d, pane.getBoundingBox().getMinZ(), 1e-12);
        assertEquals(9d / 16d, pane.getBoundingBox().getMaxZ(), 1e-12);
    }

    @Test
    void stonecutterIsNinePixelsHigh() {
        Block stonecutter = Block.get(BlockID.STONECUTTER_BLOCK);
        stonecutter.setComponents(10d, 20d, 30d);

        assertEquals(20d + 9d / 16d, stonecutter.getBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void hopperHasABowlAndARim() {
        Block hopper = Block.get(BlockID.HOPPER_BLOCK);
        hopper.setComponents(0d, 0d, 0d);
        AxisAlignedBB bowl = new SimpleAxisAlignedBB(0.3d, 10d / 16d + 0.001d, 0.3d,
                0.7d, 0.95d, 0.7d);
        AxisAlignedBB rim = new SimpleAxisAlignedBB(0.01d, 10d / 16d + 0.001d, 0.4d,
                0.1d, 0.95d, 0.6d);

        assertFalse(hopper.collidesWithBB(bowl));
        assertTrue(hopper.collidesWithBB(rim));
    }

    @Test
    void snowLayerGrowsTwoPixelsPerLayer() {
        for (int layers = 0; layers < 8; layers++) {
            Block snow = Block.get(BlockID.SNOW_LAYER, layers);
            snow.setComponents(0d, 0d, 0d);

            assertEquals(layers / 8d, snow.getMaxY(), 1e-12);
        }
    }

    @Test
    void cauldronHasAFreeCavityAndSolidBottomAndRim() {
        Block cauldron = Block.get(BlockID.CAULDRON_BLOCK, 6);
        cauldron.setComponents(0d, 0d, 0d);
        AxisAlignedBB cavity = new SimpleAxisAlignedBB(0.2d, 5d / 16d + 0.001d, 0.2d,
                0.8d, 0.95d, 0.8d);
        AxisAlignedBB bottom = new SimpleAxisAlignedBB(0.2d, 0.1d, 0.2d, 0.8d, 0.2d, 0.8d);
        AxisAlignedBB rim = new SimpleAxisAlignedBB(0.01d, 0.7d, 0.2d, 0.1d, 0.9d, 0.8d);

        assertFalse(cauldron.collidesWithBB(cavity, true));
        assertTrue(cauldron.collidesWithBB(bottom, true));
        assertTrue(cauldron.collidesWithBB(rim, true));

        List<AxisAlignedBB> boxes = new ArrayList<>();
        cauldron.addCollisionBoxesToList(cavity, boxes);
        assertTrue(boxes.isEmpty());
        cauldron.addCollisionBoxesToList(bottom, boxes);
        assertEquals(1, boxes.size());
    }

    @Test
    void defaultCollisionBoxesPreserveVirtualShapePredicate() {
        Block stair = Block.get(BlockID.WOOD_STAIRS, 0);
        stair.setComponents(0d, 0d, 0d);
        AxisAlignedBB upperStep =
                new SimpleAxisAlignedBB(0.75d, 0.75d, 0.25d, 0.9d, 0.9d, 0.4d);

        assertFalse(stair.getCollisionBoundingBox().intersectsWith(upperStep));
        List<AxisAlignedBB> boxes = new ArrayList<>();
        stair.addCollisionBoxesToList(upperStep, boxes);
        assertEquals(1, boxes.size());
    }
}
