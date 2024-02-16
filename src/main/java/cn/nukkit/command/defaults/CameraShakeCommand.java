package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.network.protocol.CameraShakePacket;

public class CameraShakeCommand extends VanillaCommand {

    public CameraShakeCommand(String name) {
        super(name, "commands.screenshake.description");
        this.setPermission("nukkit.command.camerashake");
        this.commandParameters.clear();
        this.commandParameters.put("add", new CommandParameter[]{
                CommandParameter.newEnum("add", false, new String[]{"add"}),
                CommandParameter.newType("player", false, CommandParamType.TARGET),
                CommandParameter.newType("intensity", false, CommandParamType.FLOAT),
                CommandParameter.newType("second", false, CommandParamType.FLOAT),
                CommandParameter.newEnum("shakeType", false, new String[]{"positional", "rotational"})
        });
        this.commandParameters.put("stop", new CommandParameter[]{
                CommandParameter.newEnum("stop", false, new String[]{"stop"}),
                CommandParameter.newType("player", false, CommandParamType.TARGET),
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (args.length == 0) {
            return false;
        }
        switch (args[0]) {
            // todo: add messages to notice sender & add translations
            case "add" -> {
                if (args.length != 5) {
                    return false;
                }
                String players_str = args[1];
                float intensity = Float.parseFloat(args[2]);
                float second = Float.parseFloat(args[3]);
                String type = args[4];
                CameraShakePacket.CameraShakeType shakeType = switch (type) {
                    case "positional" -> CameraShakePacket.CameraShakeType.POSITIONAL;
                    case "rotational" -> CameraShakePacket.CameraShakeType.ROTATIONAL;
                    default -> null;
                };
                CameraShakePacket packet = new CameraShakePacket();
                packet.intensity = intensity;
                packet.duration = second;
                packet.shakeType = shakeType;
                packet.shakeAction = CameraShakePacket.CameraShakeAction.ADD;
                Player player = Server.getInstance().getPlayer(players_str);
                if (player != null) {
                    player.dataPacket(packet);
                }
            }
            case "stop" -> {
                if (args.length != 2) {
                    return false;
                }
                String players_str = args[1];
                CameraShakePacket packet = new CameraShakePacket();
                packet.shakeAction = CameraShakePacket.CameraShakeAction.STOP;
                //avoid NPE
                packet.intensity = -1;
                packet.duration = -1;
                packet.shakeType = CameraShakePacket.CameraShakeType.POSITIONAL;
                Player player = Server.getInstance().getPlayer(players_str);
                if (player != null) {
                    player.dataPacket(packet);
                }
            }
        }
        return true;
    }
}
