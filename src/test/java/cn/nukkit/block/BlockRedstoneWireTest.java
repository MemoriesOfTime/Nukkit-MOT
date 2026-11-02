package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.block.BlockRedstoneEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.redstone.RedstoneUpdateEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Position;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
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

    @Nested
    class UpdateDepth {
        @Test void depthLimitDefersOneRequiredRecompute() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            assertEquals(1, f.scheduler.getPendingCount());
            assertTrue(f.level.isUpdateScheduled(f.wire, f.wire));
            verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
            verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        }
        @Test void repeatedDepthLimitNotificationsKeepOneFutureTask() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(17, () -> { for (int i=0; i<100; i++) f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE); });
            assertEquals(1, f.scheduler.getPendingCount());
        }
        @Test void existingFutureDeadlineIsNotPulledForward() throws Exception {
            Fixture f = new Fixture();
            f.level.scheduleUpdate(f.wire, f.wire, 7);
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            assertEquals(1, f.scheduler.getPendingCount());
            f.tick(1);
            assertEquals(1, f.scheduler.getPendingCount());
            verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
        }
        @Test void retryReadsFreshWireAndCurrentPower() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            BlockRedstoneWire fresh = new BlockRedstoneWire();
            fresh.position(new Position(0,64,0,f.level)); f.current = fresh;
            f.signal = 7;
            f.tick(1);
            assertEquals(7, fresh.getDamage());
            assertEquals(0, f.wire.getDamage(), "The queued old object must not write its old state");
        }
        @Test void activeRetryAtDepthLimitLeavesNextTickContinuation() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            f.atDepth(16, () -> {
                try { f.tick(1); } catch (Exception e) { throw new RuntimeException(e); }
            });
            assertEquals(1, f.scheduler.getPendingCount(), "An active entry must not suppress next-tick work");
            f.signal = 7; f.tick(2);
            assertEquals(7, f.current.getDamage());
        }
        @Test void removedCellIsNotRecreatedByRetry() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            f.current = new BlockAir(); f.tick(1);
            assertEquals(0, f.scheduler.getPendingCount());
            verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
            verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        }
        @Test void replacedCellIsNotChangedByRetry() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(16, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            f.current = new BlockStone(); f.tick(1);
            assertEquals(0, f.scheduler.getPendingCount());
            verify(f.powerBlock, never()).getWeakPower(any(BlockFace.class));
            verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
        }
        @Test void removedSourceDoesNotQueueItself() throws Exception {
            Fixture f = new Fixture();
            Method calculation = BlockRedstoneWire.class.getDeclaredMethod("calculateCurrentChanges", boolean.class, boolean.class);
            calculation.setAccessible(true);
            f.atDepth(16, () -> { try { calculation.invoke(f.wire,false,false); } catch (Exception e) { throw new RuntimeException(e); } });
            assertEquals(0, f.scheduler.getPendingCount());
        }
        @Test void belowLimitKeepsNormalCalculationAndRestoresDepth() throws Exception {
            Fixture f = new Fixture();
            f.atDepth(15, () -> f.wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            verify(f.powerBlock).getWeakPower(any(BlockFace.class));
            assertEquals(0, f.scheduler.getPendingCount());
        }
        static final class Fixture {
            final Level level = mock(Level.class);
            final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level,0);
            final BlockRedstoneWire wire = new BlockRedstoneWire();
            Block current = wire;
            final Block powerBlock = mock(Block.class);
            int signal;
            Fixture() throws Exception {
                Field queue=Level.class.getDeclaredField("updateQueue"); queue.setAccessible(true); queue.set(level,scheduler);
                Server server=mock(Server.class);
                when(level.getServer()).thenReturn(server);
                when(level.isChunkLoaded(anyInt(),anyInt())).thenReturn(true);
                when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenReturn(true);
                when(level.getMinBlockY()).thenReturn(-64); when(level.getMaxBlockY()).thenReturn(319);
                doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt());
                doCallRealMethod().when(level).scheduleUpdate(any(Block.class),any(Vector3.class),anyInt(),anyInt(),anyBoolean());
                doCallRealMethod().when(level).isUpdateScheduled(any(Vector3.class),any(Block.class));
                when(powerBlock.getId()).thenReturn(BlockID.REDSTONE_BLOCK);
                when(powerBlock.getWeakPower(any(BlockFace.class))).thenAnswer(i -> signal);
                when(level.getBlock(any(Vector3.class))).thenAnswer(i -> {
                    Vector3 pos=i.getArgument(0);
                    return pos.getFloorX()==0 && pos.getFloorY()==65 && pos.getFloorZ()==0 ? powerBlock : new BlockAir();
                });
                when(level.getBlock(any(Vector3.class),anyInt())).thenAnswer(i -> current);
                wire.position(new Position(0,64,0,level));
            }
            @SuppressWarnings("unchecked") void atDepth(int value,Runnable action) throws Exception {
                Field field=BlockRedstoneWire.class.getDeclaredField("UPDATE_DEPTH"); field.setAccessible(true);
                ThreadLocal<Integer> depth=(ThreadLocal<Integer>)field.get(null); int previous=depth.get(); depth.set(value);
                try { action.run(); assertEquals(value,depth.get()); } finally { depth.set(previous); }
            }
            void tick(int value) throws Exception {
                Field field=Level.class.getDeclaredField("levelCurrentTick"); field.setAccessible(true); field.set(level,(long)value);
                scheduler.tick(value);
            }
        }
    }

    @Nested
    class EventGuard {
        private final Level level = mock(Level.class);
        private final BlockRedstoneWire wire = new BlockRedstoneWire();
        private final AtomicInteger suppliedPower = new AtomicInteger(15);
        private final RecordingManager manager = new RecordingManager();
        private final PluginBase plugin = new PluginBase() {};
        private final List<Runnable> cleanup = new ArrayList<>();

        @BeforeEach
        void setUp() {
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(manager);
            Block source = mock(Block.class);
            when(source.getId()).thenReturn(Block.REDSTONE_BLOCK);
            when(source.getWeakPower(any(BlockFace.class))).thenAnswer(ignored -> suppliedPower.get());
            when(level.getBlock(any(Vector3.class))).thenReturn(source);
            wire.level = level;
            wire.y = 64;
            plugin.setEnabled(true);
        }

        @AfterEach
        void unregisterOwnListeners() {
            cleanup.forEach(Runnable::run);
        }

        @Test
        void noListenersStillChangesPowerAndPropagatesToAllSevenPositions() {
            assertEquals(Level.BLOCK_UPDATE_REDSTONE, wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            assertTrue(manager.events.isEmpty());
            assertEquals(15, wire.getDamage());
            verify(level).setBlock(wire, wire, false, false);
            verify(level, times(7)).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
            verify(level).scheduleUpdate(wire, wire, 2);
        }

        @Test
        void cancellationStillStopsCalculationWritesAndPropagation() {
            listen(RedstoneUpdateEvent.getHandlers(), event -> event.setCancelled());
            listen(BlockRedstoneEvent.getHandlers(), event -> fail("Cancelled update changed power"));
            assertEquals(0, wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            assertEquals(0, wire.getDamage());
            assertEquals(1, manager.events.size());
            assertInstanceOf(RedstoneUpdateEvent.class, manager.events.get(0));
            verify(level, never()).getBlock(any(Vector3.class));
            verify(level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
            verify(level, never()).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
            verify(level, never()).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
        }

        @Test
        void listenersKeepEventOrderAndImmutableComputedPowerSemantics() {
            List<String> order = new ArrayList<>();
            listen(RedstoneUpdateEvent.getHandlers(), event -> {
                assertEquals(0, wire.getDamage());
                order.add("update");
            });
            listen(BlockRedstoneEvent.getHandlers(), event -> {
                BlockRedstoneEvent change = (BlockRedstoneEvent) event;
                order.add("power");
                assertSame(wire, change.getBlock());
                assertEquals(0, change.getOldPower());
                assertEquals(15, change.getNewPower());
                assertEquals(0, change.getBlock().getDamage());
                // This event has no mutable newPower property. The original write follows dispatch.
                change.getBlock().setDamage(4);
            });
            assertEquals(Level.BLOCK_UPDATE_REDSTONE, wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE));
            assertEquals(List.of("update", "power"), order);
            assertEquals(15, wire.getDamage());
            verify(level).setBlock(wire, wire, false, false);
            verify(level, times(7)).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
        }

        @Test
        void updateListenerCanChangeThePowerSourceBeforeCalculation() {
            listen(RedstoneUpdateEvent.getHandlers(), event -> suppliedPower.set(7));
            List<Integer> powers = new ArrayList<>();
            listen(BlockRedstoneEvent.getHandlers(), event -> powers.add(((BlockRedstoneEvent) event).getNewPower()));
            wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
            assertEquals(List.of(7), powers);
            assertEquals(7, wire.getDamage());
        }

        @Test
        void powerListenerRegisteredInUpdateReceivesCurrentChange() {
            List<Integer> powers = new ArrayList<>();
            listen(RedstoneUpdateEvent.getHandlers(), event ->
                    listen(BlockRedstoneEvent.getHandlers(), change -> powers.add(((BlockRedstoneEvent) change).getNewPower())));
            wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
            assertEquals(List.of(15), powers);
        }

        @Test
        void powerListenerRemovedInUpdateIsNotDispatched() {
            RegisteredListener power = listen(BlockRedstoneEvent.getHandlers(), event -> fail("Removed power listener"));
            listen(RedstoneUpdateEvent.getHandlers(), event -> BlockRedstoneEvent.getHandlers().unregister(power));
            wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
            assertEquals(1, manager.events.size());
            assertInstanceOf(RedstoneUpdateEvent.class, manager.events.get(0));
            assertEquals(15, wire.getDamage());
        }

        @Test
        void parentBlockUpdateSubscriptionDoesNotPopulateTheSeparateRedstoneList() {
            listen(BlockUpdateEvent.getHandlers(), event -> {
                event.setCancelled();
                fail("RedstoneUpdateEvent owns its own HandlerList");
            });
            wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
            assertTrue(manager.events.isEmpty());
            assertEquals(15, wire.getDamage());
        }

        @Test
        void scheduledUpdateStillSkipsRedstoneUpdateButNotPowerChange() {
            listen(RedstoneUpdateEvent.getHandlers(), event -> fail("Scheduled updates did not fire this event"));
            listen(BlockRedstoneEvent.getHandlers(), event -> {});
            assertEquals(Level.BLOCK_UPDATE_SCHEDULED, wire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
            assertEquals(1, manager.events.size());
            assertInstanceOf(BlockRedstoneEvent.class, manager.events.get(0));
            assertEquals(15, wire.getDamage());
            verify(level).scheduleUpdate(wire, wire, 2);
        }

        @Test
        void unchangedPowerDoesNotCreatePowerEventOrPropagate() {
            wire.setDamage(15);
            listen(RedstoneUpdateEvent.getHandlers(), event -> {});
            listen(BlockRedstoneEvent.getHandlers(), event -> fail("Unchanged power"));
            wire.onUpdate(Level.BLOCK_UPDATE_REDSTONE);
            assertEquals(1, manager.events.size());
            assertInstanceOf(RedstoneUpdateEvent.class, manager.events.get(0));
            assertEquals(15, wire.getDamage());
            verify(level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean());
            verify(level, never()).updateAroundRedstone(any(Vector3.class), nullable(BlockFace.class));
            verify(level, never()).scheduleUpdate(any(Block.class), any(Vector3.class), anyInt());
        }

        private RegisteredListener listen(HandlerList handlers, Consumer<Event> action) {
            RegisteredListener listener = new RegisteredListener(new Listener() {},
                    (ignored, event) -> action.accept(event), EventPriority.NORMAL, plugin, false);
            handlers.register(listener);
            cleanup.add(() -> handlers.unregister(listener));
            return listener;
        }

        private static final class RecordingManager extends PluginManager {
            private final List<Event> events = new ArrayList<>();

            private RecordingManager() {
                super(null, null);
            }

            @Override
            public void callEvent(Event event) {
                events.add(event);
                super.callEvent(event);
            }
        }
    }

    @Nested
    class PowerTraversal {
        static Stream<Vector3> origins() {
            return Stream.of(new Vector3(0,64,0), new Vector3(-0.1,64,-0.1), new Vector3(15.999,64,16),
                    new Vector3(-16,64,-16.1), new Vector3(2147483647.75,64,-2147483648.25),
                    new Vector3(-2147483648.25,64,2147483647.75), new Vector3(2,-64.25,3),
                    new Vector3(2,319.75,3));
        }

        @ParameterizedTest @MethodSource("origins")
        void retainsFreshPositionArgumentsAndLegacyFloorCoordinates(Vector3 origin) throws Exception {
            Fixture f = new Fixture(); f.wire.x=origin.x; f.wire.y=origin.y; f.wire.z=origin.z;
            assertEquals(0, power(f.wire));
            assertEquals(6,f.reads.size());
            Location snapshot = new Location(origin.x,origin.y,origin.z,f.level);
            Set<Vector3> identities = Collections.newSetFromMap(new IdentityHashMap<>());
            for (int i=0; i<6; i++) {
                Vector3 actual = f.reads.get(i), expected = snapshot.getSide(BlockFace.values()[i]);
                assertEquals(Position.class,actual.getClass()); assertSame(f.level,((Position)actual).getLevel());
                assertEquals(expected.x,actual.x); assertEquals(expected.y,actual.y); assertEquals(expected.z,actual.z);
                assertEquals(expected.getFloorX(),actual.getFloorX()); assertEquals(expected.getFloorY(),actual.getFloorY());
                assertEquals(expected.getFloorZ(),actual.getFloorZ()); assertTrue(identities.add(actual),"Do not reuse retained lookup arguments");
            }
            verify(f.level,never()).getBlock(anyInt(),anyInt(),anyInt());
        }

        @ParameterizedTest @EnumSource(BlockFace.class)
        void keepsWeakPowerFaceOrderAndFirstFifteenExit(BlockFace powered) throws Exception {
            Fixture f = new Fixture(); Block source = mock(Block.class);
            when(source.getWeakPower(any(BlockFace.class))).thenAnswer(call -> call.getArgument(0)==powered ? 15 : 0);
            f.block = source;
            assertEquals(15,power(f.wire));
            assertEquals(powered.ordinal()+1,f.reads.size());
            var order = inOrder(source);
            for (int i=0;i<=powered.ordinal();i++) order.verify(source).getWeakPower(BlockFace.values()[i]);
            verify(source,times(powered.ordinal()+1)).getWeakPower(any(BlockFace.class));
        }

        @ParameterizedTest @EnumSource(BlockFace.class)
        void keepsAllSixStrongPowerDirectionsThroughNormalNeighbor(BlockFace powered) throws Exception {
            Fixture f = new Fixture(); Block normal = mock(Block.class), source = mock(Block.class);
            when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
            when(source.getStrongPower(any(BlockFace.class))).thenAnswer(call -> call.getArgument(0)==powered ? 14 : 0);
            when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 pos = call.getArgument(0); f.reads.add(pos);
                return pos.x==0 && pos.y==63 && pos.z==0 ? normal : source;
            });
            assertEquals(14,power(f.wire));
            var order = inOrder(source);
            for (BlockFace face : BlockFace.values()) order.verify(source).getStrongPower(face);
            verify(source,times(6)).getStrongPower(any(BlockFace.class));
        }

        @Test void adjacentWireStillProvidesNoIndirectPower() throws Exception {
            Fixture f = new Fixture(); Block wire = mock(Block.class);
            when(wire.getId()).thenReturn(BlockID.REDSTONE_WIRE); f.block=wire;
            assertEquals(0,power(f.wire)); assertEquals(6,f.reads.size());
            verify(wire,never()).isNormalBlock(); verify(wire,never()).getWeakPower(any()); verify(wire,never()).getStrongPower(any());
        }

        @Test void originSnapshotSurvivesNestedCalculationAndReceiverLevelChange() throws Exception {
            Fixture outer = new Fixture(), nested = new Fixture();
            outer.wire.position(new Position(2,64,3,outer.level)); nested.wire.position(new Position(100,70,200,nested.level));
            Block first = mock(Block.class), next = mock(Block.class);
            when(next.getWeakPower(any(BlockFace.class))).thenReturn(9); nested.block=next;
            when(first.getWeakPower(any(BlockFace.class))).thenAnswer(call -> {
                assertEquals(BlockFace.DOWN,call.getArgument(0));
                assertEquals(9,power(nested.wire));
                outer.wire.position(new Position(900,80,900,nested.level));
                return 7;
            });
            outer.block=first;
            assertEquals(9,power(outer.wire));
            assertEquals(1,outer.reads.size()); assertEquals(11,nested.reads.size());
            Location snapshot = new Location(2,64,3,outer.level);
            List<Vector3> outerReads = new ArrayList<>(outer.reads); outerReads.addAll(nested.reads.subList(6,11));
            for (int i=0;i<6;i++) {
                Vector3 expected=snapshot.getSide(BlockFace.values()[i]), actual=outerReads.get(i);
                assertEquals(expected.x,actual.x); assertEquals(expected.y,actual.y); assertEquals(expected.z,actual.z);
                assertSame(outer.level,((Position)actual).getLevel(),"Position context stays captured; lookup receiver is live");
            }
        }

        @Test void respectsCustomLocationSideOverrideAndVirtualOrigin() throws Exception {
            Fixture f = new Fixture(); List<BlockFace> sides = new ArrayList<>();
            Location custom = new Location(20.5,72,-30.5,f.level) {
                @Override public Position getSide(BlockFace face) {
                    sides.add(face); return new Position(20.5+3*face.getXOffset(),72+3*face.getYOffset(),-30.5+3*face.getZOffset(),f.level);
                }
            };
            BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return custom; } };
            wire.position(new Position(999,99,999,f.level));
            assertEquals(0,power(wire)); assertEquals(Arrays.asList(BlockFace.values()),sides);
            for (int i=0;i<6;i++) {
                BlockFace face=BlockFace.values()[i]; Vector3 pos=f.reads.get(i);
                assertEquals(20.5+3*face.getXOffset(),pos.x); assertEquals(72+3*face.getYOffset(),pos.y);
                assertEquals(-30.5+3*face.getZOffset(),pos.z);
            }
        }

        @Test void respectsCustomPositionSideOverrideDuringStrongPowerScan() throws Exception {
            Fixture f = new Fixture(); List<BlockFace> sides = new ArrayList<>();
            Position custom = new Position(0,63,0,f.level) {
                @Override public Position getSide(BlockFace face) {
                    sides.add(face); return new Position(2*face.getXOffset(),63+2*face.getYOffset(),2*face.getZOffset(),f.level);
                }
            };
            Location origin = new Location(0,64,0,f.level) {
                @Override public Position getSide(BlockFace face) { return face==BlockFace.DOWN ? custom : super.getSide(face); }
            };
            BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return origin; } };
            wire.position(new Position(0,64,0,f.level));
            Block normal=mock(Block.class); when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
            when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 pos=call.getArgument(0); f.reads.add(pos); return pos==custom ? normal : new BlockAir();
            });
            assertEquals(0,power(wire)); assertEquals(Arrays.asList(BlockFace.values()),sides);
            for (int i=0;i<6;i++) {
                Vector3 pos=f.reads.get(i+1); BlockFace face=BlockFace.values()[i];
                assertEquals(2*face.getXOffset(),pos.x); assertEquals(63+2*face.getYOffset(),pos.y); assertEquals(2*face.getZOffset(),pos.z);
            }
        }

        @Test void strongPowerScanAlsoExcludesAdjacentWire() throws Exception {
            Fixture f=new Fixture(); Block normal=mock(Block.class), wire=mock(Block.class);
            when(normal.getId()).thenReturn(BlockID.STONE); when(normal.isNormalBlock()).thenReturn(true);
            when(wire.getId()).thenReturn(BlockID.REDSTONE_WIRE);
            when(f.level.getBlock(any(Vector3.class))).thenAnswer(call -> {
                Vector3 pos=call.getArgument(0); f.reads.add(pos);
                return pos.x==0 && pos.y==63 && pos.z==0 ? normal : wire;
            });
            assertEquals(0,power(f.wire)); assertEquals(12,f.reads.size());
            verify(wire,never()).getStrongPower(any()); verify(wire,never()).getWeakPower(any());
        }

        @Test void invalidCapturedPositionStillFailsBeforeAnyLookup() {
            Fixture f = new Fixture();
            BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return new Location(0,64,0); } };
            wire.position(new Position(0,64,0,f.level));
            InvocationTargetException thrown = assertThrows(InvocationTargetException.class,() -> power(wire));
            assertInstanceOf(cn.nukkit.utils.LevelException.class,thrown.getCause());
            assertTrue(f.reads.isEmpty());
        }

        @Test void customSideFailureStillPropagatesWithoutLookup() {
            Fixture f = new Fixture();
            Location custom = new Location(0,64,0,f.level) { @Override public Position getSide(BlockFace face) { throw new IllegalArgumentException("custom side"); } };
            BlockRedstoneWire wire = new BlockRedstoneWire() { @Override public Location getLocation() { return custom; } };
            wire.position(new Position(0,64,0,f.level));
            InvocationTargetException thrown=assertThrows(InvocationTargetException.class,() -> power(wire));
            assertInstanceOf(IllegalArgumentException.class,thrown.getCause()); assertEquals("custom side",thrown.getCause().getMessage());
            assertTrue(f.reads.isEmpty());
        }

        private static int power(BlockRedstoneWire wire) throws Exception {
            Method method=BlockRedstoneWire.class.getDeclaredMethod("getIndirectPower"); method.setAccessible(true);
            return (int)method.invoke(wire);
        }

        private static final class Fixture {
            final Level level=mock(Level.class);
            final BlockRedstoneWire wire=new BlockRedstoneWire();
            final List<Vector3> reads=new ArrayList<>();
            Block block=new BlockAir();
            Fixture() {
                wire.position(new Position(0,64,0,level));
                when(level.getBlock(any(Vector3.class))).thenAnswer(call -> { Vector3 pos=call.getArgument(0); reads.add(pos); return block; });
            }
        }
    }
}
