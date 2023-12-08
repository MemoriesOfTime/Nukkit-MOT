package cn.nukkit.command.defaults;

import cn.nukkit.Nukkit;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginDescription;
import cn.nukkit.utils.TextFormat;

import java.util.List;

/**
 * Created on 2015/11/12 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class VersionCommand extends VanillaCommand {

    public VersionCommand(String name) {
        super(name,
                "%nukkit.command.version.description",
                "%nukkit.command.version.usage",
                new String[]{"ver", "about"}
        );
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newType("pluginName", true, CommandParamType.STRING)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (args.length == 0 || !sender.hasPermission("nukkit.command.version.plugins")) {
            final String branch = Nukkit.getBranch();

            sender.sendMessage("§e#########################################\n§cNukkit§3-§dMOT\n§6Build: §b" + branch + '/' + Nukkit.VERSION.substring(4) + "\n§6Multiversion: §bUp to version " + ProtocolInfo.MINECRAFT_VERSION_NETWORK + "\n§e#########################################");

            if (sender.isOp()) {
                sender.sendMessage("§c[Nukkit-MOT] You are using the java8 branch, this branch may stop being maintained in the future");
                sender.sendMessage("§c[Nukkit-MOT] it is recommended that you update to java17 and switch to the master branch!");
                sender.sendMessage("§c[Nukkit-MOT] 您正在使用java8分支核心，该分支可能会在以后停止维护");
                sender.sendMessage("§c[Nukkit-MOT] 建议您更新到java17并切换到master分支核心!");
            }
            return true;
        }

        final String pluginName = String.join(" ", args);
        Plugin exactPlugin = sender.getServer().getPluginManager().getPlugin(pluginName);
        boolean found = false;

        if (exactPlugin == null) {
            final String lowerName = pluginName.toLowerCase();
            for (Plugin p : sender.getServer().getPluginManager().getPlugins().values()) {
                if (p.getName().toLowerCase().contains(lowerName)) {
                    exactPlugin = p;
                    found = true;
                }
            }
        } else {
            found = true;
        }

        if (found) {
            PluginDescription desc = exactPlugin.getDescription();
            sender.sendMessage(TextFormat.DARK_GREEN + desc.getName() + TextFormat.WHITE + " version " + TextFormat.DARK_GREEN + desc.getVersion());
            if (desc.getDescription() != null) {
                sender.sendMessage(desc.getDescription());
            }
            if (desc.getWebsite() != null) {
                sender.sendMessage("Website: " + desc.getWebsite());
            }
            List<String> authors = desc.getAuthors();
            if (authors.size() == 1) {
                sender.sendMessage("Author: " + authors.get(0));
            } else if (authors.size() >= 2) {
                sender.sendMessage("Authors: " + String.join(", ", authors));
            }
        } else {
            sender.sendMessage(new TranslationContainer("nukkit.command.version.noSuchPlugin"));
        }
        return true;
    }
}
