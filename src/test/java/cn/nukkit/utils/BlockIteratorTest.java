package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * Tests for BlockIterator to ensure proper ray tracing functionality.
 * These tests verify that BlockIterator handles boundary conditions correctly,
 * including the fix for "Start block missed" error (GitHub Issue #701).
 *
 * @author Nukkit-MOT Team
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class BlockIteratorTest {

    private static Level level;

    @BeforeAll
    public void setUp() {
        MockServer.init();
        level = Server.getInstance() != null ? Server.getInstance().getDefaultLevel() : null;
    }

    /**
     * Test BlockIterator with normal position (center of block).
     * This should always work without throwing exceptions.
     */
    @Test
    public void testNormalBlockCenterPosition() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext(), "Iterator should have at least one block");
        }, "BlockIterator should handle normal block center position");
    }

    /**
     * Test BlockIterator with position on block face (x-axis).
     * This tests the boundary condition that could trigger "Start block missed".
     */
    @Test
    public void testBlockFacePositionX() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.0, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle position on block face (x-axis)");
    }

    /**
     * Test BlockIterator with position on block face (y-axis).
     */
    @Test
    public void testBlockFacePositionY() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.0, 10.5);
            Vector3 direction = new Vector3(0, 1, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle position on block face (y-axis)");
    }

    /**
     * Test BlockIterator with position on block face (z-axis).
     */
    @Test
    public void testBlockFacePositionZ() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.0);
            Vector3 direction = new Vector3(0, 0, 1);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle position on block face (z-axis)");
    }

    /**
     * Test BlockIterator with position on block edge (intersection of two faces).
     */
    @Test
    public void testBlockEdgePosition() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.0, 64.0, 10.5);
            Vector3 direction = new Vector3(1, 1, 0).normalize();
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle position on block edge");
    }

    /**
     * Test BlockIterator with position on block corner (intersection of three faces).
     * This is the most problematic case for "Start block missed" error.
     */
    @Test
    public void testBlockCornerPosition() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.0, 64.0, 10.0);
            Vector3 direction = new Vector3(1, 1, 1).normalize();
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle position on block corner");
    }

    /**
     * Test BlockIterator with diagonal direction and edge start position.
     * This tests the specific case mentioned in issue #701:
     * start=(10.0,64.0,10.25), direction=(0.1,-0.1,1)
     */
    @Test
    public void testDiagonalWithEdgeStart() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.0, 64.0, 10.25);
            Vector3 direction = new Vector3(0.1, -0.1, 1);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle diagonal direction with edge start position");
    }

    /**
     * Test that the iterator preserves the immediate next voxel after re-inserting
     * a start block that was missed by the initial scan.
     */
    @Test
    public void testDiagonalWithEdgeStartSequence() {
        Vector3 start = new Vector3(10.0, 64.0, 10.25);
        Vector3 direction = new Vector3(0.1, -0.1, 1);

        BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);

        Assertions.assertTrue(iterator.hasNext());
        assertBlockPosition(iterator.next(), 10, 64, 10);
        Assertions.assertTrue(iterator.hasNext());
        assertBlockPosition(iterator.next(), 10, 63, 10);
        Assertions.assertTrue(iterator.hasNext());
        assertBlockPosition(iterator.next(), 10, 63, 11);
    }

    /**
     * Test BlockIterator with very small direction vector (near-zero).
     */
    @Test
    public void testSmallDirectionVector() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(0.001, 0.001, 0.001).normalize();
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 5);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle small direction vectors");
    }

    /**
     * Test BlockIterator with negative coordinates.
     */
    @Test
    public void testNegativeCoordinates() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(-10.5, 64.5, -10.5);
            Vector3 direction = new Vector3(-1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle negative coordinates");
    }

    /**
     * Test BlockIterator with yOffset parameter.
     */
    @Test
    public void testYOffset() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.0, 10.5);
            Vector3 direction = new Vector3(0, 1, 0);
            double yOffset = 0.5;
            
            BlockIterator iterator = new BlockIterator(level, start, direction, yOffset, 10);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle yOffset parameter");
    }

    /**
     * Test BlockIterator with very short max distance.
     */
    @Test
    public void testShortMaxDistance() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 1);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle short max distance");
    }

    /**
     * Test that BlockIterator properly implements Iterator interface.
     */
    @Test
    public void testIteratorInterface() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 3);
            
            Assertions.assertTrue(iterator.hasNext());
            Assertions.assertTrue(iterator.hasNext());
            
            iterator.next();
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should implement Iterator interface correctly");
    }

    /**
     * Test that remove() throws UnsupportedOperationException.
     */
    @Test
    public void testRemoveThrowsException() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 3);
            iterator.next();
            
            Assertions.assertThrows(UnsupportedOperationException.class, () -> {
                iterator.remove();
            }, "remove() should throw UnsupportedOperationException");
        });
    }

    /**
     * Test BlockIterator with zero maxDistance (no limit).
     */
    @Test
    public void testNoDistanceLimit() {
        Assertions.assertDoesNotThrow(() -> {
            Vector3 start = new Vector3(10.5, 64.5, 10.5);
            Vector3 direction = new Vector3(1, 0, 0);
            
            BlockIterator iterator = new BlockIterator(level, start, direction, 0, 0);
            Assertions.assertTrue(iterator.hasNext());
        }, "BlockIterator should handle zero maxDistance (no limit)");
    }

    /**
     * Test BlockIterator with all eight octant directions from corner.
     */
    @Test
    public void testAllOctantDirections() {
        Vector3 start = new Vector3(10.0, 64.0, 10.0);
        
        // All 8 octant directions
        Vector3[] directions = {
            new Vector3(1, 1, 1),
            new Vector3(1, 1, -1),
            new Vector3(1, -1, 1),
            new Vector3(1, -1, -1),
            new Vector3(-1, 1, 1),
            new Vector3(-1, 1, -1),
            new Vector3(-1, -1, 1),
            new Vector3(-1, -1, -1)
        };
        
        for (Vector3 dir : directions) {
            Vector3 normalizedDir = dir.normalize();
            Assertions.assertDoesNotThrow(() -> {
                BlockIterator iterator = new BlockIterator(level, start, normalizedDir, 0, 5);
                Assertions.assertTrue(iterator.hasNext(), 
                    "Should have blocks for direction " + dir);
            }, "BlockIterator should handle direction " + dir);
        }
    }

    private static void assertBlockPosition(Vector3 block, int x, int y, int z) {
        Assertions.assertEquals(x, block.getFloorX());
        Assertions.assertEquals(y, block.getFloorY());
        Assertions.assertEquals(z, block.getFloorZ());
    }
}
