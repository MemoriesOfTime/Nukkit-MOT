package cn.nukkit.utils;

public class DynamicClassLoader extends ClassLoader {

    public Class<?> defineClass(String name, byte[] b) {
        return defineClass(name, b, 0, b.length);
    }

}