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
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockRedstoneWireEventGuardTest {
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
