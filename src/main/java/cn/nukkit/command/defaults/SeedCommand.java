package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.command.tree.ParamList;
import cn.nukkit.command.utils.CommandLogger;

import java.util.Map;

/**
 * @author MagicDroidX (Nukkit Project)
 */
public class SeedCommand extends VanillaCommand {

    public SeedCommand(String name) {
        super(name, "Show the level's seed");//no translation in client
        this.setPermission("nukkit.command.seed");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[0]);
        this.enableParamTree();
    }

    @Override
    public int execute(CommandSender sender, String commandLabel, Map.Entry<String, ParamList> result, CommandLogger log) {
        long seed;
        if (sender instanceof Player player) {
            seed = player.getLevel().getSeed();
        } else {
            seed = sender.getServer().getDefaultLevel().getSeed();
        }
        log.addSuccess("commands.seed.success", String.valueOf(seed)).output();
        return 1;
    }
}