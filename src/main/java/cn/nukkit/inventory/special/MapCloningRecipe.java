package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;

import java.util.List;
import java.util.UUID;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class MapCloningRecipe extends MultiRecipe {

    public MapCloningRecipe(){
        super(UUID.fromString(TYPE_BOOK_CLONING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            if (item1.getId() == Item.MAP && item2.getId() == Item.EMPTY_MAP) {
                return true;
            } else return item1.getId() == Item.EMPTY_MAP && item2.getId() == Item.MAP;
        }
        return false;
    }
}
