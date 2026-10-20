package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockTripWireHookLiveStateTest {
    @Test
    void redstoneCallbackCannotReplaceStoneWithTheCachedWire() {
        Fixture f = new Fixture(BlockFace.EAST, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockStone()));
        f.calculate(false, -1, null);
        assertInstanceOf(BlockStone.class, f.at(f.wirePosition));
        assertEquals(0, f.wireWrites);
    }

    @Test
    void redstoneCallbackCannotResurrectRemovedWire() {
        Fixture f = new Fixture(BlockFace.NORTH, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockAir()));
        f.calculate(false, -1, null);
        assertInstanceOf(BlockAir.class, f.at(f.wirePosition));
        assertEquals(0, f.wireWrites);
    }

    @Test
    void attachmentPreservesLiveDisarmedPowerAndConnectionBits() {
        Fixture f = new Fixture(BlockFace.SOUTH, false);
        int liveMeta = 8 | BlockTripWire.FLAG_CONNECTION_WEST;
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockTripWire(liveMeta)));
        f.calculate(false, -1, null);
        assertEquals(liveMeta | 4, f.at(f.wirePosition).getDamage());
        assertEquals(1, f.wireWrites);
    }

    @Test
    void nonWireChangeArgumentCannotBeWrittenIntoALiveWireCell() {
        Fixture f = new Fixture(BlockFace.WEST, false);
        int originalMeta = f.at(f.wirePosition).getDamage();
        f.calculate(false, 1, new BlockStone());
        assertInstanceOf(BlockTripWire.class, f.at(f.wirePosition));
        assertEquals(originalMeta, f.at(f.wirePosition).getDamage());
        assertEquals(0, f.wireWrites);
    }

    @Test
    void normalAttachmentKeepsPowerAndConnectionBitsInEveryDirection() {
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH,
                BlockFace.EAST, BlockFace.WEST}) {
            Fixture f = new Fixture(face, false);
            int originalMeta = f.at(f.wirePosition).getDamage();
            f.calculate(false, -1, null);
            assertEquals(originalMeta | 4, f.at(f.wirePosition).getDamage(), face.toString());
            assertTrue(((BlockTripWireHook) f.at(f.hook)).isAttached());
            assertTrue(((BlockTripWireHook) f.at(f.peerPosition)).isPowered());
            assertEquals(1, f.wireWrites);
            verify(f.manager, times(2)).callEvent(any(BlockRedstoneEvent.class));
        }
    }

    @Test
    void breakingHookDetachesLiveWireWithoutClearingOtherBits() {
        Fixture f = new Fixture(BlockFace.EAST, true);
        int originalMeta = f.at(f.wirePosition).getDamage();
        f.calculate(true, -1, null);
        assertEquals(originalMeta & ~4, f.at(f.wirePosition).getDamage());
        assertFalse(((BlockTripWireHook) f.at(f.peerPosition)).isAttached());
        assertEquals(1, f.wireWrites);
    }


    @Test
    void changedLivePowerSchedulesBothHooksAndTheirNextTickCorrectsPower() {
        Fixture f = new Fixture(BlockFace.SOUTH, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockTripWire(0)));
        f.calculate(false, -1, null);
        verify(f.level).scheduleUpdate(any(Block.class), eq(f.hook), eq(10));
        verify(f.level).scheduleUpdate(any(Block.class), eq(f.peerPosition), eq(10));
        BlockTripWireHook current = (BlockTripWireHook) f.at(f.hook);
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
            current.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        }
        assertFalse(((BlockTripWireHook) f.at(f.hook)).isPowered());
        assertFalse(((BlockTripWireHook) f.at(f.peerPosition)).isPowered());
        assertFalse(((BlockTripWire) f.at(f.wirePosition)).isPowered());
    }

    @Test
    void unchangedLivePowerDoesNotCreateExtraScheduledHookWork() {
        Fixture f = new Fixture(BlockFace.EAST, false);
        f.calculate(false, -1, null);
        verify(f.level, never()).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager manager = mock(PluginManager.class);
        final BlockTripWireHook hook = new BlockTripWireHook();
        final Map<String, Block> world = new HashMap<>();
        final Vector3 wirePosition;
        final Vector3 peerPosition;
        int wireWrites;

        Fixture(BlockFace face, boolean attached) {
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            hook.y = 64;
            hook.level = level;
            hook.setFace(face);
            hook.setAttached(attached);
            hook.setPowered(attached);
            wirePosition = hook.getLocation().getSide(face, 1);
            peerPosition = hook.getLocation().getSide(face, 2);
            BlockTripWireHook peer = new BlockTripWireHook();
            peer.setFace(face.getOpposite());
            peer.setAttached(attached);
            peer.setPowered(attached);
            put(hook, hook);
            put(peerPosition, peer);
            put(wirePosition, new BlockTripWire(1 | (attached ? 4 : 0)
                    | BlockTripWire.FLAG_CONNECTION_EAST | BlockTripWire.FLAG_CONNECTION_NORTH));
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> at(i.getArgument(0)).clone());
            when(level.getBlockIdAt(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(new Vector3((int) i.getArgument(0), (int) i.getArgument(1),
                            (int) i.getArgument(2))).getId());
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> {
                        Vector3 position = i.getArgument(0);
                        if (key(position).equals(key(wirePosition))) wireWrites++;
                        put(position, i.getArgument(1));
                        return true;
                    });
        }

        void calculate(boolean broken, int changedPosition, Block changedBlock) {
            // Only isolate registry bootstrap; calculateState and every block state are real.
            try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
                blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
                hook.calculateState(broken, true, changedPosition, changedBlock);
            }
        }

        void onPowerChange(Runnable callback) {
            AtomicBoolean first = new AtomicBoolean(true);
            doAnswer(i -> {
                if (first.getAndSet(false)) callback.run();
                return null;
            }).when(manager).callEvent(any(BlockRedstoneEvent.class));
        }

        Block at(Vector3 position) {
            return world.getOrDefault(key(position), new BlockAir());
        }

        void put(Vector3 position, Block block) {
            Block copy = block.clone();
            copy.x = position.getFloorX();
            copy.y = position.getFloorY();
            copy.z = position.getFloorZ();
            copy.level = level;
            world.put(key(position), copy);
        }

        static String key(Vector3 position) {
            return position.getFloorX() + ":" + position.getFloorY() + ":" + position.getFloorZ();
        }
    }
}
