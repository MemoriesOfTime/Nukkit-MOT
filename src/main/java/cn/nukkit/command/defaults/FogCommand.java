package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.network.protocol.PlayerFogPacket;
import cn.nukkit.utils.Identifier;

import java.util.ArrayList;
import java.util.List;

public class FogCommand extends VanillaCommand {

    public FogCommand(String name) {
        super(name, "commands.fog.description", "commands.fog.usage");
        this.setPermission("nukkit.command.fog");
        this.commandParameters.clear();
        this.commandParameters.put("push", new CommandParameter[]{
                CommandParameter.newType("victim", false, CommandParamType.TARGET),
                CommandParameter.newEnum("push", new String[]{"push"}),
                CommandParameter.newType("fogId", CommandParamType.STRING),
                CommandParameter.newType("userProvidedId", CommandParamType.STRING)
        });
        this.commandParameters.put("delete", new CommandParameter[]{
                CommandParameter.newType("victim", false, CommandParamType.TARGET),
                CommandParameter.newEnum("mode", new CommandEnum("delete", "pop", "remove")),
                CommandParameter.newType("userProvidedId", CommandParamType.STRING)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return false;
        }

        if (args.length < 2) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", "/fog <victim: target> push <fogId: string> <userProvidedId: string>"));
            return false;
        }

        Player player;
        if ("@s".equalsIgnoreCase(args[0]) && sender instanceof Player) {
            player = (Player) sender;
        } else {
            player = Server.getInstance().getPlayer(args[0]);
        }
        switch (args[1]) {
            case "push":
                if (args.length < 4) {
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "/fog <victim: target> push <fogId: string> <userProvidedId: string>"));
                    return false;
                }
                String fogIdStr = args[2];
                var fogId = Identifier.tryParse(fogIdStr);
                if (fogId == null) {
                    sender.sendMessage(new TranslationContainer("commands.fog.invalidFogId", fogIdStr));
                    return false;
                }
                String userProvidedId = args[3];
                PlayerFogPacket.Fog fog = new PlayerFogPacket.Fog(fogId, userProvidedId);
                player.getFogStack().add(fog);
                player.sendFogStack();
                sender.sendMessage(new TranslationContainer("commands.fog.success.push", userProvidedId, fogIdStr));
                return true;
            case "pop":
                if (args.length < 3) {
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "/fog <victim: target> <mode: delete> <userProvidedId: string>"));
                    return false;
                }
                String userProvidedId = args[2];
                List<PlayerFogPacket.Fog> fogStack = player.getFogStack();
                for (int i = fogStack.size() - 1; i >= 0; i--) {
                    var fog = fogStack.get(i);
                    if (fog.userProvidedId().equals(userProvidedId)) {
                        fogStack.remove(fog);
                        player.sendFogStack();
                        sender.sendMessage(new TranslationContainer("commands.fog.success.pop", userProvidedId, fog.identifier().toString()));
                        return true;
                    }
                }
                sender.sendMessage(new TranslationContainer("commands.fog.invalidUserId", userProvidedId));
                return false;
            case "remove":
                if (args.length < 3) {
                    sender.sendMessage(new TranslationContainer("commands.generic.usage", "/fog <victim: target> <mode: delete> <userProvidedId: string>"));
                    return false;
                }
                String userProvidedId = args[2];
                var fogStack = player.getFogStack();
                List<PlayerFogPacket.Fog> shouldRemoved = new ArrayList<>();
                for (PlayerFogPacket.Fog fog : fogStack) {
                    if (fog.userProvidedId().equals(userProvidedId)) {
                        shouldRemoved.add(fog);
                        sender.sendMessage(new TranslationContainer("commands.fog.success.remove", userProvidedId, fog.identifier().toString()));
                    }
                }
                fogStack.removeAll(shouldRemoved);
                player.sendFogStack();
                if (shouldRemoved.isEmpty()) {
                    sender.sendMessage(new TranslationContainer("commands.fog.invalidUserId", userProvidedId));
                    return false;
                }
                return true;
            default:
                sender.sendMessage(new TranslationContainer("commands.generic.usage", "/fog <victim: target> push <fogId: string> <userProvidedId: string>"));
                return false;
        }
    }
}
