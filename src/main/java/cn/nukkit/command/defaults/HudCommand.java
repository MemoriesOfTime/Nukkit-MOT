package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.network.protocol.SetHudPacket;
import cn.nukkit.network.protocol.types.hub.HudElement;
import cn.nukkit.network.protocol.types.hub.HudVisibility;

public class HudCommand extends VanillaCommand {

    public HudCommand(String name) {
        super(name, "commands.hud.description", "%commands.hud.usage");
        this.setPermission("nukkit.command.hud");
        this.getCommandParameters().clear();
        this.addCommandParameters("default", new CommandParameter[]{
                CommandParameter.newType("player", false, CommandParamType.TARGET),
                CommandParameter.newEnum("visible", false, new String[]{"hide", "reset"}),
                CommandParameter.newEnum("hud_element", false, new String[]{"armor", "air_bubbles_bar", "crosshair", "food_bar", "health", "hotbar", "paper_doll", "tool_tips", "progress_bar", "touch_controls", "vehicle_health"})
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        Player player = Server.getInstance().getPlayerExact(args[0]);
        if (player == null) {
            sender.sendMessage("Player + " + args[0] + " + not found");
            return true;
        }
        if (args.length < 3) {
            return false;
        }

        HudVisibility visibility = switch (args[1]) {
            case "hide" -> HudVisibility.HIDE;
            case "reset" -> HudVisibility.RESET;
            default -> null;
        };

        HudElement element = switch (args[2]) {
            case "armor" -> HudElement.ARMOR;
            case "air_bubbles_bar" -> HudElement.AIR_BUBBLES_BAR;
            case "crosshair" -> HudElement.CROSSHAIR;
            case "food_bar" -> HudElement.FOOD_BAR;
            case "health" -> HudElement.HEALTH;
            case "hotbar" -> HudElement.HOTBAR;
            case "paper_doll" -> HudElement.PAPER_DOLL;
            case "tool_tips" -> HudElement.TOOL_TIPS;
            case "progress_bar" -> HudElement.PROGRESS_BAR;
            case "touch_controls" -> HudElement.TOUCH_CONTROLS;
            case "vehicle_health" -> HudElement.VEHICLE_HEALTH;

            default -> null;
        };

        if(visibility == null || element == null) {
            sender.sendMessage("Invalid visibility or element");
            return false;
        }

        SetHudPacket packet = new SetHudPacket();
        packet.elements.add(element);
        packet.visibility = visibility;
        player.dataPacket(packet);

        sender.sendMessage("HUD element " + element.name() + " is now " + visibility.name() + " for " + player.getName());

        return true;
    }
}
