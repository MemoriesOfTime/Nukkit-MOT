package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class BlockItemFrameTest {

    @Test
    /**
     * Verifies item frames can encode every attachment face without losing the exposed face.
     */
    void blockFaceEncodingPreservesAllSixFaces() {
        assertFaceMeta(BlockFace.DOWN, 8);
        assertFaceMeta(BlockFace.UP, 9);
        assertFaceMeta(BlockFace.NORTH, 3);
        assertFaceMeta(BlockFace.SOUTH, 2);
        assertFaceMeta(BlockFace.WEST, 1);
        assertFaceMeta(BlockFace.EAST, 0);
    }

    @Test
    /**
     * Ensures the map bit only uses the wall-mounted states that exist in the runtime palette.
     */
    void storingMapKeepsWallAndVerticalFacingStable() {
        assertMapMeta(BlockFace.EAST, 4);
        assertMapMeta(BlockFace.WEST, 5);
        assertMapMeta(BlockFace.SOUTH, 6);
        assertMapMeta(BlockFace.NORTH, 7);
        assertMapMeta(BlockFace.DOWN, 8);
        assertMapMeta(BlockFace.UP, 9);
    }

    private static void assertFaceMeta(BlockFace face, int expectedMeta) {
        BlockItemFrame frame = new BlockItemFrame();

        frame.setBlockFace(face);

        Assertions.assertEquals(expectedMeta, frame.getDamage(), face.getName());
        Assertions.assertEquals(face, frame.getBlockFace(), face.getName());
    }

    private static void assertMapMeta(BlockFace face, int expectedMeta) {
        BlockItemFrame frame = new BlockItemFrame();

        frame.setBlockFace(face);
        frame.setStoringMap(true);

        Assertions.assertEquals(expectedMeta, frame.getDamage(), face.getName());
        Assertions.assertEquals(face, frame.getBlockFace(), face.getName());
    }
}
