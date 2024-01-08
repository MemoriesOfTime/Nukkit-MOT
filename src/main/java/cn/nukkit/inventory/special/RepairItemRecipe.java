package cn.nukkit.inventory.special;

import cn.nukkit.item.Item;

import java.util.List;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class RepairItemRecipe implements SpecialRecipe {

    public RepairItemRecipe(){}

    @Override
    public boolean canExecute(List<Item> inputs, Item outputItem) {
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            if (item1.equals(item2, false, false)) {
                if (item1.isTool() || item1.isArmor()) {
                    int damage = calculateDamage(item1.getDamage(), item2.getDamage(), item1.getMaxDurability());
                    if (outputItem.equals(item1, false, true)) {
                        return outputItem.getDamage() == damage;
                    }
                }
            }
        } else if (inputs.size() == 1) {
            Item item = inputs.get(0);
            if (item.getCount() == 2) {
                if (item.isTool() || item.isArmor()) {
                    int damage = calculateDamage(item.getDamage(), item.getDamage(), item.getMaxDurability());
                    if (outputItem.equals(item, false, true)) {
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
