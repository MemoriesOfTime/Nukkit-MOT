package cn.nukkit.plugin;

import cn.nukkit.event.Event;
import cn.nukkit.event.Listener;
import cn.nukkit.utils.EventException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 验证 {@link MethodEventExecutor} 在优化为 MethodHandle 直接调用后仍保持原有语义：
 * 兼容所有访问级别（含 private）与独立 ClassLoader、事件子类分发、错配事件静默跳过、
 * 监听器异常上报，以及反射回退路径。
 * <p>
 * Verifies that {@link MethodEventExecutor} keeps its semantics after switching to direct
 * MethodHandle invocation: all access levels (including private) and isolated ClassLoaders,
 * event subclass dispatch, silent skip on mismatched events, listener exception reporting,
 * and the reflective fallback path.
 */
public class MethodEventExecutorTest {

    static class SampleEvent extends Event {
    }

    static class SampleSubEvent extends SampleEvent {
    }

    static class OtherEvent extends Event {
    }

    public static class TestListener implements Listener {
        public final AtomicInteger count = new AtomicInteger();

        public void onEvent(SampleEvent event) {
            count.incrementAndGet();
        }

        private void onPrivateEvent(SampleEvent event) {
            count.addAndGet(10);
        }

        public void onThrowing(SampleEvent event) {
            throw new IllegalStateException("boom");
        }

        /** 监听器内部真实的类型转换失败，不是事件类型错配。 / A genuine cast failure inside the listener, not an event mismatch. */
        public void onCastFailure(SampleEvent event) {
            Object value = "not a number";
            count.addAndGet((Integer) value);
        }
    }

    /** 模拟真实插件常见写法：package-private 监听器类。 / Mimics common plugin style: package-private listener class. */
    static class PackagePrivateListener implements Listener {
        final AtomicInteger count = new AtomicInteger();

        void onEvent(SampleEvent event) {
            count.incrementAndGet();
        }
    }

    /** 由隔离 ClassLoader 加载，模拟插件类。 / Loaded by an isolated ClassLoader to mimic a plugin class. */
    public static class IsolatedListener implements Listener {
        public static int hits;

        public void onEvent(SampleEvent event) {
            hits++;
        }
    }

    private MethodEventExecutor executorFor(String name) throws NoSuchMethodException {
        Method method = TestListener.class.getDeclaredMethod(name, SampleEvent.class);
        method.setAccessible(true);
        return new MethodEventExecutor(method);
    }

    @Test
    public void testPublicEventDispatch() throws Exception {
        TestListener listener = new TestListener();
        MethodEventExecutor executor = executorFor("onEvent");
        assertNotNull(executor.getMethod());
        executor.execute(listener, new SampleEvent());
        assertEquals(1, listener.count.get());
    }

    @Test
    public void testSubclassEventDispatch() throws Exception {
        TestListener listener = new TestListener();
        executorFor("onEvent").execute(listener, new SampleSubEvent());
        assertEquals(1, listener.count.get());
    }

    @Test
    public void testPrivateMethodDispatch() throws Exception {
        TestListener listener = new TestListener();
        MethodEventExecutor executor = executorFor("onPrivateEvent");
        executor.execute(listener, new SampleEvent());
        assertEquals(10, listener.count.get());
    }

    @Test
    public void testMismatchedEventSilentlySkipped() throws Exception {
        TestListener listener = new TestListener();
        executorFor("onEvent").execute(listener, new OtherEvent());
        assertEquals(0, listener.count.get());
    }

    @Test
    public void testTargetExceptionWrapped() throws Exception {
        TestListener listener = new TestListener();
        EventException ex = assertThrows(EventException.class,
                () -> executorFor("onThrowing").execute(listener, new SampleEvent()));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertEquals("boom", ex.getCause().getMessage());
    }

    /**
     * 监听器内部抛出的 ClassCastException 必须上报，不能被当成事件类型错配而静默吞掉。
     * <p>
     * A ClassCastException thrown inside the listener must be reported, not silently
     * swallowed as if it were an event type mismatch.
     */
    @Test
    public void testListenerClassCastExceptionReported() throws Exception {
        TestListener listener = new TestListener();
        EventException ex = assertThrows(EventException.class,
                () -> executorFor("onCastFailure").execute(listener, new SampleEvent()));
        assertTrue(ex.getCause() instanceof ClassCastException);
    }

