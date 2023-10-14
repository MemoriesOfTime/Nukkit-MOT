package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

/**
 * Created by Pub4Game on 23.01.2016.
 */
public class EnchantCommand extends VanillaCommand {

    public EnchantCommand(String name) {
        super(name, "%nukkit.command.enchant.description", "%commands.enchant.usage");
        this.setPermission("nukkit.command.enchant");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                new CommandParameter("player", CommandParamType.TARGET, false),
                new CommandParameter("enchantment ID", CommandParamType.INT, false),
                new CommandParameter("level", CommandParamType.INT, true)
        });
        this.commandParameters.put("byName", new CommandParameter[]{
                CommandParameter.newType("player", CommandParamType.TARGET),
                CommandParameter.newEnum("enchantmentName", new CommandEnum("Enchant",
                        "protection", "fire_protection", "feather_falling", "blast_protection", "projectile_protection", "thorns", "respiration",
                        "aqua_affinity", "depth_strider", "sharpness", "smite", "bane_of_arthropods", "knockback", "fire_aspect", "looting", "efficiency",
                        "silk_touch", "durability", "fortune", "power", "punch", "flame", "infinity", "luck_of_the_sea", "lure", "frost_walker", "mending",
                        "binding_curse", "vanishing_curse", "impaling", "loyalty", "riptide", "channeling", "multishot", "piercing", "quick_charge",
                        "soul_speed")),
                CommandParameter.newType("level", true, CommandParamType.INT)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return true;
        }
        Player player = sender.getServer().getPlayer(args[0].replace("@s", sender.getName()));
        if (player == null) {
            sender.sendMessage(new TranslationContainer(TextFormat.RED + "%commands.generic.player.notFound"));
            return true;
        }
        int enchantId;
        int enchantLevel;
        try {
            enchantId = getIdByName(args[1]);
            enchantLevel = args.length == 3 ? Integer.parseInt(args[2]) : 1;
        } catch (NumberFormatException e) {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return true;
        }
        Enchantment enchantment = Enchantment.getEnchantment(enchantId);
        if (enchantment == null) {
            sender.sendMessage(new TranslationContainer("commands.enchant.notFound", String.valueOf(enchantId)));
            return true;
        }
        enchantment.setLevel(enchantLevel);
        Item item = player.getInventory().getItemInHand();
        if (item.getId() <= 0) {
            sender.sendMessage(new TranslationContainer("commands.enchant.noItem"));
            return true;
        }
        item.addEnchantment(enchantment);
        player.getInventory().setItemInHand(item);
        broadcastCommandMessage(sender, new TranslationContainer("%commands.enchant.success"));
        return true;
    }

    public int getIdByName(String value) throws NumberFormatException {
        value = value.toLowerCase();
        return switch (value) {
            case "protection" -> 0;
            case "fire_protection" -> 1;
            case "feather_falling" -> 2;
            case "blast_protection" -> 3;
            case "projectile_protection" -> 4;
            case "thorns" -> 5;
            case "respiration" -> 6;
            case "depth_strider" -> 7;
            case "aqua_affinity" -> 8;
            case "sharpness" -> 9;
            case "smite" -> 10;
            case "bane_of_arthropods" -> 11;
            case "knockback" -> 12;
            case "fire_aspect" -> 13;
            case "looting" -> 14;
            case "efficiency" -> 15;
            case "silk_touch" -> 16;
            case "durability", "unbreaking" -> 17;
            case "fortune" -> 18;
            case "power" -> 19;
            case "punch" -> 20;
            case "flame" -> 21;
            case "infinity" -> 22;
            case "luck_of_the_sea" -> 23;
            case "lure" -> 24;
            case "frost_walker" -> 25;
            case "mending" -> 26;
            case "binding_curse" -> 27;
            case "vanishing_curse" -> 28;
            case "impaling" -> 29;
            case "riptide" -> 30;
            case "loyalty" -> 31;
            case "channeling" -> 32;
            case "multishot" -> 33;
            case "piercing" -> 34;
            case "quick_charge" -> 35;
            case "soul_speed" -> 36;
            case "swift_sneak" -> 37;
            default -> Integer.parseInt(value);
        };
    }
}
