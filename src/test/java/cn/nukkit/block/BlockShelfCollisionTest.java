package cn.nukkit.block;

import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockShelfCollisionTest {

    @BeforeAll
    static void initializeBlocks() {
        Block.init();
    }

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
