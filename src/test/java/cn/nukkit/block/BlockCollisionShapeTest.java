package cn.nukkit.block;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

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
    void stonecuttersKeepTheirOwnHeights() {
        // BDS：旧切石机（245）满格，新切石机（452）9px 高 / old stonecutter is a full cube, stonecutter_block is 9px
        Block legacy = Block.get(BlockID.STONECUTTER);
        legacy.setComponents(10d, 20d, 30d);
        assertEquals(21d, legacy.getBoundingBox().getMaxY(), 1e-12);

        Block modern = Block.get(BlockID.STONECUTTER_BLOCK);
        modern.setComponents(10d, 20d, 30d);
        assertEquals(20d + 9d / 16d, modern.getBoundingBox().getMaxY(), 1e-12);
    }

    @Test
    void hopperMatchesTheBedrockShape() {
        Block hopper = Block.get(BlockID.HOPPER_BLOCK);
        hopper.setComponents(0d, 0d, 0d);
        // 法兰以下的四角为空 / the corners below the flange are open
        AxisAlignedBB cornerUnderFlange = new SimpleAxisAlignedBB(0.01d, 0.01d, 0.01d, 0.2d, 0.6d, 0.2d);
        // 法兰（y10-11px 满幅）/ the flange spans the full footprint
        AxisAlignedBB flange = new SimpleAxisAlignedBB(0.3d, 0.63d, 0.3d, 0.7d, 0.7d, 0.7d);
        // 漏斗体（内缩 4-12px）/ the inset funnel body
        AxisAlignedBB funnel = new SimpleAxisAlignedBB(0.4d, 0.3d, 0.4d, 0.6d, 0.6d, 0.6d);
        // 法兰之上的 2px 壁 / the 2px wall above the flange
        AxisAlignedBB wall = new SimpleAxisAlignedBB(0.01d, 0.7d, 0.4d, 0.1d, 0.9d, 0.6d);
        // 朝下时的底部出料管 / the bottom spout tube when facing down
        AxisAlignedBB spout = new SimpleAxisAlignedBB(0.4d, 0.01d, 0.4d, 0.6d, 0.2d, 0.6d);

        assertFalse(hopper.collidesWithBB(cornerUnderFlange));
        assertTrue(hopper.collidesWithBB(flange));
        assertTrue(hopper.collidesWithBB(funnel));
        assertTrue(hopper.collidesWithBB(wall));
        assertTrue(hopper.collidesWithBB(spout));

        List<AxisAlignedBB> boxes = new ArrayList<>();
        hopper.addCollisionBoxesToList(new SimpleAxisAlignedBB(0d, 0d, 0d, 1d, 1d, 1d), boxes);
        assertEquals(7, boxes.size());
    }

    @Test
    void sidewaysHopperMovesItsSpout() {
        Block north = Block.get(BlockID.HOPPER_BLOCK, 2);
        north.setComponents(0d, 0d, 0d);
        AxisAlignedBB sideSpout = new SimpleAxisAlignedBB(0.4d, 0.3d, 0.01d, 0.6d, 0.45d, 0.2d);
        AxisAlignedBB bottomTube = new SimpleAxisAlignedBB(0.4d, 0.01d, 0.4d, 0.6d, 0.2d, 0.6d);

        assertTrue(north.collidesWithBB(sideSpout));
        assertFalse(north.collidesWithBB(bottomTube));
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
