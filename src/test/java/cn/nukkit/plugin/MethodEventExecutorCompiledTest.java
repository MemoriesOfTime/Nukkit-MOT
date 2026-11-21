package cn.nukkit.plugin;

import cn.nukkit.event.Event;
import cn.nukkit.event.Listener;
import cn.nukkit.utils.EventException;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MethodEventExecutorCompiledTest {

    public static class PublicSampleEvent extends Event {
    }

    public static class PublicSubEvent extends PublicSampleEvent {
    }

    public static class UnrelatedEvent extends Event {
    }

    static class PackageEvent extends Event {
    }

    public interface InterfaceListener extends Listener {
        AtomicInteger INTERFACE_HITS = new AtomicInteger();

        default int onInterfaceEvent(PublicSampleEvent event) {
            return INTERFACE_HITS.incrementAndGet();
        }
    }

    static abstract class PackageBaseListener implements Listener {
        final AtomicInteger baseHits = new AtomicInteger();

        public long onBaseEvent(PublicSampleEvent event) {
            return baseHits.incrementAndGet();
        }
    }

    public static class PublicConcreteListener extends PackageBaseListener implements InterfaceListener {
        public static final AtomicInteger STATIC_HITS = new AtomicInteger();
        public final AtomicInteger hits = new AtomicInteger();

        public void onPublic(PublicSampleEvent event) {
            hits.incrementAndGet();
        }

        public double onDoubleReturn(PublicSampleEvent event) {
            hits.addAndGet(2);
            return 1.5;
        }

        public static String onStatic(PublicSampleEvent event) {
            STATIC_HITS.incrementAndGet();
            return "ok";
        }

        private void onPrivate(PackageEvent event) {
            hits.addAndGet(5);
        }

        public void onBadCast(PublicSampleEvent event) {
            Object raw = "abc";
            hits.addAndGet((Integer) raw);
        }
    }

    public static class IsolatedPluginListener implements Listener {
        public static int count;

        public void onSample(PublicSampleEvent event) {
            count++;
        }
    }

    @Test
    void compilesPublicInterfaceStaticReturnAndInheritedHandlersToBytecode() throws Exception {
        PublicConcreteListener listener = new PublicConcreteListener();
        PublicConcreteListener.STATIC_HITS.set(0);
        InterfaceListener.INTERFACE_HITS.set(0);

        MethodEventExecutor pubExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PublicConcreteListener.class.getMethod("onPublic", PublicSampleEvent.class));
        assertNotSame(MethodEventExecutor.class, pubExec.getClass());
        pubExec.execute(listener, new PublicSampleEvent());
        pubExec.execute(listener, new PublicSubEvent());
        pubExec.execute(listener, new UnrelatedEvent());
        assertEquals(2, listener.hits.get());

        MethodEventExecutor dblExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PublicConcreteListener.class.getMethod("onDoubleReturn", PublicSampleEvent.class));
        assertNotSame(MethodEventExecutor.class, dblExec.getClass());
        dblExec.execute(listener, new PublicSampleEvent());
        assertEquals(4, listener.hits.get());

        MethodEventExecutor staticExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PublicConcreteListener.class.getMethod("onStatic", PublicSampleEvent.class));
        assertNotSame(MethodEventExecutor.class, staticExec.getClass());
        staticExec.execute(null, new PublicSampleEvent());
        assertEquals(1, PublicConcreteListener.STATIC_HITS.get());

        MethodEventExecutor ifaceExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                InterfaceListener.class.getMethod("onInterfaceEvent", PublicSampleEvent.class));
        assertNotSame(MethodEventExecutor.class, ifaceExec.getClass());
        ifaceExec.execute(listener, new PublicSampleEvent());
        assertEquals(1, InterfaceListener.INTERFACE_HITS.get());

        // Public handler inherited from a package-private superclass invoked through the public listener subclass
        MethodEventExecutor inheritedExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PackageBaseListener.class.getMethod("onBaseEvent", PublicSampleEvent.class));
        assertNotSame(MethodEventExecutor.class, inheritedExec.getClass());
        inheritedExec.execute(listener, new PublicSampleEvent());
        assertEquals(1, listener.baseHits.get());

        // Genuine ClassCastException inside listener body is wrapped in EventException, never swallowed
        MethodEventExecutor castExec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PublicConcreteListener.class.getMethod("onBadCast", PublicSampleEvent.class));
        EventException ex = assertThrows(EventException.class,
                () -> castExec.execute(listener, new PublicSampleEvent()));
        assertInstanceOf(ClassCastException.class, ex.getCause());
    }

    @Test
    void compilesSameModulePrivateHandlerViaHiddenNestmate() throws Exception {
        PublicConcreteListener listener = new PublicConcreteListener();
        Method priv = java.util.Arrays.stream(PublicConcreteListener.class.getDeclaredMethods())
                .filter(m -> m.getName().equals("onPrivate"))
                .findFirst()
                .orElseThrow();
        priv.setAccessible(true);

        MethodEventExecutor exec = MethodEventExecutor.compile(PublicConcreteListener.class, priv);
        assertNotSame(MethodEventExecutor.class, exec.getClass());
        assertTrue(exec.getClass().isHidden(), "Same-module private handler should compile as a hidden nestmate");

        exec.execute(listener, new PackageEvent());
        exec.execute(listener, new UnrelatedEvent());
        assertEquals(5, listener.hits.get());
    }

    @Test
    void compilesIsolatedPluginClassLoaderAndAllowsUnloading() throws Exception {
        WeakReference<ClassLoader> loaderRef = runIsolatedDispatchAndRelease();
        for (int attempt = 0; attempt < 20 && loaderRef.get() != null; attempt++) {
            System.gc();
            Thread.sleep(15);
        }
        assertNull(loaderRef.get(), "Compiled executor must not pin an unloaded PluginClassLoader");
    }

    private static WeakReference<ClassLoader> runIsolatedDispatchAndRelease() throws Exception {
        String name = IsolatedPluginListener.class.getName();
        ClassLoader loader = new SingleIsolatedLoader(name, MethodEventExecutorCompiledTest.class.getClassLoader());
        Class<?> isolated = Class.forName(name, true, loader);
        assertNotSame(IsolatedPluginListener.class, isolated);
        assertNotSame(MethodEventExecutor.class.getModule(), isolated.getModule());

        @SuppressWarnings("unchecked")
        Class<? extends Listener> listenerType = (Class<? extends Listener>) isolated;
        Method m = isolated.getMethod("onSample", PublicSampleEvent.class);
        MethodEventExecutor exec = MethodEventExecutor.compile(listenerType, m);
        assertNotSame(MethodEventExecutor.class, exec.getClass(),
                "Public isolated plugin handler must compile to bytecode");

        Listener instance = listenerType.getDeclaredConstructor().newInstance();
        exec.execute(instance, new PublicSampleEvent());
        exec.execute(instance, new PublicSubEvent());
        exec.execute(instance, new UnrelatedEvent());
        assertEquals(2, isolated.getField("count").getInt(null));
        return new WeakReference<>(loader);
    }

    @Test
    void compiledDispatchAllocatesZeroBytesOnHotPath() throws Exception {
        PublicConcreteListener listener = new PublicConcreteListener();
        PublicSampleEvent match = new PublicSampleEvent();
        UnrelatedEvent mismatch = new UnrelatedEvent();
        MethodEventExecutor exec = MethodEventExecutor.compile(
                PublicConcreteListener.class,
                PublicConcreteListener.class.getMethod("onPublic", PublicSampleEvent.class));

        for (int i = 0; i < 10_000; i++) {
            exec.execute(listener, match);
            exec.execute(listener, mismatch);
        }

        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 10_000; i++) {
            exec.execute(listener, match);
            exec.execute(listener, mismatch);
        }
        long allocated = mx.getThreadAllocatedBytes(tid) - before;
        assertTrue(allocated <= 256L, "Expected 0 B allocation across 20,000 event dispatches, got " + allocated + " B");
    }

    private static final class SingleIsolatedLoader extends ClassLoader {
        private final String target;

        SingleIsolatedLoader(String target, ClassLoader parent) {
            super(parent);
            this.target = target;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!target.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                        if (in == null) {
                            throw new ClassNotFoundException(name);
                        }
                        byte[] bytes = in.readAllBytes();
                        c = defineClass(name, bytes, 0, bytes.length);
                    } catch (IOException e) {
                        throw new ClassNotFoundException(name, e);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }
    }
}
