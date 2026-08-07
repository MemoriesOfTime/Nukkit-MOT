package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockRedstoneWireTest {

    @Test
    void singleConnectionWirePowersTowardAndAwayFromConnection() {
        Level level = mock(Level.class);
        BlockRedstoneWire wire = place(new BlockRedstoneWire(15), level, 0, 64, 0);
        BlockRedstoneWire eastWire = place(new BlockRedstoneWire(14), level, 1, 64, 0);
        Map<String, Block> blocks = blocks(wire, eastWire);

        stubBlocks(level, blocks);

        assertEquals(15, wire.getWeakPower(BlockFace.EAST));
        assertEquals(15, wire.getWeakPower(BlockFace.WEST));
    }

    @Test
    void multiConnectionWireOnlyPowersConnectedSides() {
        Level level = mock(Level.class);
        BlockRedstoneWire wire = place(new BlockRedstoneWire(15), level, 0, 64, 0);
        BlockRedstoneWire eastWire = place(new BlockRedstoneWire(14), level, 1, 64, 0);
        BlockRedstoneWire northWire = place(new BlockRedstoneWire(14), level, 0, 64, -1);
        Map<String, Block> blocks = blocks(wire, eastWire, northWire);

        stubBlocks(level, blocks);

        assertEquals(15, wire.getWeakPower(BlockFace.WEST));
        assertEquals(15, wire.getWeakPower(BlockFace.SOUTH));
        assertEquals(0, wire.getWeakPower(BlockFace.EAST));
    }

    @Test
    void diagonalWireDoesNotPowerTargetThroughAdjacentWireWhenNotConnectedTowardTarget() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        Block target = place(Block.get(Block.STONE), level, 8, 64, 8);
        BlockRedstoneWire eastWire = place(new BlockRedstoneWire(15), level, 9, 64, 8);
        BlockRedstoneWire diagonalWire = place(new BlockRedstoneWire(14), level, 9, 64, 7);
        Map<String, Block> blocks = blocks(target, eastWire, diagonalWire);

        stubBlocks(level, blocks);

        assertEquals(0, level.getRedstonePower(target.getSideVec(BlockFace.EAST), BlockFace.EAST));
        assertFalse(level.isBlockPowered(target));
    }

    @Test
    void parallelWirePowersAdjacentDoorButNotOrdinaryBlock() {
        Level level = mock(Level.class, CALLS_REAL_METHODS);
        BlockDoorWood door = place(new BlockDoorWood(), level, 8, 64, 8);
        BlockDoorWood doorTop = place(new BlockDoorWood(BlockDoor.DOOR_TOP_BIT), level, 8, 65, 8);
        BlockRedstoneWire eastWire = place(new BlockRedstoneWire(15), level, 9, 64, 8);
        BlockRedstoneWire northWire = place(new BlockRedstoneWire(14), level, 9, 64, 7);
        BlockRedstoneWire southWire = place(new BlockRedstoneWire(14), level, 9, 64, 9);
        Map<String, Block> blocks = blocks(door, doorTop, eastWire, northWire, southWire);

        stubBlocks(level, blocks);

        assertFalse(level.isBlockPowered(door));
        assertTrue(door.isGettingPower());
    }

    private static void stubBlocks(Level level, Map<String, Block> blocks) {
        doAnswer(invocation -> blockAt(level, blocks, invocation.getArgument(0)))
                .when(level).getBlock(any(Vector3.class));
        doAnswer(invocation -> blockAt(level, blocks, invocation.getArgument(0)))
                .when(level).getBlock(any(Vector3.class), anyBoolean());
        doAnswer(invocation -> blockAt(
                level,
                blocks,
                vectorFromInvocation(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))
        )).when(level).getBlock(anyInt(), anyInt(), anyInt());
        doAnswer(invocation -> blockAt(
                level,
                blocks,
                vectorFromInvocation(invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2))
        )).when(level).getBlock(anyInt(), anyInt(), anyInt(), anyInt());
    }

    private static <T extends Block> T place(T block, Level level, int x, int y, int z) {
        block.level = level;
        block.x = x;
        block.y = y;
        block.z = z;
        return block;
    }

    private static Map<String, Block> blocks(Block... blocks) {
        Map<String, Block> result = new HashMap<>();
        for (Block block : blocks) {
            result.put(key(block.getFloorX(), block.getFloorY(), block.getFloorZ()), block);
        }
        return result;
    }

    private static Block blockAt(Level level, Map<String, Block> blocks, Vector3 pos) {
        int x = pos.getFloorX();
        int y = pos.getFloorY();
        int z = pos.getFloorZ();
        Block block = blocks.get(key(x, y, z));
        if (block != null) {
            return block;
        }
        return place(Block.get(Block.AIR), level, x, y, z);
    }

    private static Vector3 vectorFromInvocation(Object x, Object y, Object z) {
        return new Vector3(((Number) x).intValue(), ((Number) y).intValue(), ((Number) z).intValue());
    }

    private static String key(int x, int y, int z) {
        return x + ":" + y + ":" + z;
    }

}
