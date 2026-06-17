package cn.nukkit.plugin;

import cn.nukkit.Server;

import java.io.File;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class PluginClassLoader extends URLClassLoader {

    private final JavaPluginLoader loader;

    private final Map<String, Class<?>> classes = new HashMap<>();

    public PluginClassLoader(JavaPluginLoader loader, ClassLoader parent, File file) throws MalformedURLException {
        super(new URL[]{file.toURI().toURL()}, parent);
        this.loader = loader;
    }

    @Override
    protected Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (name.startsWith("com.nimbusds.jose.shaded.")) {
            Server.getInstance().getLogger().warning(
                    "Plugin code loaded server-internal shaded class '" + name + "'. " +
                            "com.nimbusds.jose.shaded is an internal compatibility shim (not public API), " +
                            "and may break without notice on upgrades. Migrate to the server-bundled " +
                            "Gson (com.google.gson.Gson) instead.");
        }
        return super.loadClass(name, resolve);
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return this.findClass(name, true);

    }

    protected Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
        if (name.startsWith("cn.nukkit.") || name.startsWith("net.minecraft.")) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result = classes.get(name);

        if (result == null) {
            if (checkGlobal) {
                result = loader.getClassByName(name);
            }

            if (result == null) {
                result = super.findClass(name);

                if (result != null) {
                    loader.setClass(name, result);
                }
            }

            classes.put(name, result);
        }

        return result;
    }
}
