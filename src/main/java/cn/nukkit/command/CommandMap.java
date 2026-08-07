package cn.nukkit.command;

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

    void registerSimpleCommands(Object object);

    boolean dispatch(CommandSender sender, String cmdLine);

    void clearCommands();

    Command getCommand(String name);
}
