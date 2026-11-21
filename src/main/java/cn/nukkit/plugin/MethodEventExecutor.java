package cn.nukkit.plugin;

import cn.nukkit.event.Event;
import cn.nukkit.event.Listener;
import cn.nukkit.utils.EventException;
import lombok.extern.log4j.Log4j2;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Event executor backed by per-handler bytecode generation (with {@link MethodHandle} and
 * reflective fallbacks).
 * <p>
 * {@link #compile(Class, Method)} emits a tiny Java 17 {@code MethodEventExecutor} subclass
 * that performs a direct {@code instanceof} guard and {@code invokevirtual}/{@code invokeinterface}/
 * {@code invokestatic} call without sharing {@code MethodHandle.asType} {@code LambdaForm}s across
 * hundreds of distinct handler types:
 * <ul>
 *   <li>Public handlers on public listener classes (including isolated {@code PluginClassLoader}s)
 *       are defined in a per-executor child {@link ClassLoader} so they unload when the listener
 *       is unregistered.</li>
 *   <li>Non-public same-module handlers are defined via {@link MethodHandles.Lookup#defineHiddenClass}
 *       with {@link MethodHandles.Lookup.ClassOption#NESTMATE}.</li>
 *   <li>Any remaining handler (such as a {@code private} handler inside an isolated plugin module)
 *       falls back to {@link MethodHandle} or reflection.</li>
 * </ul>
 *
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class MethodEventExecutor implements EventExecutor {

    private static final MethodType EXECUTOR_TYPE = MethodType.methodType(void.class, Listener.class, Event.class);
    private static final AtomicLong COMPILED_IDS = new AtomicLong();

    private static final String SUPER_INTERNAL_NAME = "cn/nukkit/plugin/MethodEventExecutor";
    private static final String LISTENER_INTERNAL_NAME = "cn/nukkit/event/Listener";
    private static final String EVENT_INTERNAL_NAME = "cn/nukkit/event/Event";
    private static final String EVENT_EXCEPTION_INTERNAL_NAME = "cn/nukkit/utils/EventException";
    private static final String THROWABLE_INTERNAL_NAME = "java/lang/Throwable";

    private final Method method;

    private final Class<?>[] parameterTypes;

    private final Class<?> singleParameterType;

    private final MethodHandle handle;

    public MethodEventExecutor(Method method) {
        this(method, false);
    }

    protected MethodEventExecutor(Method method, boolean compiledSubclass) {
        this.method = method;
        this.parameterTypes = method.getParameterTypes();
        this.singleParameterType = this.parameterTypes.length == 1 ? this.parameterTypes[0] : null;
        this.handle = compiledSubclass ? null : createHandle(method);
    }

    public static MethodEventExecutor compile(Method method) {
        return compile(null, method);
    }

    public static MethodEventExecutor compile(Class<? extends Listener> listenerClass, Method method) {
        if (method != null && method.getParameterCount() == 1) {
            Class<?> paramType = method.getParameterTypes()[0];
            if (Event.class.isAssignableFrom(paramType) && !paramType.isPrimitive() && !paramType.isHidden()) {
                MethodEventExecutor compiled = tryCompileBytecode(listenerClass, method, paramType);
                if (compiled != null) {
                    return compiled;
                }
            }
        }
        return new MethodEventExecutor(method);
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
        Class<?> single = this.singleParameterType;
        if (single != null) {
            return single.isInstance(event);
        }
        for (Class<?> param : parameterTypes) {
            if (param.isInstance(event)) {
                return true;
            }
        }
        return false;
    }

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

    private static MethodEventExecutor tryCompileBytecode(Class<? extends Listener> listenerClass,
                                                          Method method,
                                                          Class<?> paramType) {
        try {
            Class<?> declaring = method.getDeclaringClass();
            if (declaring.isHidden() || declaring.isPrimitive() || declaring.isArray()) {
                return null;
            }
            Class<?> returnType = method.getReturnType();
            if (returnType.isHidden()) {
                return null;
            }
            boolean isStatic = Modifier.isStatic(method.getModifiers());
            Class<?> invokeOwner = declaring;
            if (!Modifier.isPublic(declaring.getModifiers())
                    && !isStatic
                    && listenerClass != null
                    && !listenerClass.isHidden()
                    && Modifier.isPublic(listenerClass.getModifiers())
                    && declaring.isAssignableFrom(listenerClass)) {
                invokeOwner = listenerClass;
            }

            // Strategy 1: Public owner + public method + public parameter/return types -> child ClassLoader
            // (works across isolated PluginClassLoaders and unloads when the executor is GC'd).
            if (Modifier.isPublic(invokeOwner.getModifiers())
                    && Modifier.isPublic(method.getModifiers())
                    && Modifier.isPublic(paramType.getModifiers())
                    && isPubliclyVisibleType(returnType)) {
                ClassLoader parent = chooseParentLoader(invokeOwner, paramType, returnType);
                if (parent != null) {
                    String internalName = "cn/nukkit/plugin/compiled/CompiledEventExecutor$" + COMPILED_IDS.incrementAndGet();
                    byte[] bytes = emitExecutorClassBytes(internalName, invokeOwner, method, paramType, returnType, isStatic);
                    Class<?> defined = new ExecutorClassLoader(parent).define(internalName.replace('/', '.'), bytes);
                    Constructor<?> ctor = defined.getConstructor(Method.class);
                    return (MethodEventExecutor) ctor.newInstance(method);
                }
            }

            // Strategy 2: Same-module private/package-private handler -> hidden NESTMATE class in declaring's package.
            if (declaring.getModule() == MethodEventExecutor.class.getModule()
                    && isAccessibleFromPackage(paramType, declaring.getPackageName())
                    && isAccessibleFromPackage(returnType, declaring.getPackageName())) {
                ClassLoader targetLoader = declaring.getClassLoader();
                if (resolvesExact(targetLoader, MethodEventExecutor.class)
                        && resolvesExact(targetLoader, declaring)
                        && resolvesExact(targetLoader, paramType)) {
                    MethodHandles.Lookup lookup = MethodHandles.privateLookupIn(declaring, MethodHandles.lookup());
                    String pkg = declaring.getPackageName();
                    String internalName = (pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/") + "CompiledEventExecutor";
                    byte[] bytes = emitExecutorClassBytes(internalName, declaring, method, paramType, returnType, isStatic);
                    MethodHandles.Lookup hiddenLookup = lookup.defineHiddenClass(
                            bytes, true, MethodHandles.Lookup.ClassOption.NESTMATE);
                    MethodHandle ctor = hiddenLookup.findConstructor(
                            hiddenLookup.lookupClass(), MethodType.methodType(void.class, Method.class));
                    return (MethodEventExecutor) ctor.invoke(method);
                }
            }
        } catch (Throwable t) {
            log.debug("Falling back to MethodHandle for event handler {}", method, t);
        }
        return null;
    }

    private static boolean isPubliclyVisibleType(Class<?> type) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        return type.isPrimitive() || Modifier.isPublic(type.getModifiers());
    }

    private static boolean isAccessibleFromPackage(Class<?> type, String packageName) {
        while (type.isArray()) {
            type = type.getComponentType();
        }
        if (type.isPrimitive() || Modifier.isPublic(type.getModifiers())) {
            return true;
        }
        return !Modifier.isPrivate(type.getModifiers()) && packageName.equals(type.getPackageName());
    }

    private static ClassLoader chooseParentLoader(Class<?> owner, Class<?> paramType, Class<?> returnType) {
        ClassLoader[] candidates = {
                owner.getClassLoader(),
                paramType.getClassLoader(),
                MethodEventExecutor.class.getClassLoader()
        };
        for (ClassLoader candidate : candidates) {
            if (candidate != null
                    && resolvesExact(candidate, MethodEventExecutor.class)
                    && resolvesExact(candidate, Listener.class)
                    && resolvesExact(candidate, Event.class)
                    && resolvesExact(candidate, EventException.class)
                    && resolvesExact(candidate, owner)
                    && resolvesExact(candidate, paramType)
                    && resolvesReturnType(candidate, returnType)) {
                return candidate;
            }
        }
        return null;
    }

    private static boolean resolvesReturnType(ClassLoader loader, Class<?> returnType) {
        while (returnType.isArray()) {
            returnType = returnType.getComponentType();
        }
        return returnType.isPrimitive() || resolvesExact(loader, returnType);
    }

    private static boolean resolvesExact(ClassLoader loader, Class<?> target) {
        if (target.isPrimitive()) {
            return true;
        }
        try {
            Class<?> resolved = loader == null
                    ? Class.forName(target.getName(), false, null)
                    : Class.forName(target.getName(), false, loader);
            return resolved == target;
        } catch (ClassNotFoundException | SecurityException e) {
            return false;
        }
    }

    private static final class ExecutorClassLoader extends ClassLoader {
        ExecutorClassLoader(ClassLoader parent) {
            super(parent);
        }

        Class<?> define(String binaryName, byte[] bytes) {
            return defineClass(binaryName, bytes, 0, bytes.length);
        }
    }

    /**
     * Emits a Java 17 (version 61.0) class extending {@link MethodEventExecutor} that overrides
     * {@link #execute(Listener, Event)} with a direct {@code instanceof} check and monomorphic call.
     */
    private static byte[] emitExecutorClassBytes(String thisInternalName,
                                                 Class<?> invokeOwner,
                                                 Method method,
                                                 Class<?> paramType,
                                                 Class<?> returnType,
                                                 boolean isStatic) throws IOException {
        ConstantPool cp = new ConstantPool();
        int thisClass = cp.classInfo(thisInternalName);
        int superClass = cp.classInfo(SUPER_INTERNAL_NAME);
        int ownerClass = cp.classInfo(internalName(invokeOwner));
        int paramClass = cp.classInfo(internalName(paramType));
        int eventExceptionClass = cp.classInfo(EVENT_EXCEPTION_INTERNAL_NAME);
        int throwableClass = cp.classInfo(THROWABLE_INTERNAL_NAME);

        int superCtorRef = cp.methodRef(SUPER_INTERNAL_NAME, "<init>", "(Ljava/lang/reflect/Method;Z)V");
        int eventExInitRef = cp.methodRef(EVENT_EXCEPTION_INTERNAL_NAME, "<init>", "(Ljava/lang/Throwable;)V");

        boolean ownerIsInterface = invokeOwner.isInterface();
        String methodDesc = "(" + descriptor(paramType) + ")" + descriptor(returnType);
        int targetRef = ownerIsInterface
                ? cp.interfaceMethodRef(internalName(invokeOwner), method.getName(), methodDesc)
                : cp.methodRef(internalName(invokeOwner), method.getName(), methodDesc);

        int initName = cp.utf8("<init>");
        int initDesc = cp.utf8("(Ljava/lang/reflect/Method;)V");
        int executeName = cp.utf8("execute");
        int executeDesc = cp.utf8("(L" + LISTENER_INTERNAL_NAME + ";L" + EVENT_INTERNAL_NAME + ";)V");
        int codeAttrName = cp.utf8("Code");
        int stackMapAttrName = cp.utf8("StackMapTable");
        int exceptionsAttrName = cp.utf8("Exceptions");

        // Build execute(Listener, Event) bytecode so branch/handler offsets are exact.
        ByteArrayOutputStream execCodeBuf = new ByteArrayOutputStream(48);
        DataOutputStream execCode = new DataOutputStream(execCodeBuf);
        // 0: aload_2 (event)
        execCode.writeByte(0x2c);
        // 1: instanceof paramClass
        execCode.writeByte(0xc1);
        execCode.writeShort(paramClass);
        // 4: ifne +4 (to offset 8)
        execCode.writeByte(0x9a);
        execCode.writeShort(4);
        // 7: return
        execCode.writeByte(0xb1);

        int tryStart = execCode.size(); // 8
        if (!isStatic) {
            // aload_1 (listener); checkcast ownerClass
            execCode.writeByte(0x2b);
            execCode.writeByte(0xc0);
            execCode.writeShort(ownerClass);
        }
        // aload_2 (event); checkcast paramClass
        execCode.writeByte(0x2c);
        execCode.writeByte(0xc0);
        execCode.writeShort(paramClass);

        if (isStatic) {
            execCode.writeByte(0xb8); // invokestatic
            execCode.writeShort(targetRef);
        } else if (ownerIsInterface) {
            execCode.writeByte(0xb9); // invokeinterface
            execCode.writeShort(targetRef);
            execCode.writeByte(2);    // receiver + 1 event reference
            execCode.writeByte(0);
        } else {
            execCode.writeByte(0xb6); // invokevirtual
            execCode.writeShort(targetRef);
        }

        if (returnType != void.class) {
            execCode.writeByte(returnType == long.class || returnType == double.class ? 0x58 : 0x57); // pop2 : pop
        }
        int tryEnd = execCode.size();
        execCode.writeByte(0xb1); // return

        int handlerPc = execCode.size();
        execCode.writeByte(0x4e); // astore_3
        execCode.writeByte(0xbb); // new EventException
        execCode.writeShort(eventExceptionClass);
        execCode.writeByte(0x59); // dup
        execCode.writeByte(0x2d); // aload_3
        execCode.writeByte(0xb7); // invokespecial EventException.<init>(Throwable)
        execCode.writeShort(eventExInitRef);
        execCode.writeByte(0xbf); // athrow
        byte[] executeBytes = execCodeBuf.toByteArray();

        ByteArrayOutputStream classBuf = new ByteArrayOutputStream(512);
        DataOutputStream out = new DataOutputStream(classBuf);
        out.writeInt(0xCAFEBABE);
        out.writeShort(0);  // minor
        out.writeShort(61); // major: Java 17
        cp.writeTo(out);

        out.writeShort(0x0001 | 0x0010 | 0x0020); // ACC_PUBLIC | ACC_FINAL | ACC_SUPER
        out.writeShort(thisClass);
        out.writeShort(superClass);
        out.writeShort(0); // interfaces_count
        out.writeShort(0); // fields_count
        out.writeShort(2); // methods_count

        // Method 1: public <init>(Method)
        out.writeShort(0x0001); // ACC_PUBLIC
        out.writeShort(initName);
        out.writeShort(initDesc);
        out.writeShort(1); // 1 attribute: Code
        out.writeShort(codeAttrName);
        out.writeInt(2 + 2 + 4 + 7 + 2 + 2); // max_stack + max_locals + code_len + 7B code + ex_len + attrs_len
        out.writeShort(3); // max_stack
        out.writeShort(2); // max_locals
        out.writeInt(7);   // code_length
        out.writeByte(0x2a); // aload_0
        out.writeByte(0x2b); // aload_1
        out.writeByte(0x04); // iconst_1
        out.writeByte(0xb7); // invokespecial
        out.writeShort(superCtorRef);
        out.writeByte(0xb1); // return
        out.writeShort(0); // exception_table_length
        out.writeShort(0); // attributes_count

        // Method 2: public execute(Listener, Event) throws EventException
        // StackMapTable payload: 2 entries:
        //   Frame 0 (pc = 8): same_frame (tag = 8) -> 1 byte
        //   Frame 1 (pc = handlerPc): same_locals_1_stack_item_frame_extended (tag = 247) + u2 delta + tag(7) + u2 throwableClass -> 6 bytes
        //   Total StackMapTable info length = 2 (number_of_entries) + 1 + 6 = 9 bytes
        int stackMapAttrLen = 9;
        int execCodeAttrLen = 2 + 2 + 4 + executeBytes.length + 2 + 8 + 2 + (2 + 4 + stackMapAttrLen);
        out.writeShort(0x0001); // ACC_PUBLIC
        out.writeShort(executeName);
        out.writeShort(executeDesc);
        out.writeShort(2); // Code + Exceptions

        out.writeShort(codeAttrName);
        out.writeInt(execCodeAttrLen);
        out.writeShort(3); // max_stack
        out.writeShort(4); // max_locals (this, listener, event, caught)
        out.writeInt(executeBytes.length);
        out.write(executeBytes);
        out.writeShort(1); // 1 exception handler
        out.writeShort(tryStart);
        out.writeShort(tryEnd);
        out.writeShort(handlerPc);
        out.writeShort(throwableClass);
        out.writeShort(1); // 1 Code sub-attribute: StackMapTable
        out.writeShort(stackMapAttrName);
        out.writeInt(stackMapAttrLen);
        out.writeShort(2); // 2 frames
        out.writeByte(tryStart); // same_frame at offset 8
        out.writeByte(247);      // same_locals_1_stack_item_frame_extended
        out.writeShort(handlerPc - tryStart - 1);
        out.writeByte(7);        // ITEM_Object
        out.writeShort(throwableClass);

        out.writeShort(exceptionsAttrName);
        out.writeInt(4);
        out.writeShort(1);
        out.writeShort(eventExceptionClass);

        out.writeShort(0); // class attributes_count
        return classBuf.toByteArray();
    }

    private static String internalName(Class<?> type) {
        return type.getName().replace('.', '/');
    }

    private static String descriptor(Class<?> type) {
        return type.descriptorString();
    }

    private static final class ConstantPool {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream(256);
        private final DataOutputStream out = new DataOutputStream(bytes);
        private final Map<String, Integer> utf8Pool = new HashMap<>();
        private final Map<String, Integer> classPool = new HashMap<>();
        private int nextIndex = 1;

        int utf8(String value) throws IOException {
            Integer existing = utf8Pool.get(value);
            if (existing != null) {
                return existing;
            }
            int idx = nextIndex++;
            out.writeByte(1); // CONSTANT_Utf8
            out.writeUTF(value);
            utf8Pool.put(value, idx);
            return idx;
        }

        int classInfo(String internalName) throws IOException {
            Integer existing = classPool.get(internalName);
            if (existing != null) {
                return existing;
            }
            int nameIdx = utf8(internalName);
            int idx = nextIndex++;
            out.writeByte(7); // CONSTANT_Class
            out.writeShort(nameIdx);
            classPool.put(internalName, idx);
            return idx;
        }

        int nameAndType(String name, String descriptor) throws IOException {
            int n = utf8(name);
            int d = utf8(descriptor);
            int idx = nextIndex++;
            out.writeByte(12); // CONSTANT_NameAndType
            out.writeShort(n);
            out.writeShort(d);
            return idx;
        }

        int methodRef(String ownerInternal, String name, String descriptor) throws IOException {
            int c = classInfo(ownerInternal);
            int nt = nameAndType(name, descriptor);
            int idx = nextIndex++;
            out.writeByte(10); // CONSTANT_Methodref
            out.writeShort(c);
            out.writeShort(nt);
            return idx;
        }

        int interfaceMethodRef(String ownerInternal, String name, String descriptor) throws IOException {
            int c = classInfo(ownerInternal);
            int nt = nameAndType(name, descriptor);
            int idx = nextIndex++;
            out.writeByte(11); // CONSTANT_InterfaceMethodref
            out.writeShort(c);
            out.writeShort(nt);
            return idx;
        }

        void writeTo(DataOutputStream target) throws IOException {
            target.writeShort(nextIndex);
            bytes.writeTo(target);
        }
    }

    public Method getMethod() {
        return method;
    }
}
