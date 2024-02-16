package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.Command;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.event.player.PlayerTeleportEvent;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.generator.Normal;

import java.util.HashMap;
import java.util.concurrent.ThreadLocalRandom;

public class WorldCommand extends Command {

    public WorldCommand(String name) {
        super(name, "%nukkit.command.world.description", "%nukkit.command.world.usage");
        this.setPermission("nukkit.command.world");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("world", CommandParamType.STRING, false)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        Server.getInstance().generateLevel("leveldbtest", ThreadLocalRandom.current().nextInt(), Normal.class, new HashMap<>(), LevelDBProvider.class);

        if (args.length == 0) {
            sender.sendMessage(new TranslationContainer("nukkit.command.world.list"));
            for (Level level : Server.getInstance().getLevels().values()) {
                sender.sendMessage(level.getName());
            }
            return true;
        }

        if (args.length == 2) {
            if (!sender.hasPermission("nukkit.command.world.others")) {
                return false;
            }

            if (Server.getInstance().getPlayer(args[1].replace("@s", sender.getName())) == null) {
                sender.sendMessage(new TranslationContainer("nukkit.command.world.unknownPlayer"));
                return true;
            }
            if (Server.getInstance().getLevelByName(args[0]) == null) {
                sender.sendMessage(new TranslationContainer("nukkit.command.world.unknownLevel"));
                return true;
            }
            Server.getInstance().getPlayer(args[1].replace("@s", sender.getName())).teleport(Server.getInstance().getLevelByName(args[0]).getSafeSpawn(), PlayerTeleportEvent.TeleportCause.COMMAND);
            return true;
        }

        if (!(sender instanceof Player)) {
            sender.sendMessage(new TranslationContainer("commands.generic.ingame"));
            return true;
        }

        if (args.length == 1) {
            if (Server.getInstance().getLevelByName(args[0]) != null) {
                ((Player) sender).teleport(Server.getInstance().getLevelByName(args[0]).getSafeSpawn(), PlayerTeleportEvent.TeleportCause.COMMAND);
            } else {
                sender.sendMessage(new TranslationContainer("nukkit.command.world.unknownLevel"));
            }
            return true;
        }

        return false;
    }
}
