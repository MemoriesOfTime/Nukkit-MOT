package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

/**
 * Created on 2015/11/11 by xtypr.
 * Package cn.nukkit.command.defaults in project Nukkit .
 */
public class InputPermissionCommand extends VanillaCommand {

    public InputPermissionCommand(String name) {
        super(name, "commands.inputpermission.description", "");
        this.setPermission("nukkit.command.inputpermission");
        this.commandParameters.clear();
        this.commandParameters.put("query", new CommandParameter[]{
                CommandParameter.newEnum("operation_type", false, new String[]{"query", "set"}),
                CommandParameter.newType("player", false, CommandParamType.TARGET),
                CommandParameter.newEnum("perm_type", false, new String[]{"camera", "movement"}),
        });
        this.commandParameters.put("set", new CommandParameter[]{
                CommandParameter.newEnum("operation_type", false, new String[]{"query", "set"}),
                CommandParameter.newType("player", false, CommandParamType.TARGET),
                CommandParameter.newEnum("perm_type", false, new String[]{"camera", "movement"}),
                CommandParameter.newEnum("value", true, new String[]{"disabled", "enabled"})
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return false;
        }
        String type = args[0];
        String name = args[1].replace("@s", sender.getName());
        Player player = Server.getInstance().getPlayer(name);
        if (player == null) {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.player.notFound"));
            return true;
        }
        switch (type) {
            case "query":
                if (args.length > 2) {
                    String permission_type = args[2];
                    switch (permission_type) {
                        case "camera" ->
                                sender.sendMessage(new TranslationContainer("commands.inputpermission.query", player.getName(), "%commands.inputpermission.camera", player.isLockCameraInput()? "%commands.inputpermission.disabled": "%commands.inputpermission.enabled"));
                        case "movement" ->
                                sender.sendMessage(new TranslationContainer("commands.inputpermission.query", player.getName(), "%commands.inputpermission.movement", player.isLockMovementInput()? "%commands.inputpermission.disabled": "%commands.inputpermission.enabled"));
                        default -> sender.sendMessage(new TextContainer("commands.inputpermission.set.missingpermission"));
                    }
                }
                break;
            case "set":
                if (args.length >= 4) {
                    String permission_type = args[2];
                    boolean lock = args[3].equals("disabled");
                    switch (permission_type) {
                        case "camera" -> player.setLockCameraInput(lock);
                        case "movement" -> player.setLockMovementInput(lock);
                        default -> {
                            sender.sendMessage(new TextContainer("commands.inputpermission.set.missingpermission"));
                            return false;
                        }
                    }
                    sender.sendMessage(new TranslationContainer("commands.inputpermission.set.outputoneplayer", player.getName(), "%commands.inputpermission." + permission_type, lock? "%commands.inputpermission.disabled": "%commands.inputpermission.enabled"));
                }
                break;
        }
        return true;
    }
}
