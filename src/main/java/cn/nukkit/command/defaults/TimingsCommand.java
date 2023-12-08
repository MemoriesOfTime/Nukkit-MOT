package cn.nukkit.command.defaults;

import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;

/**
 * @author fromgate
 * @author Pub4Game
 */
@Deprecated
public class TimingsCommand extends VanillaCommand {

    public TimingsCommand(String name) {
        super(name, "%nukkit.command.timings.description", "%nukkit.command.timings.usage");
        this.setPermission("nukkit.command.timings");
        this.commandParameters.clear();
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        sender.sendMessage("§cTimings are deprecated. Please use Spark.");
        Server.getInstance().dispatchCommand(sender, "spark help");
        return true;
    }
}

