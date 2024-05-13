package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.network.protocol.StopSoundPacket;

import java.util.List;
import java.util.stream.Collectors;

public class StopSoundCommand extends VanillaCommand {

    public StopSoundCommand(String name) {
        super(name, "%nukkit.command.stopsound.description", "%commands.stopsound.usage");
        this.setPermission("nukkit.command.stopsound");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("player", CommandParamType.TARGET, false),
                new CommandParameter("sound", CommandParamType.STRING, true)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage(new TranslationContainer("%commands.stopsound.usage", this.usageMessage));
            return false;
        }

        List<Player> players = List.of(Player.EMPTY_ARRAY);
        if (args.length == 1) {
            if (args[0].equalsIgnoreCase("@a")) {
                players = Server.getInstance().getOnlinePlayers().values().stream().toList();
            } else if (args[0].equalsIgnoreCase("@s") && sender instanceof Player) {
                players = List.of((Player) sender);
            } else {
                Player p = Server.getInstance().getPlayer(args[0]);
                if (p == null) {
                    sender.sendMessage(new TranslationContainer("commands.generic.player.notFound"));
                    return false;
                }
                players = List.of(p);
            }
        }

        String sound = "";
        if (args.length > 1) {
            sound = args[1];
        }

        StopSoundPacket packet = new StopSoundPacket();
        packet.name = sound;
        if (sound.isEmpty()) {
            packet.stopAll = true;
        }
        Server.broadcastPacket(players, packet);
        String players_str = players.stream().map(Player::getName).collect(Collectors.joining(", "));
        if (packet.stopAll) {
            sender.sendMessage(new TranslationContainer("commands.stopsound.success.all", players_str));
        } else {
            sender.sendMessage(new TranslationContainer("commands.stopsound.success", sound, players_str));
        }
        return true;
    }
}