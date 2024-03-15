package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.List;
import java.util.UUID;

/**
 * @author glorydark
 * @date {2024/1/8} {16:04}
 */
public class MapCloningRecipe extends MultiRecipe {

    public MapCloningRecipe(){
        super(UUID.fromString(TYPE_MAP_CLONING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        // Processing the checks about the inputs and outputItem
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            return (item1.getId() == 358 || item2.getId() == 358) && (item1.getId() == 395 || item2.getId() == 395);
        }
        return false;
    }

    @Override
    public void executeExtra(Player player, CraftingTransaction craftingTransaction) {
        List<Item> inputs = craftingTransaction.getInputList();
        if (inputs.size() == 2) {
            Item item1 = inputs.get(0);
            Item item2 = inputs.get(1);
            Item clonedMap;
            if (item1.getId() == ItemID.MAP) {
                clonedMap = item1.clone();
                clonedMap.setCount(item2.getCount());
                player.getInventory().removeItem(item2);
            } else {
                clonedMap = item2.clone();
                clonedMap.setCount(item1.getCount());
                player.getInventory().removeItem(item1);
            }
            player.getInventory().addItem(clonedMap);
        }
    }
}
