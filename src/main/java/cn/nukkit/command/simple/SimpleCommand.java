package cn.nukkit.command.simple;

import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.ConsoleCommandSender;
import cn.nukkit.command.PluginIdentifiableCommand;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.plugin.Plugin;

import java.lang.reflect.Method;

/**
 * @author Tee7even
 */
public class SimpleCommand extends Command implements PluginIdentifiableCommand {
    private Object object;
    private Method method;
    private final Plugin owningPlugin;
    private boolean forbidConsole;
    private int maxArgs;
    private int minArgs;

    public SimpleCommand(Object object, Method method, String name, String description, String usageMessage, String[] aliases) {
        this(object, method, null, name, description, usageMessage, aliases);
    }

    /**
     * 携带所属插件构造 SimpleCommand，使 {@link PluginIdentifiableCommand} 归属判定在卸载时生效。<br>
     * Builds a SimpleCommand with its owning plugin so unload-time attribution via
     * {@link PluginIdentifiableCommand} works.
     *
     * @param owningPlugin 拥有该命令的插件；当 {@code object} 自身不是插件时可传 null。<br>
     *                     The plugin owning this command; {@code null} when {@code object} is not itself a plugin.
     */
    public SimpleCommand(Object object, Method method, Plugin owningPlugin, String name, String description, String usageMessage, String[] aliases) {
        super(name, description, usageMessage, aliases);
        this.object = object;
        this.method = method;
        this.owningPlugin = owningPlugin;
    }

    @Override
    public Plugin getPlugin() {
        return this.owningPlugin;
    }

    public void setForbidConsole(boolean forbidConsole) {
        this.forbidConsole = forbidConsole;
    }

    public void setMaxArgs(int maxArgs) {
        this.maxArgs = maxArgs;
    }

    public void setMinArgs(int minArgs) {
        this.minArgs = minArgs;
    }

    public void sendUsageMessage(CommandSender sender) {
        if (!this.usageMessage.isEmpty()) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
        }
    }

    public void sendInGameMessage(CommandSender sender) {
        sender.sendMessage(new TranslationContainer("commands.generic.ingame"));
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (this.forbidConsole && sender instanceof ConsoleCommandSender) {
            this.sendInGameMessage(sender);
            return false;
        } else if (!this.testPermission(sender)) {
            return false;
        } else if (this.maxArgs != 0 && args.length > this.maxArgs) {
            this.sendUsageMessage(sender);
            return false;
        } else if (this.minArgs != 0 && args.length < this.minArgs) {
            this.sendUsageMessage(sender);
            return false;
        }

        boolean success = false;

        try {
            success = (Boolean) this.method.invoke(this.object, sender, commandLabel, args);
        } catch (Exception exception) {
            Server.getInstance().getLogger().logException(exception);
        }

        if (!success) {
            this.sendUsageMessage(sender);
        }

        return success;
    }
}
