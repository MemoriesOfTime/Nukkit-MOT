package cn.nukkit.event.plugin;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.plugin.Plugin;

/**
 * 当一个插件即将被彻底卸载（unload）时触发。<br>
 * Fired when a plugin is about to be fully unloaded.
 * <p>
 * 与 {@link PluginDisableEvent} 不同，卸载会移除插件注册表条目并关闭 ClassLoader，<br>
 * 监听本事件可在卸载前取消整个流程。
 * <p>
 * Unlike {@link PluginDisableEvent}, unloading also removes the plugin from the registry
 * and closes its ClassLoader. Listen to this event to cancel the whole unload flow.
 */
public class PluginUnloadEvent extends PluginEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public PluginUnloadEvent(Plugin plugin) {
        super(plugin);
    }

    public static HandlerList getHandlers() {
        return handlers;
    }
}
