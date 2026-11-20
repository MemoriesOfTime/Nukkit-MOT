package cn.nukkit.block;

import cn.nukkit.block.properties.enums.DripstoneThickness;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Nested;
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
    void mudIsFourteenPixelsHigh() {
        Block mud = Block.get(BlockID.MUD);
        mud.setComponents(10d, 20d, 30d);

        assertInstanceOf(BlockMud.class, mud);
        assertEquals(20d + 14d / 16d, mud.getBoundingBox().getMaxY(), 1e-12);
        assertEquals(20d + 14d / 16d, mud.getCollisionBoundingBox().getMaxY(), 1e-12);
        assertEquals(20d, mud.getBoundingBox().getMinY(), 1e-12);
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

    /**
     * Collision boxes must match the client (minecraft-data bedrock/1.26.30
     * blockCollisionShapes.json). A server-only full cube lifts, blocks or holds players
     * where the client sees air.
     */
    @Nested
    class VanillaShapes {

        private static final double EPS = 1.0E-6;

        private static void assertBox(AxisAlignedBB box, double minX, double minY, double minZ,
                                      double maxX, double maxY, double maxZ) {
            assertEquals(minX, box.getMinX(), EPS);
            assertEquals(minY, box.getMinY(), EPS);
            assertEquals(minZ, box.getMinZ(), EPS);
            assertEquals(maxX, box.getMaxX(), EPS);
            assertEquals(maxY, box.getMaxY(), EPS);
            assertEquals(maxZ, box.getMaxZ(), EPS);
        }

        private static BlockPointedDripstone dripstone(DripstoneThickness thickness, boolean hanging) {
            BlockPointedDripstone block = new BlockPointedDripstone();
            block.setThickness(thickness);
            block.setHanging(hanging);
            block.setComponents(10, 40, -3);
            return block;
        }

        @Test
        void snifferEggMatchesBedrockShape() {
            BlockSnifferEgg egg = new BlockSnifferEgg();
            egg.setComponents(10, 40, -3);
            assertBox(egg.getBoundingBox(), 10 + 1 / 16d, 40, -3 + 2 / 16d, 10 + 15 / 16d, 41, -3 + 14 / 16d);
        }

        @Test
        void standingTipIsElevenSixteenthsHighAndSixWide() {
            assertBox(dripstone(DripstoneThickness.TIP, false).getBoundingBox(),
                    10 + 5 / 16d, 40, -3 + 5 / 16d, 10 + 11 / 16d, 40 + 11 / 16d, -3 + 11 / 16d);
        }

        @Test
        void hangingTipStartsFiveSixteenthsUp() {
            assertBox(dripstone(DripstoneThickness.TIP, true).getBoundingBox(),
                    10 + 5 / 16d, 40 + 5 / 16d, -3 + 5 / 16d, 10 + 11 / 16d, 41, -3 + 11 / 16d);
        }

        @Test
        void widthFollowsThicknessInBothDirections() {
            double[][] expected = {
                    // thickness ordinal -> half width in sixteenths
                    {DripstoneThickness.MERGE.ordinal(), 3},
                    {DripstoneThickness.FRUSTUM.ordinal(), 4},
                    {DripstoneThickness.MIDDLE.ordinal(), 5},
                    {DripstoneThickness.BASE.ordinal(), 6}};
            for (double[] row : expected) {
                DripstoneThickness thickness = DripstoneThickness.values()[(int) row[0]];
                double half = row[1] / 16d;
                for (boolean hanging : new boolean[]{false, true}) {
                    assertBox(dripstone(thickness, hanging).getBoundingBox(),
                            10.5 - half, 40, -2.5 - half, 10.5 + half, 41, -2.5 + half);
                }
            }
        }
    }

    @Nested
    class Shelf {

        private BlockShelf shelfFacing(BlockFace face) {
            BlockShelf shelf = new BlockShelfOak();
            shelf.setComponents(0d, 0d, 0d);
            shelf.setBlockFace(face);
            return shelf;
        }

        @Test
        void aShelfLeavesElevenPixelsOfTheBlockFree() {
            BlockShelf shelf = shelfFacing(BlockFace.SOUTH);
            AxisAlignedBB inFrontOfTheShelf =
                    new SimpleAxisAlignedBB(0.2d, 0d, 0.4d, 0.8d, 1.8d, 0.95d);

            assertFalse(shelf.collidesWithBB(inFrontOfTheShelf));

            List<AxisAlignedBB> boxes = new ArrayList<>();
            shelf.addCollisionBoxesToList(inFrontOfTheShelf, boxes);
            assertTrue(boxes.isEmpty());
        }

        @Test
        void aShelfStillStopsWhatTouchesItsBoards() {
            BlockShelf shelf = shelfFacing(BlockFace.SOUTH);
            AxisAlignedBB lowerBoard = new SimpleAxisAlignedBB(0.2d, 0.05d, 0.05d, 0.8d, 0.2d, 0.25d);
            AxisAlignedBB topBoard = new SimpleAxisAlignedBB(0.2d, 0.8d, 0.2d, 0.8d, 0.95d, 0.3d);
            AxisAlignedBB nicheBetweenTheBoards =
                    new SimpleAxisAlignedBB(0.2d, 0.4d, 0.25d, 0.8d, 0.6d, 0.3d);

            assertTrue(shelf.collidesWithBB(lowerBoard));
            assertTrue(shelf.collidesWithBB(topBoard));
            assertFalse(shelf.collidesWithBB(nicheBetweenTheBoards));

            List<AxisAlignedBB> boxes = new ArrayList<>();
            shelf.addCollisionBoxesToList(lowerBoard, boxes);
            assertEquals(1, boxes.size());
        }

        @Test
        void everyFacingKeepsTheShelfAgainstItsOwnWall() {
            assertEquals(0.3125d, shelfFacing(BlockFace.SOUTH).getBoundingBox().getMaxZ(), 1e-12);
            assertEquals(0.6875d, shelfFacing(BlockFace.NORTH).getBoundingBox().getMinZ(), 1e-12);
            assertEquals(0.3125d, shelfFacing(BlockFace.EAST).getBoundingBox().getMaxX(), 1e-12);
            assertEquals(0.6875d, shelfFacing(BlockFace.WEST).getBoundingBox().getMinX(), 1e-12);
        }
    }

    /** The inner quarter of a fence, wall or pane corner is free, the arms are not. */
    @Nested
    class CrossShapes {

        /** A block whose stretched box is fixed, as if its neighbours connected to it. */
        private static <T extends Block> T at(T block) {
            block.setComponents(10d, 64d, 20d);
            return block;
        }

        private static final class Fence extends BlockFence {
            private final AxisAlignedBB box;
            Fence(AxisAlignedBB box) { this.box = box; }
            @Override protected AxisAlignedBB recalculateBoundingBox() { return box; }
        }

        private static final class Wall extends BlockWall {
            private final AxisAlignedBB box;
            Wall(AxisAlignedBB box) { this.box = box; }
            @Override protected AxisAlignedBB recalculateBoundingBox() { return box; }
        }

        private static final class Pane extends BlockGlassPane {
            private final AxisAlignedBB box;
            Pane(AxisAlignedBB box) { this.box = box; }
            @Override protected AxisAlignedBB recalculateBoundingBox() { return box; }
        }

        private static AxisAlignedBB cell(double minX, double minZ, double maxX, double maxZ, double height) {
            return new SimpleAxisAlignedBB(10 + minX, 64, 20 + minZ, 10 + maxX, 64 + height, 20 + maxZ);
        }

        /** A thin probe standing on the ground in the given point of the cell. */
        private static AxisAlignedBB probe(double x, double z) {
            return new SimpleAxisAlignedBB(10 + x - 0.02, 64.1, 20 + z - 0.02, 10 + x + 0.02, 65, 20 + z + 0.02);
        }

        @Test
        void fenceCornerLeavesTheInnerQuarterFree() {
            // Connected to the west and to the south: the core box is 0..0.625 x 0.375..1.
            Block fence = at(new Fence(cell(0, 0.375, 0.625, 1, 1.5)));
            assertFalse(fence.collidesWithBB(probe(0.2, 0.8)), "inner quarter of the corner");
            assertFalse(fence.collidesWithBB(probe(0.2, 0.8), true), "inner quarter of the corner");
            assertTrue(fence.collidesWithBB(probe(0.5, 0.5)), "post");
            assertTrue(fence.collidesWithBB(probe(0.1, 0.5)), "west arm");
            assertTrue(fence.collidesWithBB(probe(0.5, 0.9)), "south arm");

            List<AxisAlignedBB> boxes = new ArrayList<>();
            fence.addCollisionBoxesToList(new SimpleAxisAlignedBB(9, 63, 19, 12, 67, 22), boxes);
            assertEquals(2, boxes.size(), "an L is two arms, the post lies inside them");
            for (AxisAlignedBB box : boxes) {
                assertEquals(65.5, box.getMaxY(), 1e-12, "a fence stays a block and a half high");
            }
        }

        @Test
        void outlineOfTheFenceStaysTheStretchedBox() {
            Block fence = at(new Fence(cell(0, 0.375, 0.625, 1, 1.5)));
            assertTrue(fence.collidesWithBB(probe(0.2, 0.8), false));
        }

        @Test
        void straightFenceAndLonePostAreUnchanged() {
            AxisAlignedBB straight = cell(0, 0.375, 1, 0.625, 1.5);
            List<AxisAlignedBB> boxes = new ArrayList<>();
            at(new Fence(straight)).addCollisionBoxesToList(straight.grow(1, 1, 1), boxes);
            assertEquals(List.of(straight).toString(), boxes.toString());

            AxisAlignedBB post = cell(0.375, 0.375, 0.625, 0.625, 1.5);
            boxes.clear();
            at(new Fence(post)).addCollisionBoxesToList(post.grow(1, 1, 1), boxes);
            assertEquals(List.of(post).toString(), boxes.toString());
        }

        @Test
        void wallCornerHasAThickPostAndNarrowArms() {
            // Connected to the east and to the north: 0.25..1 x 0..0.75.
            Block wall = at(new Wall(cell(0.25, 0, 1, 0.75, 1.5)));
            assertFalse(wall.collidesWithBB(probe(0.9, 0.1)), "inner quarter");
            assertTrue(wall.collidesWithBB(probe(0.27, 0.5)), "post edge");
            assertFalse(wall.collidesWithBB(probe(0.9, 0.29)), "beside the east arm");
            assertTrue(wall.collidesWithBB(probe(0.9, 0.5)), "east arm");
            assertTrue(wall.collidesWithBB(probe(0.5, 0.05)), "north arm");
        }

        @Test
        void straightWallWithoutPostKeepsItsNarrowBox() {
            AxisAlignedBB straight = cell(0.3125, 0, 0.6875, 1, 1.5);
            List<AxisAlignedBB> boxes = new ArrayList<>();
            at(new Wall(straight)).addCollisionBoxesToList(straight.grow(1, 1, 1), boxes);
            assertEquals(List.of(straight).toString(), boxes.toString());
        }

        @Test
        void paneCrossLeavesAllFourQuartersFree() {
            Block pane = at(new Pane(cell(0, 0, 1, 1, 1)));
            assertFalse(pane.collidesWithBB(probe(0.2, 0.2)));
            assertFalse(pane.collidesWithBB(probe(0.8, 0.2)));
            assertFalse(pane.collidesWithBB(probe(0.2, 0.8)));
            assertFalse(pane.collidesWithBB(probe(0.8, 0.8)));
            assertTrue(pane.collidesWithBB(probe(0.5, 0.1)));
            assertTrue(pane.collidesWithBB(probe(0.9, 0.5)));
        }
    }
}
