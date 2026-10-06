package cn.nukkit.plugin;

import cn.nukkit.event.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import static org.junit.jupiter.api.Assertions.*;

class PluginManagerHandlerCacheTest {
    static class EnabledPlugin extends PluginBase {}
    static class ParentEvent extends Event implements Cancellable {
        static HandlerList handlers = new HandlerList();
        static int resolutions;
        public static HandlerList getHandlers() { resolutions++; return handlers; }
    }
    static class ChildEvent extends ParentEvent {}
    static class OwnEvent extends ParentEvent {
        static final HandlerList own = new HandlerList();
        public static HandlerList getHandlers() { return own; }
    }
    static class TransientEvent extends Event {
        static int attempts;
        static final HandlerList handlers = new HandlerList();
        public static HandlerList getHandlers() {
            if (attempts++ == 0) throw new IllegalStateException("temporary");
            return handlers;
        }
    }
    static class NullEvent extends Event {
        static HandlerList handlers;
        public static HandlerList getHandlers() { return handlers; }
    }
    static class MissingEvent extends Event {}
    public static class CrossLoaderEvent extends Event {
        static final HandlerList handlers = new HandlerList();
        public static HandlerList getHandlers() { return handlers; }
    }

    @AfterEach void cleanup() { HandlerList.unregisterAll(); ParentEvent.handlers = new HandlerList(); }
    private HandlerList resolve(PluginManager manager, Class<? extends Event> type) throws Exception {
        Method method = PluginManager.class.getDeclaredMethod("getEventListeners", Class.class);
        method.setAccessible(true);
        try { return (HandlerList) method.invoke(manager, type); }
        catch (InvocationTargetException e) { throw (Exception) e.getCause(); }
    }
    private EnabledPlugin enabled() { EnabledPlugin p = new EnabledPlugin(); p.setEnabled(true); return p; }

    @Test void cachesSuccessfulResolutionAndClearsItOnReload() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        ParentEvent.resolutions = 0;
        HandlerList original = resolve(manager, ParentEvent.class);
        assertSame(original, resolve(manager, ParentEvent.class));
        assertEquals(1, ParentEvent.resolutions);
        manager.clearPlugins();
        ParentEvent.handlers = new HandlerList();
        assertNotSame(original, resolve(manager, ParentEvent.class));
        assertEquals(2, ParentEvent.resolutions);
    }
    @Test void sharesParentListButHonorsSubclassOwnedList() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        assertSame(resolve(manager, ParentEvent.class), resolve(manager, ChildEvent.class));
        assertSame(OwnEvent.own, resolve(manager, OwnEvent.class));
        assertNotSame(resolve(manager, ParentEvent.class), resolve(manager, OwnEvent.class));
    }
    @Test void lookupFailuresAreRetried() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        TransientEvent.attempts = 0;
        assertThrows(IllegalAccessException.class, () -> resolve(manager, TransientEvent.class));
        assertSame(TransientEvent.handlers, resolve(manager, TransientEvent.class));
        assertEquals(2, TransientEvent.attempts);
    }
    @Test void invalidNullHandlerListDoesNotPoisonCache() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        NullEvent.handlers = null;
        assertThrows(IllegalArgumentException.class, () -> resolve(manager, NullEvent.class));
        NullEvent.handlers = new HandlerList();
        assertSame(NullEvent.handlers, resolve(manager, NullEvent.class));
    }
    @Test void missingHandlersKeepCheckedFailure() {
        PluginManager manager = new PluginManager(null, null);
        assertThrows(IllegalAccessException.class, () -> resolve(manager, MissingEvent.class));
    }
    @Test void prioritiesCancellationAndPluginDisableArePreserved() {
        PluginManager manager = new PluginManager(null, null);
        EnabledPlugin p = enabled();
        Listener listener = new Listener() {};
        List<String> calls = new ArrayList<>();
        manager.registerEvent(ParentEvent.class, listener, EventPriority.MONITOR, (l,e)->calls.add("monitor"), p, false);
        manager.registerEvent(ParentEvent.class, listener, EventPriority.LOWEST, (l,e)->{ calls.add("cancel"); e.setCancelled(); }, p, false);
        manager.registerEvent(ParentEvent.class, listener, EventPriority.NORMAL, (l,e)->calls.add("skip"), p, true);
        manager.callEvent(new ChildEvent());
        assertEquals(List.of("cancel", "monitor"), calls);
        calls.clear(); p.setEnabled(false);
        manager.callEvent(new ChildEvent());
        assertTrue(calls.isEmpty());
    }
    @Test void cachesListNotBakedListeners() {
        PluginManager manager = new PluginManager(null, null);
        EnabledPlugin p = enabled();
        Listener listener = new Listener() {};
        List<Integer> calls = new ArrayList<>();
        manager.callEvent(new ParentEvent());
        manager.registerEvent(ParentEvent.class, listener, EventPriority.NORMAL, (l,e)->calls.add(1), p, false);
        manager.callEvent(new ParentEvent());
        HandlerList.unregisterAll(listener);
        manager.callEvent(new ParentEvent());
        assertEquals(List.of(1), calls);
    }
    @Test void mutationDuringDispatchUsesCurrentSnapshot() {
        PluginManager manager = new PluginManager(null, null);
        EnabledPlugin p = enabled();
        Listener a = new Listener() {}, b = new Listener() {};
        List<Integer> calls = new ArrayList<>();
        manager.registerEvent(ParentEvent.class, a, EventPriority.LOWEST, (l,e)->{
            calls.add(1); HandlerList.unregisterAll(b);
        }, p, false);
        manager.registerEvent(ParentEvent.class, b, EventPriority.NORMAL, (l,e)->calls.add(2), p, false);
        manager.callEvent(new ParentEvent());
        manager.callEvent(new ParentEvent());
        assertEquals(List.of(1,2,1), calls);
    }
    @Test void eventClassesWithTheSameNameInDifferentLoadersHaveDistinctLists() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        String name = CrossLoaderEvent.class.getName();
        byte[] bytes;
        try (var in = getClass().getResourceAsStream("/" + name.replace('.', '/') + ".class")) {
            assertNotNull(in);
            bytes = in.readAllBytes();
        }
        class IsolatedLoader extends ClassLoader {
            IsolatedLoader() { super(PluginManagerHandlerCacheTest.class.getClassLoader()); }
            Class<? extends Event> isolated() { return defineClass(name, bytes, 0, bytes.length).asSubclass(Event.class); }
        }
        Class<? extends Event> first = new IsolatedLoader().isolated();
        Class<? extends Event> second = new IsolatedLoader().isolated();
        HandlerList firstList = resolve(manager, first);
        HandlerList secondList = resolve(manager, second);
        assertNotSame(firstList, secondList);
        assertSame(firstList, resolve(manager, first));
        assertSame(secondList, resolve(manager, second));
    }
    @Test void concurrentColdResolutionsPublishTheSameHandlerList() throws Exception {
        PluginManager manager = new PluginManager(null, null);
        var pool = Executors.newFixedThreadPool(8);
        try {
            List<java.util.concurrent.Callable<HandlerList>> calls = new ArrayList<>();
            for (int i = 0; i < 64; i++) calls.add(() -> resolve(manager, ParentEvent.class));
            for (var result : pool.invokeAll(calls)) assertSame(ParentEvent.handlers, result.get());
        } finally {
            pool.shutdownNow();
        }
    }
}
