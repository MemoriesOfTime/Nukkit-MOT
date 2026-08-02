package cn.nukkit.plugin;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 回归测试：卸载插件只清理自己的类缓存条目。<br>
 * Regression: unloading a plugin must drop only its own class-cache entries.
 * <p>
 * 若查找时开启 checkGlobal，其他插件的类会经全局缓存原样返回并被误判为本插件所有，导致缓存被整体清空。<br>
 * With checkGlobal enabled the global cache returns other plugins' classes verbatim, they compare equal,
 * and the entire cache gets wiped.
 */
class JavaPluginLoaderUnloadTest {

    @Test
    void unloadingOnePluginKeepsOtherPluginsClassCache(@TempDir Path tempDir) throws Exception {
        File jarA = buildSingleClassJar(tempDir, "OwnedByA");
        File jarB = buildSingleClassJar(tempDir, "OwnedByB");

        JavaPluginLoader javaPluginLoader = new JavaPluginLoader(null);
        Map<String, Class<?>> classes = readField(javaPluginLoader, "classes");
        Map<String, PluginClassLoader> classLoaders = readField(javaPluginLoader, "classLoaders");

        try (PluginClassLoader loaderA = new PluginClassLoader(javaPluginLoader, getClass().getClassLoader(), jarA);
             PluginClassLoader loaderB = new PluginClassLoader(javaPluginLoader, getClass().getClassLoader(), jarB)) {
            classLoaders.put("PluginA", loaderA);
            classLoaders.put("PluginB", loaderB);

            // 触发加载以填充全局类缓存 / load both to populate the global class cache
            loaderA.loadClass("fixture.OwnedByA");
            loaderB.loadClass("fixture.OwnedByB");
            assertTrue(classes.containsKey("fixture.OwnedByA"));
            assertTrue(classes.containsKey("fixture.OwnedByB"));

            javaPluginLoader.unloadPlugin(stubPlugin("PluginA"));

            assertFalse(classes.containsKey("fixture.OwnedByA"),
                    "被卸载插件的类应从全局缓存移除 / the unloaded plugin's class must leave the global cache");
            assertTrue(classes.containsKey("fixture.OwnedByB"),
                    "其他插件的类缓存不得被连带清空 / another plugin's cached class must survive");
            assertSame(loaderB, classLoaders.get("PluginB"),
                    "其他插件的 ClassLoader 注册不得受影响 / another plugin's ClassLoader registration must be intact");
        }
    }

    private static Plugin stubPlugin(String name) {
        PluginDescription description = Mockito.mock(PluginDescription.class);
        Mockito.when(description.getName()).thenReturn(name);
        Plugin plugin = Mockito.mock(Plugin.class);
        Mockito.when(plugin.getDescription()).thenReturn(description);
        Mockito.when(plugin.isEnabled()).thenReturn(false);
        return plugin;
    }

    @SuppressWarnings("unchecked")
    private static <T> T readField(JavaPluginLoader loader, String name) throws Exception {
        Field field = JavaPluginLoader.class.getDeclaredField(name);
        field.setAccessible(true);
        return (T) field.get(loader);
    }

    /**
     * 把一个只含单个空类的 fixture 编译进独立 jar。<br>
     * Compiles a fixture holding a single empty class into a standalone jar.
     */
    private static File buildSingleClassJar(Path tempDir, String className) throws IOException {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertNotNull(compiler, "该回归测试需要 JDK JavaCompiler / this regression test requires a JDK JavaCompiler");

        Path source = tempDir.resolve("src/" + className + "/fixture/" + className + ".java");
        Files.createDirectories(source.getParent());
        Files.writeString(source, "package fixture;\npublic final class " + className + " {}\n");

        Path classes = tempDir.resolve("classes/" + className);
        Files.createDirectories(classes);
        assertEquals(0, compiler.run(null, null, null, "-proc:none", "-d", classes.toString(), source.toString()),
                "fixture 编译失败 / fixture compilation failed: " + className);

        File jar = tempDir.resolve(className + ".jar").toFile();
        try (JarOutputStream output = new JarOutputStream(Files.newOutputStream(jar.toPath()))) {
            output.putNextEntry(new JarEntry("fixture/" + className + ".class"));
            output.write(Files.readAllBytes(classes.resolve("fixture/" + className + ".class")));
            output.closeEntry();
        }
        return jar;
    }
}
