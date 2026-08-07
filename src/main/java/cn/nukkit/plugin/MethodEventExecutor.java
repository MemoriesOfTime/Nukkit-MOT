package cn.nukkit.plugin;

import cn.nukkit.event.Event;
import cn.nukkit.event.Listener;
import cn.nukkit.utils.EventException;
import lombok.extern.log4j.Log4j2;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * 基于方法句柄的事件执行器：注册时把 {@link Method#invoke} 换成适配到
 * {@code (Listener, Event)void} 的 {@link MethodHandle}，省掉每次分发的变参数组分配；
 * 句柄无法创建或签名不适配时回退反射。
 * <p>
 * Method-handle based event executor: at registration time it replaces
 * {@link Method#invoke} with a {@link MethodHandle} adapted to
 * {@code (Listener, Event)void}, avoiding the varargs array allocation on every
 * dispatch, and falls back to reflection when the handle cannot be built or adapted.
 *
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class MethodEventExecutor implements EventExecutor {

    private static final MethodType EXECUTOR_TYPE = MethodType.methodType(void.class, Listener.class, Event.class);

    private final Method method;

    private final Class<?>[] parameterTypes;

    private final MethodHandle handle;

    public MethodEventExecutor(Method method) {
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.handle = createHandle(method);
    }

    @Override
    public void execute(Listener listener, Event event) throws EventException {
        if (!isApplicable(event)) {
            return;
        }
        try {
            if (handle != null) {
                handle.invokeExact(listener, event);
            } else {
                invokeReflectively(listener, event);
            }
        } catch (Throwable t) {
            throw new EventException(t);
        }
    }

    /**
     * 反射调用并剥掉 {@link InvocationTargetException} 外壳，使两条路径抛出的都是监听器本身的异常。
     * <p>
     * Invokes reflectively and unwraps {@link InvocationTargetException}, so that both
     * paths surface the exception thrown by the listener itself.
     */
    private void invokeReflectively(Listener listener, Event event) throws Throwable {
        try {
            method.invoke(listener, event);
        } catch (InvocationTargetException ex) {
            throw ex.getCause() != null ? ex.getCause() : ex;
        }
    }

    /**
     * 子类事件共用父类的 {@code HandlerList}，因此监听器可能收到父类型事件，分发前必须过滤。
     * <p>
     * Subclass events share the parent's {@code HandlerList}, so a handler may receive a
     * supertype event and must be filtered before dispatch.
     */
    private boolean isApplicable(Event event) {
        for (Class<?> param : parameterTypes) {
            if (param.isInstance(event)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 为监听方法创建直接调用句柄。插件由独立 ClassLoader 加载（各属不同的 unnamed module），
     * 此处只能用 {@link MethodHandle#asType} 适配而不能用 LambdaMetafactory —— 后者要求完整权限的
     * Lookup，而 {@code privateLookupIn} 跨模块时会丢弃 MODULE 权限。
     * <p>
     * Builds a direct invocation handle for the handler method. Plugins are loaded by
     * isolated ClassLoaders (each in its own unnamed module), so the handle must be adapted
     * via {@link MethodHandle#asType} rather than LambdaMetafactory, which requires a
     * full-privilege Lookup that {@code privateLookupIn} cannot grant across modules.
     *
     * @return 适配后的句柄，为 null 表示回退反射 / the adapted handle, or null to fall back to reflection
     */
    private static MethodHandle createHandle(Method method) {
        try {
            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodHandles.Lookup caller;
            try {
                caller = MethodHandles.privateLookupIn(method.getDeclaringClass(), lookup);
            } catch (IllegalAccessException e) {
                caller = lookup;
            }
            return caller.unreflect(method).asType(EXECUTOR_TYPE);
        } catch (Throwable t) {
            log.debug("Falling back to reflective invocation for event handler {}", method, t);
            return null;
        }
    }

    public Method getMethod() {
        return method;
    }
}
