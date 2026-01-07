package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class RepairItemRecipe extends MultiRecipe {

    public RepairItemRecipe(){
        super(TYPE_REPAIR_ITEM);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        // Processing the checks about the inputs and outputItem
        ItemDescriptor[] items = inputs.toArray(new ItemDescriptor[0]);
        if (inputs.size() == 2 && items[0] instanceof DefaultDescriptor item1 && items[1] instanceof DefaultDescriptor item2) {
            if (item1.getItem().equals(item2.getItem(), false, false)) {
                if (item1.getItem().isTool() || item1.getItem().isArmor()) {
                    int damage = calculateDamage(item1.getItem().getDamage(), item2.getItem().getDamage(), item1.getItem().getMaxDurability());
                    if (outputItem.equals(item1.getItem(), false, true)) {
                        return outputItem.getDamage() == damage;
                    }
                }
            }
        } else if (inputs.size() == 1 && items[0] instanceof DefaultDescriptor item) {
            if (item.getItem().getCount() == 2) {
                if (item.getItem().isTool() || item.getItem().isArmor()) {
                    int damage = calculateDamage(item.getItem().getDamage(), item.getItem().getDamage(), item.getItem().getMaxDurability());
                    if (outputItem.equals(item.getItem(), false, true)) {
                        return outputItem.getDamage() == damage;
                    }
                }
            }
        }
        return false;
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
