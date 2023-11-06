package cn.nukkit.command.defaults;

import cn.nukkit.Player;
import cn.nukkit.command.CommandSender;
import cn.nukkit.command.data.CommandEnum;
import cn.nukkit.command.data.CommandParamType;
import cn.nukkit.command.data.CommandParameter;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerOffhandInventory;
import cn.nukkit.item.Item;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.utils.TextFormat;

import java.util.Map;

public class ClearCommand extends VanillaCommand {

    public ClearCommand(String name) {
        super(name, "commands.clear.description", "commands.clear.usage");
        this.setPermission("nukkit.command.clear");
        this.commandParameters.clear();
        this.commandParameters.put("default", new CommandParameter[]{
                CommandParameter.newType("player", true, CommandParamType.TARGET),
                CommandParameter.newEnum("itemName", true, CommandEnum.ENUM_ITEM),
                CommandParameter.newType("data", true, CommandParamType.INT),
                CommandParameter.newType("maxCount", true, CommandParamType.INT)
        });
    }

    @Override
    public boolean execute(CommandSender sender, String commandLabel, String[] args) {
        if (!this.testPermission(sender)) {
            return false;
        }

        Player target;
        int maxCount = -1;

        Item item = null;

        if (args.length > 0) {
            target = sender.getServer().getPlayer(args[0]);

            if (args.length > 1) {
                String id = args[1];
                int meta = 0;

                if (args.length > 2) {
                    meta = Integer.parseInt(args[2]);
                    if (args.length > 3) {
                        maxCount = Integer.parseInt(args[3]);
                    }
                }

                item = Item.fromString(id + ':' + meta);
            }
        } else if (sender.isPlayer()) {
            target = (Player) sender;
        } else {
            sender.sendMessage(new TranslationContainer("commands.generic.usage", this.usageMessage));
            return false;
        }

        if (target != null) {
            PlayerInventory inventory = target.getInventory();
            PlayerOffhandInventory offhand = target.getOffhandInventory();

            if (item == null) {
                int count = 0;

                for (Map.Entry<Integer, Item> entry : inventory.getContents().entrySet()) {
                    Item slot = entry.getValue();
                    if (!slot.isNull()) {
                        count += slot.getCount();
                        inventory.clear(entry.getKey());
                    }
                }

                Item slot = offhand.getItem(0);
                if (!slot.isNull()) {
                    count += slot.getCount();
                    offhand.clear(0);
                }

                if (count == 0) {
                    sender.sendMessage(new TranslationContainer(TextFormat.RED + "commands.clear.failure.no.items", target.getName()));
                } else {
                    sender.sendMessage(new TranslationContainer("commands.clear.success", target.getName(), String.valueOf(count)));
                }
            } else if (maxCount == 0) {
                int count = 0;

                for (Map.Entry<Integer, Item> entry : inventory.getContents().entrySet()) {
                    Item slot = entry.getValue();

                    if (item.equals(slot, item.hasMeta(), false)) {
                        count += slot.getCount();
                    }
                }

                Item slot = offhand.getItem(0);
                if (item.equals(slot, item.hasMeta(), false)) {
                    count += slot.getCount();
                }

                if (count == 0) {
                    sender.sendMessage(new TranslationContainer(TextFormat.RED + "commands.clear.failure.no.items", target.getName()));
                    return false;
                }

                sender.sendMessage(new TranslationContainer("commands.clear.testing", target.getName(), String.valueOf(count)));
            } else if (maxCount == -1) {
                int count = 0;

                for (Map.Entry<Integer, Item> entry : inventory.getContents().entrySet()) {
                    Item slot = entry.getValue();

                    if (item.equals(slot, item.hasMeta(), false)) {
                        count += slot.getCount();
                        inventory.clear(entry.getKey());
                    }
                }

                Item slot = offhand.getItem(0);
                if (item.equals(slot, item.hasMeta(), false)) {
                    count += slot.getCount();
                    offhand.clear(0);
                }

                if (count == 0) {
                    sender.sendMessage(new TranslationContainer(TextFormat.RED + "commands.clear.failure.no.items", target.getName()));
                    return false;
                }

                sender.sendMessage(new TranslationContainer("commands.clear.success", target.getName(), String.valueOf(count)));
            } else {
                int remaining = maxCount;

                for (Map.Entry<Integer, Item> entry : inventory.getContents().entrySet()) {
                    Item slot = entry.getValue();

                    if (item.equals(slot, item.hasMeta(), false)) {
                        int count = slot.getCount();
                        int amount = Math.min(count, remaining);

                        slot.setCount(count - amount);
                        inventory.setItem(entry.getKey(), slot);

                        if ((remaining -= amount) <= 0) {
                            break;
                        }
                    }
                }

                if (remaining > 0) {
                    Item slot = offhand.getItem(0);
                    if (item.equals(slot, item.hasMeta(), false)) {
                        int count = slot.getCount();
                        int amount = Math.min(count, remaining);

                        slot.setCount(count - amount);
                        inventory.setItem(0, slot);
                        remaining -= amount;
                    }
                }

                if (remaining == maxCount) {
                    sender.sendMessage(new TranslationContainer(TextFormat.RED + "commands.clear.failure.no.items", target.getName()));
                    return false;
                }

                sender.sendMessage(new TranslationContainer("commands.clear.success", target.getName(), String.valueOf(maxCount - remaining)));
            }
        }

        return true;
    }
}
