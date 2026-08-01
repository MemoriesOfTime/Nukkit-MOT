package cn.nukkit.plugin;

import java.io.File;
import java.lang.ref.WeakReference;
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

    private WeakReference<Plugin> plugin = new WeakReference<>(null);

    public PluginClassLoader(JavaPluginLoader loader, ClassLoader parent, File file) throws MalformedURLException {
        this(loader, parent, file, new URL[0]);
    }

    /**
     * 在主 jar 之外追加库 jar URL（通常是 plugin.yml {@code libraries} 声明、服务端下载好的外部库）。
     * 隔离语义（版本优先，非访问控制）见 {@link #findClass(String, boolean)} 与 {@link LibraryLoader#resolve}。
     * / Adds extra library URLs beyond the main jar (typically downloaded from plugin.yml libraries).
     * Isolation semantics (version-preference, not access control) are documented in {@link #findClass(String, boolean)} and {@link LibraryLoader#resolve}.
     */
    public PluginClassLoader(JavaPluginLoader loader, ClassLoader parent, File file, URL[] extraUrls) throws MalformedURLException {
        super(buildUrls(file, extraUrls), parent);
        this.loader = loader;
    }

    private static URL[] buildUrls(File file, URL[] extraUrls) throws MalformedURLException {
        URL main = file.toURI().toURL();
        if (extraUrls == null || extraUrls.length == 0) {
            return new URL[]{main};
        }
        URL[] urls = new URL[1 + extraUrls.length];
        urls[0] = main;
        System.arraycopy(extraUrls, 0, urls, 1, extraUrls.length);
        return urls;
    }

    void setPlugin(Plugin plugin) {
        this.plugin = new WeakReference<>(plugin);
    }

    /**
     * 返回该 ClassLoader 正在加载的插件，包括插件注册到 PluginManager 之前的 onLoad 阶段。<br>
     * Returns the plugin loaded by this ClassLoader, including the onLoad phase before PluginManager registration.
     */
    public Plugin getPlugin() {
        return this.plugin.get();
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        return this.findClass(name, true);
    }

    /**
     * 类查找顺序：本 loader 缓存 → 自己的 URL[]（主 jar + plugin.yml libraries）→ 全局其他插件。<br>
     * Class lookup order: this loader's cache → its own URL[] (main jar + plugin.yml libraries) → other plugins globally.
     *
     * <p>"自己 URL[] 优先于全局共享"是刻意的语义：保证插件 plugin.yml 声明的 library 版本不会被其他插件
     * shade 的同名类覆盖（Spigot 风格的隔离）。依赖机制（{@code depend}/{@code softdepend}）不受影响——
     * 自己 URL[] 找不到时会回退到全局查找其他插件暴露的类。
     * <br>"Own URL[] before global sharing" is intentional: a library declared in plugin.yml keeps its version
     * instead of being shadowed by another plugin's shaded copy (Spigot-style isolation). The dependency mechanism
     * ({@code depend}/{@code softdepend}) still works — when the own URL[] misses, lookup falls back to the global
     * scan for classes exposed by other plugins.
     */
    protected Class<?> findClass(String name, boolean checkGlobal) throws ClassNotFoundException {
        if (name.startsWith("cn.nukkit.") || name.startsWith("net.minecraft.")) {
            throw new ClassNotFoundException(name);
        }
        Class<?> result = classes.get(name);

        if (result == null) {
            // 先查自己 URL[]（主 jar + libraries），保证 plugin.yml 声明的库优先
            try {
                result = super.findClass(name);
            } catch (ClassNotFoundException ignored) {
            }

            // 自己没有再查全局其他插件，保留 depend/softdepend 依赖机制
            if (result == null && checkGlobal) {
                result = loader.getClassByName(name);
            }

            if (result == null) {
                throw new ClassNotFoundException(name);
            }

            loader.setClass(name, result);
            classes.put(name, result);
        }

        return result;
    }
}
