package cn.nukkit.command;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.data.CommandData;
import cn.nukkit.command.data.CommandDataVersions;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandOverload;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.tree.ParamTree;
import cn.nukkit.command.utils.CommandLogger;
import cn.nukkit.lang.PluginI18nManager;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.permission.Permissible;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.PluginBase;
import cn.nukkit.utils.TextFormat;
import io.netty.util.internal.EmptyArrays;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * @author MagicDroidX (Nukkit Project)
 */
public abstract class Command {

    private final String name;

    private String nextLabel;

    private String label;

    private String[] aliases;

    private String[] activeAliases;

    private CommandMap commandMap;

    protected String description;

    protected String usageMessage;

    private String permission;

    private String permissionMessage;

    protected Map<String, CommandParameter[]> commandParameters = new HashMap<>();


    protected ParamTree paramTree;

    protected CommandData commandData;

    protected boolean serverSideOnly;

    public Command(String name) {
        this(name, "", null, EmptyArrays.EMPTY_STRINGS);
    }

    public Command(String name, String description) {
        this(name, description, null, EmptyArrays.EMPTY_STRINGS);
    }

    public Command(String name, String description, String usageMessage) {
        this(name, description, usageMessage, EmptyArrays.EMPTY_STRINGS);
    }

    public Command(String name, String description, String usageMessage, String[] aliases) {
        this.commandData = new CommandData();
        this.name = name.toLowerCase(Locale.ENGLISH); // Uppercase letters crash the client?!?
        this.nextLabel = name;
        this.label = name;
        this.description = description;
        this.usageMessage = usageMessage == null ? "/" + name : usageMessage;
        this.aliases = aliases;
        this.activeAliases = aliases;
        this.commandParameters.put("default", new CommandParameter[]{CommandParameter.newType("args", true, CommandParamType.RAWTEXT)});
    }

    /**
     * Returns an CommandData containing command data
     *
     * @return CommandData
     */
    public CommandData getDefaultCommandData() {
        return this.commandData;
    }

    public CommandParameter[] getCommandParameters(String key) {
        return commandParameters.get(key);
    }

    public Map<String, CommandParameter[]> getCommandParameters() {
        return commandParameters;
    }

    public void setCommandParameters(Map<String, CommandParameter[]> commandParameters) {
        this.commandParameters = commandParameters;
    }

    public void addCommandParameters(String key, CommandParameter[] parameters) {
        this.commandParameters.put(key, parameters);
    }

    /**
     * Generates modified command data for the specified player
     * for AvailableCommandsPacket.
     *
     * @param player player
     * @return CommandData|null
     */
    public CommandDataVersions generateCustomCommandData(Player player) {
        if (!this.testPermission(player)) {
            return null;
        }

        var plugin = this instanceof PluginCommand<?> pluginCommand ? pluginCommand.getPlugin() : InternalPlugin.INSTANCE;

        CommandData customData = this.commandData.clone();

        if (getAliases().length > 0) {
            List<String> aliases = new ArrayList<>(Arrays.asList(getAliases()));
            if (!aliases.contains(this.name)) {
                aliases.add(this.name);
            }

            customData.aliases = new CommandEnum(this.name + "Aliases", aliases);
        }

        if (plugin == InternalPlugin.INSTANCE) {
            customData.description = player.getServer().getLanguage().translateString(this.getDescription());
        } else if (plugin instanceof PluginBase pluginBase) {
            var i18n = PluginI18nManager.getI18n(pluginBase);
            if (i18n != null) {
                customData.description = i18n.tr(player.getLanguageCode(), this.getDescription());
            } else {
                customData.description = player.getServer().getLanguage().translateString(this.getDescription());
            }
        }

        this.commandParameters.forEach((key, params) -> {
            CommandOverload overload = new CommandOverload();
            overload.input.parameters = params;
            customData.overloads.put(key, overload);
        });

        if (customData.overloads.isEmpty()) {
            customData.overloads.put("default", new CommandOverload());
        }

        CommandDataVersions versions = new CommandDataVersions();
        versions.versions.add(customData);
        return versions;
    }

