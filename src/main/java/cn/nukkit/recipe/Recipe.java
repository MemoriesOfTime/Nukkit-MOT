package cn.nukkit.recipe;

import cn.nukkit.item.Item;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;

import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface Recipe {

    Item getResult();

    RecipeType getType();

    boolean isValidRecipe(int protocol);

    static boolean matchItemList(List<Item> have, Collection<ItemDescriptor> need) {
        final List<ItemDescriptor> needItems = new ArrayList<>(need);
        final List<Item> haveItems = new ArrayList<>(have);

        final Map<ItemDescriptor, Integer> map = new HashMap<>();
        for(ItemDescriptor item : needItems) {
            int count = 1;
            if(item instanceof DefaultDescriptor item1) {
                count = item1.getItem().getCount();
            }
            map.put(item, map.getOrDefault(item, 0) + count);
        }
        for (ItemDescriptor recipeItem : new ArrayList<>(needItems)) {
            for (Item haveItem : new ArrayList<>(haveItems)) {
                if (recipeItem.equals(haveItem)) {
                    int amount = Math.min(haveItem.getCount(), map.get(recipeItem));
                    map.put(recipeItem, map.get(recipeItem) - amount);
                    haveItem.setCount(haveItem.getCount() - amount);
                    if (haveItem.getCount() == 0) {
                        haveItems.remove(haveItem);
                    }
                    if (map.get(recipeItem) == 0) {
                        needItems.remove(recipeItem);
                        break;
                    }
                }
            }
        }
        return haveItems.isEmpty() && needItems.isEmpty();
    }
}