    @Test
    public void testPackagePrivateListenerClass() throws Exception {
        PackagePrivateListener listener = new PackagePrivateListener();
        Method method = PackagePrivateListener.class.getDeclaredMethod("onEvent", SampleEvent.class);
        method.setAccessible(true);
        MethodEventExecutor executor = new MethodEventExecutor(method);
        executor.execute(listener, new SampleEvent());
        executor.execute(listener, new SampleSubEvent());
        assertEquals(2, listener.count.get());
    }

    /**
     * 插件由独立 ClassLoader 加载，与服务端分属不同的 unnamed module。此处复现该场景，
     * 确认既能正确分发，也确实走了句柄快速路径而非静默回退反射。
     * <p>
     * Plugins are loaded by isolated ClassLoaders, landing in a different unnamed module than
     * the server. This reproduces that setup and asserts both correct dispatch and that the
     * handle fast path is actually taken instead of silently falling back to reflection.
     */
    @Test
    public void testIsolatedClassLoaderListenerUsesFastPath() throws Exception {
        String name = IsolatedListener.class.getName();
        Class<?> isolated = Class.forName(name, true,
                new IsolatedClassLoader(name, getClass().getClassLoader()));
        assertNotSame(IsolatedListener.class, isolated);
        assertNotSame(IsolatedListener.class.getModule(), isolated.getModule());

        Method method = isolated.getDeclaredMethod("onEvent", SampleEvent.class);
        method.setAccessible(true);
        MethodEventExecutor executor = new MethodEventExecutor(method);
        assertNotNull(handleOf(executor), "跨 ClassLoader 的监听器应走句柄快速路径");

        Listener listener = (Listener) isolated.getDeclaredConstructor().newInstance();
        executor.execute(listener, new SampleEvent());
        executor.execute(listener, new SampleSubEvent());
        executor.execute(listener, new OtherEvent());
        assertEquals(2, isolated.getField("hits").getInt(null));
    }

    /**
     * 句柄不可用时必须仍能正确分发，并保持与快速路径一致的异常语义。
     * <p>
     * When the handle is unavailable, dispatch must still work and keep the same exception
     * semantics as the fast path.
     */
    @Test
    public void testReflectionFallback() throws Exception {
        TestListener listener = new TestListener();
        MethodEventExecutor executor = executorFor("onEvent");
        clearHandle(executor);
        executor.execute(listener, new SampleSubEvent());
        executor.execute(listener, new OtherEvent());
        assertEquals(1, listener.count.get());

        MethodEventExecutor throwing = executorFor("onThrowing");
        clearHandle(throwing);
        EventException ex = assertThrows(EventException.class,
                () -> throwing.execute(listener, new SampleEvent()));
        assertTrue(ex.getCause() instanceof IllegalStateException);
        assertEquals("boom", ex.getCause().getMessage());

        MethodEventExecutor casting = executorFor("onCastFailure");
        clearHandle(casting);
        EventException cce = assertThrows(EventException.class,
                () -> casting.execute(listener, new SampleEvent()));
        assertTrue(cce.getCause() instanceof ClassCastException);
    }

    private static Field handleField() throws NoSuchFieldException {
        Field field = MethodEventExecutor.class.getDeclaredField("handle");
        field.setAccessible(true);
        return field;
    }

    private static Object handleOf(MethodEventExecutor executor) throws Exception {
        return handleField().get(executor);
    }

    private static void clearHandle(MethodEventExecutor executor) throws Exception {
        handleField().set(executor, null);
    }

    /**
     * 只隔离指定类、其余委托父加载器的 ClassLoader，用于复现插件 {@code PluginClassLoader} 场景。
     * <p>
     * ClassLoader that isolates a single class and delegates everything else to its parent,
     * reproducing the plugin {@code PluginClassLoader} setup.
     */
    private static final class IsolatedClassLoader extends ClassLoader {

        private final String isolated;

        IsolatedClassLoader(String isolated, ClassLoader parent) {
            super(parent);
            this.isolated = isolated;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
            if (!isolated.equals(name)) {
                return super.loadClass(name, resolve);
            }
            synchronized (getClassLoadingLock(name)) {
                Class<?> loaded = findLoadedClass(name);
                if (loaded == null) {
                    byte[] bytes = readBytes(name);
                    loaded = defineClass(name, bytes, 0, bytes.length);
                }
                if (resolve) {
                    resolveClass(loaded);
                }
                return loaded;
            }
        }

        private byte[] readBytes(String name) throws ClassNotFoundException {
            try (InputStream in = getParent().getResourceAsStream(name.replace('.', '/') + ".class")) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                return in.readAllBytes();
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }
}
