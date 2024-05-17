package cn.nukkit.inventory;

import cn.nukkit.item.Item;

import java.util.ArrayList;
import java.util.List;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface Recipe {

    static boolean matchItemList(List<Item> haveItems, List<Item> needItems) {
        for (Item needItem : new ArrayList<>(needItems)) {
            for (Item haveItem : new ArrayList<>(haveItems)) {
                if (needItem.equals(haveItem, needItem.hasMeta(), needItem.hasCompoundTag())) {
                    int amount = Math.min(haveItem.getCount(), needItem.getCount());
                    needItem.setCount(needItem.getCount() - amount);
                    haveItem.setCount(haveItem.getCount() - amount);
                    if (haveItem.getCount() == 0) {
                        haveItems.remove(haveItem);
                    }
                    if (needItem.getCount() == 0) {
                        needItems.remove(needItem);
                        break;
                    }
                }
            }
        }
        return haveItems.isEmpty() && needItems.isEmpty();
    }

    Item getResult();

    void registerToCraftingManager(CraftingManager manager);

    RecipeType getType();
}
