package cn.nukkit.plugin.annotation;

import cn.nukkit.plugin.PluginLoadOrder;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface PluginMeta {

    /**
     * The plugin name.
     */
    String name();

    /**
     * The plugin version.
     */
    String version();

    /**
     * The compatible API versions.
     */
    String[] api();

    /**
     * Description of the plugin.
     */
    String description() default "";

    /**
     * The plugin website.
     */
    String website() default "";

    /**
     * The plugin authors.
     */
    String[] authors() default {};

    /**
     * The log prefix used by this plugin. Optional.
     */
    String prefix() default "";

    /**
     * Plugins that must be loaded after this one.
     */
    String[] loadBefore() default {};

    /**
     * The load order. Defaults to {@link PluginLoadOrder#POSTWORLD}; the default
     * is omitted from the generated descriptor.
     */
    PluginLoadOrder order() default PluginLoadOrder.POSTWORLD;

    /**
     * Hard dependencies: plugins that must be present and loaded first. Optional.
     */
    Dependency[] depend() default {};

    /**
     * Soft dependencies: plugins loaded first when present, but not required. Optional.
     */
    Dependency[] softDepend() default {};
}
