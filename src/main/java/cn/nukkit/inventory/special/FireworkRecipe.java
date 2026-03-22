package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.List;
import java.util.UUID;

public class FireworkRecipe extends MultiRecipe {

    public FireworkRecipe(){
        super(UUID.fromString(TYPE_FIREWORKS));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() == ItemID.FIREWORKS) {
            boolean hasPaper = false;
            int powder = 0;
            for (Item input : inputs) {
                if (input.getId() == ItemID.GUNPOWDER) {
                    powder++;
                } else if (input.getId() == ItemID.PAPER) {
                    hasPaper = true;
                } else if (input.getId() != ItemID.FIREWORKSCHARGE) {
                    // Only paper, gunpowder and firework stars are allowed
                    return false;
                }
            }
            if (!hasPaper) {
                return false;
            }
            if (powder < 1 || powder > 3) {
                return false;
            }
            return true;
        }
        return false;
    }
}
