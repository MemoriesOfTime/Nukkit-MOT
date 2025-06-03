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
public class UncheckedMultiRecipe extends MultiRecipe {

    public UncheckedMultiRecipe(String id){
        super(UUID.fromString(id));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        // Processing the checks about the inputs and outputItem
        return true;
    }
}
