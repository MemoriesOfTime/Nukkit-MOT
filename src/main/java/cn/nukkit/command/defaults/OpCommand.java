package cn.nukkit.command.defaults;

import cn.nukkit.IPlayer;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

/**
 * Created on 2015/11/12 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class OpCommand extends VanillaCommand {

    public OpCommand(String name) {
        super(name, "%nukkit.command.op.description", "%nukkit.command.op.usage");
        this.setPermission("nukkit.command.op.give");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("player", CommandParamType.TARGET, false)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        
        if (sender instanceof Player && !(Server.getInstance().opInGame)) {
            sender.sendMessage("\u00A7cCan't use this command in game");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return false;
        }

        String name = args[0];
        IPlayer player = sender.getServer().getOfflinePlayer(name);

        broadcastCommandMessage(sender, new TranslationContainer("commands.op.success", player.getName()));
        if (player instanceof Player) {
            ((Player) player).sendMessage(new TranslationContainer(TextFormat.GRAY + "%commands.op.message"));
        }

        player.setOp(true);

        return true;
    }
}
