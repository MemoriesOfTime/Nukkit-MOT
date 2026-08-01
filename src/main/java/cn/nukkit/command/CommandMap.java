package cn.nukkit.command;

import cn.nukkit.plugin.Plugin;

import java.util.List;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface CommandMap {

    void registerAll(String fallbackPrefix, List<? extends Command> commands);

    boolean register(String fallbackPrefix, Command command);

    boolean register(String fallbackPrefix, Command command, String label);

    void unregister(String... commands);

    void unregister(Command... commands);

    default void unregister(List<? extends Command> commands) {
        this.unregister(commands.toArray(new Command[0]));
    }

    /**
     * 注销指定插件拥有的全部命令，用于插件卸载时清理。默认空实现兼容第三方实现。<br>
     * Unregisters all commands owned by the given plugin on unload. Default no-op keeps third-party impls compatible.
     *
     * @param plugin 要清理命令的插件。<br>The plugin whose commands should be removed.
     */
    default void unregister(Plugin plugin) {
    }

    void registerSimpleCommands(Object object);

    boolean dispatch(CommandSender sender, String cmdLine);

    void clearCommands();

    Command getCommand(String name);
}
