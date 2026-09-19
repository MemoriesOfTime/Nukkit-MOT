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

/** Bug report 848: the inner quarter of a fence, wall or pane corner is free, the arms are not. */
class CrossCollisionShapeTest {

    @BeforeAll
    static void initializeBlocks() {
        Block.init();
    }

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
