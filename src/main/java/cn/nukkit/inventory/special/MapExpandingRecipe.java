package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.block.BlockAir;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.inventory.ShapedRecipe;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMap;

import java.util.*;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class MapExpandingRecipe extends MultiRecipe {

    public MapExpandingRecipe(){
        super(UUID.fromString(TYPE_MAP_EXTENDING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            ItemMap map;
            // check whether item1 is filled_map or not
            if (item1.getId() == 358) {
                map = (ItemMap) item1.clone();
                if (item2.getId() != 339) {
                    return false;
                }
                if (item2.getCount() != 8) {
                    return false;
                }
            } else {
                map = (ItemMap) item2.clone();
                if (item1.getId() != 339) {
                    return false;
                }
                if (item1.getCount() != 8) {
                    return false;
                }
            }
            return map.canUpgrade();
        }
        return false;
    }

    @Override
    public Recipe toRecipe(Player player, Item outputItem, List<Item> inputs) {
        ItemMap map = (ItemMap) outputItem.clone();
        map.setScale(map.getScale() + 1);
        Map<Character, Item> ingredients = new HashMap<>();
        ingredients.put("A".charAt(0), Item.get(339, 0, 1));
        ingredients.put("B".charAt(0), map);
        return new ShapedRecipe(outputItem, new String[]{"AAA", "ABA", "AAA"}, ingredients, new ArrayList<>());
    }

    @Override
    public void executeExtra(Player player, CraftingTransaction craftingTransaction) {
        ItemMap map = (ItemMap) craftingTransaction.getPrimaryOutput().clone();
        map.setScale(map.getScale() + 1);
        craftingTransaction.setPrimaryOutput(new BlockAir().toItem());
        craftingTransaction.setExtraOutput(map);
    }
}
