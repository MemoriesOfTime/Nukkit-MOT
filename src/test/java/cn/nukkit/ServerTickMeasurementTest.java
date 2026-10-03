package cn.nukkit;

import cn.nukkit.command.SimpleCommandMap;
import cn.nukkit.event.Event;
import cn.nukkit.event.EventPriority;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.Listener;
import cn.nukkit.event.server.ServerTickEndEvent;
import cn.nukkit.event.server.ServerTickStartEvent;
import cn.nukkit.level.Level;
import cn.nukkit.network.Network;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.plugin.PluginLoader;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.plugin.RegisteredListener;
import cn.nukkit.scheduler.ServerScheduler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ServerTickMeasurementTest {
    private final Server server = mock(Server.class);
    private final Network network = mock(Network.class);
    private final ServerScheduler scheduler = mock(ServerScheduler.class);
    private final PluginManager manager = mock(PluginManager.class);
    private final AtomicLong nanos = new AtomicLong(1);
    private final List<Event> events = new ArrayList<>();

    @BeforeEach
    void prepareActualTickBody() throws Exception {
        set("network", network);
        set("scheduler", scheduler);
        set("pluginManager", manager);
        set("players", new HashMap<InetSocketAddress, Player>());
        set("levelArray", new Level[0]);
        set("tickAverage", new float[20]);
        set("useAverage", new float[20]);
        set("autoSaveTicks", Integer.MAX_VALUE);
        doCallRealMethod().when(server).tick(anyLong(), any());
        doAnswer(invocation -> {
            events.add(invocation.getArgument(0));
            return null;
        }).when(manager).callEvent(any());
    }

    @Test
    void twoMillisWorkAndFortyEightMillisWaitingRemainTwoMillisWork() {
        doAnswer(invocation -> {
            nanos.addAndGet(2_000_000);
            return null;
        }).when(network).processInterfaces();

        server.tick(0, nanos::get);
        nanos.addAndGet(48_000_000); // Waiting in tickProcessor, outside Server.tick.
        server.tick(50, nanos::get);

        assertEquals(4, events.size());
        assertEquals(2_000_000, end(1).getDurationNanos());
        assertEquals(2_000_000, end(3).getDurationNanos());
        assertEquals(1, ((ServerTickStartEvent) events.get(0)).getTickId());
        assertEquals(1, end(1).getTickId());
        assertEquals(2, end(3).getTickId());
    }

    @Test
    void includesWorkAfterSchedulerAndDoesNotClipAtFiftyMillis() throws Exception {
        Player player = mock(Player.class);
        set("players", new HashMap<>(java.util.Map.of(new InetSocketAddress(0), player)));
        set("autoSaveTicks", 1);
        doAnswer(invocation -> { nanos.addAndGet(2_000_000); return null; })
                .when(scheduler).mainThreadHeartbeat(1);
        doAnswer(invocation -> { nanos.addAndGet(30_000_000); return null; })
                .when(player).checkNetwork();
        doAnswer(invocation -> { nanos.addAndGet(43_000_000); return null; })
                .when(server).doAutoSave();

        server.tick(0, nanos::get);

        assertEquals(75_000_000, end(1).getDurationNanos());
        verify(scheduler).mainThreadHeartbeat(1);
        verify(player).checkNetwork();
        verify(server).doAutoSave();
    }

    @Test
    void earlyWakeupEmitsNeitherStartNorEnd() throws Exception {
        set("nextTick", 26L);

        server.tick(0, nanos::get);

        assertTrue(events.isEmpty());
        verifyNoInteractions(network, scheduler, manager);
        Field counter = Server.class.getDeclaredField("tickCounter");
        counter.setAccessible(true);
        assertEquals(0, counter.getInt(server));
    }

    @Test
    void bodyExceptionStillEmitsExactlyOneEndAndSurvivesEndObserverFailure() {
        RuntimeException bodyFailure = new IllegalStateException("tick body");
        Error observerFailure = new AssertionError("end observer");
        doAnswer(invocation -> {
            nanos.addAndGet(75_000_000);
            throw bodyFailure;
        }).when(scheduler).mainThreadHeartbeat(1);
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof ServerTickEndEvent) throw observerFailure;
            return null;
        }).when(manager).callEvent(any());

        assertSame(bodyFailure, assertThrows(RuntimeException.class, () -> server.tick(0, nanos::get)));
        assertEquals(2, events.size());
        assertEquals(((ServerTickStartEvent) events.get(0)).getTickId(), end(1).getTickId());
        assertEquals(75_000_000, end(1).getDurationNanos());
        assertArrayEquals(new Throwable[]{observerFailure}, bodyFailure.getSuppressed());
    }

    @Test
    void startObserverFailureStillEmitsOneEnd() {
        Error failure = new AssertionError("start observer");
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof ServerTickStartEvent) throw failure;
            return null;
        }).when(manager).callEvent(any());

        assertSame(failure, assertThrows(Error.class, () -> server.tick(0, nanos::get)));
        assertEquals(2, events.size());
        assertEquals(((ServerTickStartEvent) events.get(0)).getTickId(), end(1).getTickId());
        verifyNoInteractions(network, scheduler);
    }

    @Test
    void endObserverWorkDoesNotChangeReportedDuration() {
        doAnswer(invocation -> {
            nanos.addAndGet(2_000_000);
            return null;
        }).when(network).processInterfaces();
        doAnswer(invocation -> {
            Event event = invocation.getArgument(0);
            events.add(event);
            if (event instanceof ServerTickEndEvent) nanos.addAndGet(90_000_000);
            return null;
        }).when(manager).callEvent(any());

        server.tick(0, nanos::get);

        assertEquals(2_000_000, end(1).getDurationNanos());
    }

    @Test
    void noListenersStillRunsTheSameTickBody() throws Exception {
        set("pluginManager", new PluginManager(server, mock(SimpleCommandMap.class)));

        server.tick(0, nanos::get);

        verify(network).processInterfaces();
        verify(scheduler).mainThreadHeartbeat(1);
        Field nextTick = Server.class.getDeclaredField("nextTick");
        nextTick.setAccessible(true);
        assertEquals(50L, nextTick.getLong(server));
    }

    @Test
    void pluginDisableUnregistersTickObserversBeforeReload() {
        PluginManager realManager = new PluginManager(server, mock(SimpleCommandMap.class));
        when(server.getScheduler()).thenReturn(scheduler);
        Plugin oldPlugin = enabledPlugin();
        Plugin reloadedPlugin = enabledPlugin();
        AtomicInteger oldCalls = new AtomicInteger();
        AtomicInteger newCalls = new AtomicInteger();
        try {
            register(oldPlugin, oldCalls);
            realManager.callEvent(new ServerTickEndEvent(1, 2));
            realManager.disablePlugin(oldPlugin);
            register(reloadedPlugin, newCalls);
            realManager.callEvent(new ServerTickEndEvent(2, 2));
            assertEquals(1, oldCalls.get());
            assertEquals(1, newCalls.get());
        } finally {
            HandlerList.unregisterAll(oldPlugin);
            HandlerList.unregisterAll(reloadedPlugin);
        }
    }

    private static Plugin enabledPlugin() {
        Plugin plugin = mock(Plugin.class);
        when(plugin.isEnabled()).thenReturn(true);
        when(plugin.getPluginLoader()).thenReturn(mock(PluginLoader.class));
        when(plugin.getDescription()).thenReturn(mock(PluginDescription.class));
        return plugin;
    }

    private static void register(Plugin plugin, AtomicInteger calls) {
        ServerTickEndEvent.getHandlers().register(new RegisteredListener(
                new Listener() {}, (listener, event) -> calls.incrementAndGet(),
                EventPriority.MONITOR, plugin, false));
    }

    private ServerTickEndEvent end(int index) {
        return assertInstanceOf(ServerTickEndEvent.class, events.get(index));
    }

    private void set(String name, Object value) throws Exception {
        Field field = Server.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(server, value);
    }
}
