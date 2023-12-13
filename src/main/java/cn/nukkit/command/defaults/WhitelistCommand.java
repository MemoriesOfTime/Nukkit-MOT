package cn.nukkit.command.defaults;

import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

/**
 * Created on 2015/11/12 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class WhitelistCommand extends VanillaCommand {

    public WhitelistCommand(String name) {
        super(name, "%nukkit.command.allowlist.description", "nukkit.commands.allowlist.usage", new String[]{"allowlist"});
        this.setPermission("nukkit.command.allowlist.reload;" +
                "nukkit.command.allowlist.enable;" +
                "nukkit.command.allowlist.disable;" +
                "nukkit.command.allowlist.list;" +
                "nukkit.command.allowlist.add;" +
                "nukkit.command.allowlist.remove"
        );
        this.commandParameters.clear();
        this.commandParameters.put("1arg", new CommandParameter[]{
                CommandParameter.newEnum("action", new CommandEnum("AllowlistAction", "on", "off", "list", "reload"))
        });
        this.commandParameters.put("2args", new CommandParameter[]{
                CommandParameter.newEnum("action", new CommandEnum("AllowlistPlayerAction", "add", "remove")),
                CommandParameter.newType("player", CommandParamType.TARGET)
        });
    }


    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0 || args.length > 2) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return true;
        }

        if (args.length == 1) {
            if (badPerm(sender, args[0].toLowerCase())) {
                return false;
            }

            switch (args[0].toLowerCase()) {
                case "reload":
                    sender.getServer().reloadWhitelist();
                    broadcastCommandMessage(sender, new TranslationContainer("commands.allowlist.reloaded"));
                    break;
                case "on":
                    sender.getServer().setPropertyBoolean("white-list", true);
                    sender.getServer().setWhitelisted(true);
                    broadcastCommandMessage(sender, new TranslationContainer("commands.allowlist.enabled"));
                    break;
                case "off":
                    sender.getServer().setPropertyBoolean("white-list", false);
                    sender.getServer().setWhitelisted(false);
                    broadcastCommandMessage(sender, new TranslationContainer("commands.allowlist.disabled"));
                    break;
                case "list":
                    StringBuilder result = new StringBuilder();
                    int count = 0;
                    for (String player : sender.getServer().getWhitelist().getAll().keySet()) {
                        result.append(player).append(", ");
                        ++count;
                    }

                    sender.sendMessage(new TranslationContainer("commands.allowlist.list", String.valueOf(count), String.valueOf(count)));

                    if (result.length() > 0) {
                        sender.sendMessage(result.substring(0, result.length() - 2));
                    }
                    break;
                case "add":
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "%commands.allowlist.add.usage"));
                    break;
                case "remove":
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "%commands.allowlist.remove.usage"));
                    break;
            }
            return true;
        }

        if (badPerm(sender, args[0].toLowerCase())) {
            return false;
        }

        switch (args[0].toLowerCase()) {
            case "add":
                sender.getServer().getOfflinePlayer(args[1]).setWhitelisted(true);
                broadcastCommandMessage(sender, new TranslationContainer("commands.allowlist.add.success", args[1]));
                break;
            case "remove":
                sender.getServer().getOfflinePlayer(args[1]).setWhitelisted(false);
                broadcastCommandMessage(sender, new TranslationContainer("commands.allowlist.remove.success", args[1]));
                break;
        }

        return true;
    }

    private static boolean badPerm(CommandSender sender, String perm) {
        if (!sender.hasPermission("nukkit.command.allowlist." + perm)) {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.permission"));
            return true;
        }

        return false;
    }
}