    public Map<String, CommandOverload> getOverloads() {
        return commandData.overloads;
    }

    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        throw new UnsupportedOperationException();
    }

    /**
     * Execute int.
     *
     * @param sender       命令发送者
     * @param commandLabel the command label
     * @param result       解析的命令结果
     * @param log          命令输出工具
     * @return int 返回0代表执行失败, 返回大于等于1代表执行成功
     */
    public int execute(CommandSender sender, String commandLabel, Map.Entry<String, ParamList> result, CommandLogger log) {
        throw new UnsupportedOperationException();
    }

    public String getName() {
        return name;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }

    public boolean testPermission(CommandSender target) {
        if (this.testPermissionSilent(target)) {
            return true;
        }

        if (this.permissionMessage == null) {
            target.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.unknown", this.name));
        } else if (!this.permissionMessage.isEmpty()) {
            target.sendMessage(this.permissionMessage.replace("<permission>", this.permission));
        }

        return false;
    }

    public boolean testPermissionSilent(CommandSender target) {
        if (this.permission == null || this.permission.isEmpty()) {
            return true;
        }

        String[] permissions = this.permission.split(";");
        for (String permission : permissions) {
            if (target.hasPermission(permission)) {
                return true;
            }
        }

        return false;
    }

    public String getLabel() {
        return label;
    }

    public boolean setLabel(String name) {
        this.nextLabel = name;
        if (!this.isRegistered()) {
            this.label = name;
            return true;
        }
        return false;
    }

    public boolean register(CommandMap commandMap) {
        if (this.allowChangesFrom(commandMap)) {
            this.commandMap = commandMap;
            return true;
        }
        return false;
    }

    public boolean unregister(CommandMap commandMap) {
        if (this.allowChangesFrom(commandMap)) {
            this.commandMap = null;
            this.activeAliases = this.aliases;
            this.label = this.nextLabel;
            return true;
        }
        return false;
    }

    public boolean allowChangesFrom(CommandMap commandMap) {
        return commandMap != null && !commandMap.equals(this.commandMap);
    }

    public boolean isRegistered() {
        return this.commandMap != null;
    }

    public String[] getAliases() {
        return this.activeAliases;
    }

    public String getPermissionMessage() {
        return permissionMessage;
    }

    public String getDescription() {
        return description;
    }

    public String getUsage() {
        return usageMessage;
    }

    public boolean isServerSideOnly() {
        return serverSideOnly;
    }

    public String getCommandFormatTips() {
        StringBuilder builder = new StringBuilder();
        for (String form : this.getCommandParameters().keySet()) {
            CommandParameter[] commandParameters = this.getCommandParameters().get(form);
            builder.append("- /" + this.getName());
            for (CommandParameter commandParameter : commandParameters) {
                if (!commandParameter.optional) {
                    if (commandParameter.enumData == null) {
                        builder.append(" <").append(commandParameter.name + ": " + commandParameter.type.name().toLowerCase(Locale.ENGLISH)).append(">");
                    } else {
                        builder.append(" <").append(commandParameter.enumData.getValues().subList(0, Math.min(commandParameter.enumData.getValues().size(), 10)).stream().collect(Collectors.joining("|"))).append(commandParameter.enumData.getValues().size() > 10 ? "|..." : "").append(">");
                    }
                } else {
                    if (commandParameter.enumData == null) {
                        builder.append(" [").append(commandParameter.name + ": " + commandParameter.type.name().toLowerCase(Locale.ENGLISH)).append("]");
                    } else {
                        builder.append(" [").append(commandParameter.enumData.getValues().subList(0, Math.min(commandParameter.enumData.getValues().size(), 10)).stream().collect(Collectors.joining("|"))).append(commandParameter.enumData.getValues().size() > 10 ? "|..." : "").append("]");
                    }
                }
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    public void setAliases(String[] aliases) {
        this.aliases = aliases;
        if (!this.isRegistered()) {
            this.activeAliases = aliases;
        }
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public void setPermissionMessage(String permissionMessage) {
        this.permissionMessage = permissionMessage;
    }

    public void setUsage(String usageMessage) {
        this.usageMessage = usageMessage;
    }

    public boolean hasParamTree() {
        return this.paramTree != null;
    }

    /**
     * 若调用此方法，则将启用ParamTree用于解析命令参数
     */
    public void enableParamTree() {
        this.paramTree = new ParamTree(this);
    }

    public ParamTree getParamTree() {
        return paramTree;
    }

    public static void broadcastCommandMessage(CommandSender source, String message) {
        broadcastCommandMessage(source, message, true);
    }

    public static void broadcastCommandMessage(CommandSender source, String message, boolean sendToSource) {
        Set<Permissible> users = source.getServer().getPluginManager().getPermissionSubscriptions(Server.BROADCAST_CHANNEL_ADMINISTRATIVE);

        TranslationContainer result = new TranslationContainer("chat.type.admin", source.getName(), message);

        TranslationContainer colored = new TranslationContainer(TextFormat.GRAY + "" + TextFormat.ITALIC + "%chat.type.admin", source.getName(), message);

        if (sendToSource && !(source instanceof ConsoleCommandSender)) {
            source.sendMessage(message);
        }

        for (Permissible user : users) {
            if (user instanceof CommandSender sender) {
                if (user instanceof ConsoleCommandSender consoleSender) {
                    consoleSender.sendMessage(result);
                } else if (!user.equals(source)) {
                    sender.sendMessage(colored);
                }
            }
        }
    }

    public static void broadcastCommandMessage(CommandSender source, TextContainer message) {
        broadcastCommandMessage(source, message, true);
    }

    public static void broadcastCommandMessage(CommandSender source, TextContainer message, boolean sendToSource) {
        TextContainer m = message.clone();
        String resultStr = "[" + source.getName() + ": " + (!m.getText().equals(source.getServer().getLanguage().get(m.getText())) ? "%" : "") + m.getText() + "]";

        Set<Permissible> users = source.getServer().getPluginManager().getPermissionSubscriptions(Server.BROADCAST_CHANNEL_ADMINISTRATIVE);

        String coloredStr = TextFormat.GRAY + "" + TextFormat.ITALIC + resultStr;

        m.setText(resultStr);
        TextContainer result = m.clone();
        m.setText(coloredStr);
        TextContainer colored = m.clone();

        if (sendToSource && !(source instanceof ConsoleCommandSender)) {
            source.sendMessage(message);
        }

        for (Permissible user : users) {
            if (user instanceof CommandSender sender) {
                if (user instanceof ConsoleCommandSender consoleSender) {
                    consoleSender.sendMessage(result);
                } else if (!user.equals(source)) {
                    sender.sendMessage(colored);
                }
            }
        }
    }

    @Override
    public String toString() {
        return this.name;
    }
}