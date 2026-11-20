package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockTripWireTest {

    @Test
    void redstoneCallbackCannotReplaceStoneWithTheCachedWire() {
        HookStateFixture f = new HookStateFixture(BlockFace.EAST, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockStone()));
        f.calculate(false, -1, null);
        assertInstanceOf(BlockStone.class, f.at(f.wirePosition));
        assertEquals(0, f.wireWrites);
    }

    @Test
    void redstoneCallbackCannotResurrectRemovedWire() {
        HookStateFixture f = new HookStateFixture(BlockFace.NORTH, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockAir()));
        f.calculate(false, -1, null);
        assertInstanceOf(BlockAir.class, f.at(f.wirePosition));
        assertEquals(0, f.wireWrites);
    }

    @Test
    void attachmentPreservesLiveDisarmedPowerAndConnectionBits() {
        HookStateFixture f = new HookStateFixture(BlockFace.SOUTH, false);
        int liveMeta = 8 | BlockTripWire.FLAG_CONNECTION_WEST;
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockTripWire(liveMeta)));
        f.calculate(false, -1, null);
        assertEquals(liveMeta | 4, f.at(f.wirePosition).getDamage());
        assertEquals(1, f.wireWrites);
    }

    @Test
    void nonWireChangeArgumentCannotBeWrittenIntoALiveWireCell() {
        HookStateFixture f = new HookStateFixture(BlockFace.WEST, false);
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
            HookStateFixture f = new HookStateFixture(face, false);
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
        HookStateFixture f = new HookStateFixture(BlockFace.EAST, true);
        int originalMeta = f.at(f.wirePosition).getDamage();
        f.calculate(true, -1, null);
        assertEquals(originalMeta & ~4, f.at(f.wirePosition).getDamage());
        assertFalse(((BlockTripWireHook) f.at(f.peerPosition)).isAttached());
        assertEquals(1, f.wireWrites);
    }


    @ParameterizedTest
    @ValueSource(ints = {0, 9})
    void changedLivePowerOrDisarmedStateSchedulesBothHooksAndTheirNextTickCorrectsPower(int liveMeta) {
        HookStateFixture f = new HookStateFixture(BlockFace.SOUTH, false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockTripWire(liveMeta)));
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
        assertEquals((liveMeta & 1) != 0, ((BlockTripWire) f.at(f.wirePosition)).isPowered());
    }

    @Test
    void unchangedLivePowerDoesNotCreateExtraScheduledHookWork() {
        HookStateFixture f = new HookStateFixture(BlockFace.EAST, false);
        f.calculate(false, -1, null);
        verify(f.level, never()).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {4, 13})
    void scheduledConnectedHookReconcilesCallbackPowerOrDisarmedChange(int liveMeta) {
        HookStateFixture f = new HookStateFixture(BlockFace.SOUTH, true);
        f.hook.setPowered(false);
        f.put(f.hook, f.hook);
        ((BlockTripWireHook) f.at(f.peerPosition)).setPowered(false);
        f.onPowerChange(() -> f.put(f.wirePosition, new BlockTripWire(liveMeta)));
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
            f.hook.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        }
        verify(f.level).scheduleUpdate(any(Block.class), eq(f.hook), eq(10));
        verify(f.level).scheduleUpdate(any(Block.class), eq(f.peerPosition), eq(10));
        BlockTripWireHook current = (BlockTripWireHook) f.at(f.hook);
        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
            current.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        }
        assertFalse(((BlockTripWireHook) f.at(f.hook)).isPowered());
        assertFalse(((BlockTripWireHook) f.at(f.peerPosition)).isPowered());
        assertEquals(liveMeta, f.at(f.wirePosition).getDamage());
    }

    @Test
    void ordinaryConnectedPowerChangeDoesNotRewriteTheWireOrAddHookWork() {
        HookStateFixture f = new HookStateFixture(BlockFace.SOUTH, true);
        f.hook.setPowered(false);
        f.put(f.hook, f.hook);
        ((BlockTripWireHook) f.at(f.peerPosition)).setPowered(false);
        f.calculate(false, -1, null);
        assertTrue(((BlockTripWireHook) f.at(f.hook)).isPowered());
        assertTrue(((BlockTripWireHook) f.at(f.peerPosition)).isPowered());
        assertEquals(0, f.wireWrites);
        verify(f.level, never()).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.TRIPWIRE, Block.AIR, Block.STONE})
    void connectedScheduledCallbacksConvergeAfterFiniteChanges(int replacementId) {
        ScheduledCallbackFixture f = new ScheduledCallbackFixture();
        Entity occupant = mock(Entity.class);
        when(occupant.doesTriggerPressurePlate()).thenReturn(true);
        when(f.level.getCollidingEntities(any(AxisAlignedBB.class))).thenReturn(new Entity[]{occupant});
        AtomicInteger stage = new AtomicInteger();
        doAnswer(invocation -> {
            BlockRedstoneEvent event = invocation.getArgument(0);
            if (stage.get() == 0 && event.getNewPower() == 15) {
                f.put(f.wirePos, new BlockTripWire(4));
                stage.set(1);
            } else if (stage.get() == 1 && event.getNewPower() == 0) {
                Block replacement = switch (replacementId) {
                    case Block.TRIPWIRE -> new BlockTripWire(5);
                    case Block.AIR -> new BlockAir();
                    default -> new BlockStone();
                };
                f.put(f.wirePos, replacement);
                stage.set(2);
            }
            return null;
        }).when(f.manager).callEvent(any(BlockRedstoneEvent.class));

        try (MockedStatic<Block> blocks = mockStatic(Block.class, CALLS_REAL_METHODS)) {
            blocks.when(() -> Block.get(Block.TRIPWIRE_HOOK)).thenAnswer(i -> new BlockTripWireHook());
            // Begin consistent: connected hooks and wire are unpowered, with no pending updates.
            assertEquals(0, f.scheduler.getPendingCount());
            assertFalse(((BlockTripWireHook) f.at(f.hookPos)).isPowered());
            assertFalse(((BlockTripWire) f.at(f.wirePos)).isPowered());
            ((BlockTripWire) f.at(f.wirePos)).onEntityCollide(occupant);
            assertEquals(1, stage.get());
            assertTrue(((BlockTripWireHook) f.at(f.hookPos)).isPowered());
            assertFalse(((BlockTripWire) f.at(f.wirePos)).isPowered());
            assertTrue(f.scheduler.getPendingCount() > 0);

            f.advanceTo(10);
            assertEquals(2, stage.get(), "the real queued hook invokes the OFF callback");
            assertEquals(replacementId, f.at(f.wirePos).getId());
            for (int tick = 20; tick <= 50; tick += 10) f.advanceTo(tick);

            assertEquals(replacementId, f.at(f.wirePos).getId(), "keep the callback-owned block");
            boolean connectedAndPowered = replacementId == Block.TRIPWIRE;
            for (Vector3 position : new Vector3[]{f.hookPos, f.peerPos}) {
                BlockTripWireHook hook = (BlockTripWireHook) f.at(position);
                assertEquals(connectedAndPowered, hook.isAttached());
                assertEquals(connectedAndPowered, hook.isPowered(),
                        "hooks converge after finite callbacks even while the occupant remains");
            }
            if (connectedAndPowered) assertTrue(((BlockTripWire) f.at(f.wirePos)).isPowered());
        }
    }

    private static final class HookStateFixture {
        final Level level = mock(Level.class);
        final PluginManager manager = mock(PluginManager.class);
        final BlockTripWireHook hook = new BlockTripWireHook();
        final Map<String, Block> world = new HashMap<>();
        final Vector3 wirePosition;
        final Vector3 peerPosition;
        int wireWrites;

        HookStateFixture(BlockFace face, boolean attached) {
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

    private static final class ScheduledCallbackFixture {
        final Level level = mock(Level.class);
        final PluginManager manager = mock(PluginManager.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        final Map<String, Block> world = new HashMap<>();
        final Vector3 hookPos = new Vector3(0, 64, 0);
        final Vector3 wirePos = new Vector3(1, 64, 0);
        final Vector3 peerPos = new Vector3(2, 64, 0);
        long tick;

        ScheduledCallbackFixture() {
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
            when(level.getBlock(any(Vector3.class))).thenAnswer(i -> at(i.getArgument(0)));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(i -> at(i.getArgument(0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(new Vector3((int) i.getArgument(0), (int) i.getArgument(1), (int) i.getArgument(2))));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> {
                        put(i.getArgument(0), i.getArgument(1));
                        return true;
                    });
            doAnswer(i -> {
                schedule(i.getArgument(0), i.getArgument(0), i.getArgument(1));
                return null;
            }).when(level).scheduleUpdate(any(Block.class), anyInt());
            doAnswer(i -> {
                schedule(i.getArgument(0), i.getArgument(1), i.getArgument(2));
                return null;
            }).when(level).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());

            BlockTripWireHook hook = new BlockTripWireHook();
            hook.setFace(BlockFace.EAST);
            hook.setAttached(true);
            put(hookPos, hook);
            BlockTripWireHook peer = new BlockTripWireHook();
            peer.setFace(BlockFace.WEST);
            peer.setAttached(true);
            put(peerPos, peer);
            put(wirePos, new BlockTripWire(4));
        }

        void advanceTo(long currentTick) {
            tick = currentTick;
            scheduler.tick(currentTick);
        }

        void schedule(Block block, Vector3 position, int delay) {
            var entry = new BlockUpdateEntry(position.floor(), block, tick + delay, 0);
            if (!scheduler.contains(entry)) scheduler.add(entry);
        }

        Block at(Vector3 position) {
            Block block = world.getOrDefault(key(position), new BlockAir()).clone();
            block.x = position.getFloorX();
            block.y = position.getFloorY();
            block.z = position.getFloorZ();
            block.level = level;
            return block;
        }

        void put(Vector3 position, Block block) {
            Block copy = block.clone();
            copy.x = position.getFloorX();
            copy.y = position.getFloorY();
            copy.z = position.getFloorZ();
            copy.level = level;
            world.put(key(position), copy);
        }

        String key(Vector3 position) {
            return position.getFloorX() + ":" + position.getFloorY() + ":" + position.getFloorZ();
        }
    }
}
