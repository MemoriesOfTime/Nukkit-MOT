package cn.nukkit.block;

import cn.nukkit.math.BlockFace;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BlockChestTest {

    @Test
    void legacyChestMetaDecodesToFacingDirection() {
        assertEquals(BlockFace.NORTH, new BlockChest(0).getBlockFace());
        assertEquals(BlockFace.NORTH, new BlockChest(2).getBlockFace());
        assertEquals(BlockFace.SOUTH, new BlockChest(3).getBlockFace());
        assertEquals(BlockFace.WEST, new BlockChest(4).getBlockFace());
        assertEquals(BlockFace.EAST, new BlockChest(5).getBlockFace());
    }

    @Test
    void chestVariantsShareLegacyMetaFacing() {
        assertEquals(BlockFace.SOUTH, new BlockTrappedChest(3).getBlockFace());
        assertEquals(BlockFace.WEST, new BlockChestCopper(4).getBlockFace());
        assertEquals(BlockFace.EAST, new BlockChestCopper(5).getBlockFace());
    }

    @Test
    void legacyChestMetaPairsOnPerpendicularAxis() {
        assertPairingAxis(BlockFace.Axis.X, new BlockChest(2));
        assertPairingAxis(BlockFace.Axis.X, new BlockChest(3));
        assertPairingAxis(BlockFace.Axis.Z, new BlockChest(4));
        assertPairingAxis(BlockFace.Axis.Z, new BlockChest(5));
    }

    @Test
    void chestPairsOnlyOnPerpendicularAxis() {
        BlockChest north = new BlockChest(2);
        assertTrue(north.canPairWithSide(new BlockChest(2), BlockFace.EAST));
        assertTrue(north.canPairWithSide(new BlockChest(2), BlockFace.WEST));
        assertFalse(north.canPairWithSide(new BlockChest(2), BlockFace.NORTH));
        assertFalse(north.canPairWithSide(new BlockChest(2), BlockFace.SOUTH));

        BlockChest west = new BlockChest(4);
        assertTrue(west.canPairWithSide(new BlockChest(4), BlockFace.NORTH));
        assertTrue(west.canPairWithSide(new BlockChest(4), BlockFace.SOUTH));
        assertFalse(west.canPairWithSide(new BlockChest(4), BlockFace.WEST));
        assertFalse(west.canPairWithSide(new BlockChest(4), BlockFace.EAST));
    }

    @Test
    void chestPairingRejectsDifferentFacingAndIncompatibleVariants() {
        BlockChest north = new BlockChest(2);
        assertFalse(north.canPairWithSide(new BlockChest(3), BlockFace.EAST));
        assertFalse(north.canPairWithSide(new BlockTrappedChest(2), BlockFace.EAST));
        assertFalse(north.canPairWithSide(new BlockChestCopper(2), BlockFace.EAST));

        assertTrue(new BlockTrappedChest(2).canPairWithSide(new BlockTrappedChest(2), BlockFace.EAST));
        assertTrue(new BlockChestCopper(2).canPairWithSide(new BlockChestCopperExposed(2), BlockFace.EAST));
    }

    private static void assertPairingAxis(BlockFace.Axis expectedAxis, BlockChest chest) {
        BlockFace facing = chest.getBlockFace();

        assertEquals(expectedAxis, facing.rotateY().getAxis());
        assertEquals(expectedAxis, facing.rotateYCCW().getAxis());
    }
}
