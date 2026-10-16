package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDurable;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.tag.*;

import java.util.List;
import java.util.Set;
import java.util.UUID;

public class RepairItemRecipe extends MultiRecipe {

    public RepairItemRecipe(){
        super(UUID.fromString(TYPE_REPAIR_ITEM));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getCount() != 1) {
            return false;
        }
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            if (item1.getCount() == 1 && item2.getCount() == 1 && item1.equals(item2, false, false)) {
                if (item1 instanceof ItemDurable) {
                    int damage = calculateDamage(item1.getDamage(), item2.getDamage(), item1.getMaxDurability());
                    if (matchesRepairOutput(outputItem, item1, item2)) {
                        return outputItem.getDamage() == damage;
                    }
                }
            }
        } else if (inputs.size() == 1) {
            Item item = inputs.get(0);
            if (item.getCount() == 2) {
                if (item instanceof ItemDurable) {
                    int damage = calculateDamage(item.getDamage(), item.getDamage(), item.getMaxDurability());
                    if (matchesRepairOutput(outputItem, item, item)) {
                        return outputItem.getDamage() == damage;
                    }
                }
            }
        }
        return false;
    }

    private static boolean matchesRepairOutput(Item output, Item first, Item second) {
        if (output.equals(first, false, true)) {
            return true;
        }
        // This is provisional validation. Custom metadata is checked again on the
        // authoritative post-event result, before either inventory path consumes inputs.
        return vanillaRepairMetadata(first) && vanillaRepairMetadata(second)
                && output.equals(first.clone().clearNamedTag(), false, true);
    }

    public static boolean preservesInputMetadata(Item output, List<Item> inputs) {
        if (output == null || output.isNull()) {
            return false;
        }
        CompoundTag outputTag = output.hasCompoundTag() ? output.getNamedTag() : new CompoundTag();
        for (Item input : inputs) {
            if (input == null || !input.hasCompoundTag()) {
                continue;
            }
            for (var entry : input.getNamedTag().getTags().entrySet()) {
                if (!vanillaRepairTag(entry.getKey(), entry.getValue())
                        && !entry.getValue().equals(outputTag.get(entry.getKey()))) {
                    return false;
                }
            }
        }
        return true;
    }

    private static boolean vanillaRepairMetadata(Item item) {
        if (!item.hasCompoundTag()) {
            return true;
        }
        for (var entry : item.getNamedTag().getTags().entrySet()) {
            if (!vanillaRepairTag(entry.getKey(), entry.getValue())) {
                return false;
            }
        }
        return true;
    }

    private static boolean vanillaRepairTag(String name, Tag tag) {
        if (name.equals("RepairCost")) {
            return tag instanceof IntTag;
        }
        if (name.equals("display")) {
            return tag instanceof CompoundTag display
                    && Set.of("Name").containsAll(display.getTags().keySet())
                    && (!display.contains("Name") || display.get("Name") instanceof StringTag);
        }
        if (!name.equals("ench") || !(tag instanceof ListTag<?> enchantments)) {
            return false;
        }
        for (Tag entry : enchantments.getAll()) {
            if (!(entry instanceof CompoundTag enchantment)
                    || !Set.of("id", "lvl").equals(enchantment.getTags().keySet())
                    || !(enchantment.get("id") instanceof ShortTag)
                    || !(enchantment.get("lvl") instanceof ShortTag)) {
                return false;
            }
            int id = enchantment.getShort("id");
            Enchantment known = Enchantment.getEnchantment(id);
            if (id < 0 || id >= Enchantment.CUSTOM_ENCHANTMENT_ID || known == null
                    || "unknown".equals(known.getOriginalName()) || known.getId() != id || known.isCurse()
                    || enchantment.getShort("lvl") < 1
                    || enchantment.getShort("lvl") > known.getMaxLevel()) {
                return false;
            }
        }
        return true;
    }

    public int calculateDamage(int d1, int d2, int maxDurability) {
        int j = maxDurability - d1;
        int k = maxDurability - d2;
        int l = j + k + maxDurability * 5 / 100;
        int i1 = maxDurability + 1 - l;
        if (i1 < 0) {
            i1 = 0;
        }
        return i1;
    }
}
