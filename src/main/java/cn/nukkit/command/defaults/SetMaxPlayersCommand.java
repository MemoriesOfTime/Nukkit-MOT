package cn.nukkit.command.defaults;

import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.utils.CommandLogger;

import java.util.Map;

/**
 * @author PowerNukkitX Project Team
 */
public class SetMaxPlayersCommand extends VanillaCommand {

    public SetMaxPlayersCommand(String name) {
        super(name, "commands.setmaxplayers.description");
        this.setPermission("nukkit.command.setmaxplayers");
        this.getCommandParameters().clear();
        this.addCommandParameters("default", new CommandParameter[]{
                CommandParameter.newType("maxPlayers", false, CommandParamType.INT)
        });
        this.enableParamTree();
    }

    @Override
    public int execute(CommandSender sender, String commandLabel, Map.Entry<String, ParamList> result, CommandLogger log) {
        int maxPlayers = result.getValue().getResult(0);
        boolean lowerBound = false;

        if (maxPlayers < Server.getInstance().getOnlinePlayers().size()) {
            maxPlayers = Server.getInstance().getOnlinePlayers().size();
            lowerBound = true;
        }

        sender.getServer().setMaxPlayers(maxPlayers);
        log.addSuccess("commands.setmaxplayers.success", String.valueOf(maxPlayers));
        if (lowerBound) {
            log.addSuccess("commands.setmaxplayers.success.lowerbound");
        }
        log.output();
        return 1;
    }
}