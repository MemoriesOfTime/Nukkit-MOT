package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BannerDuplicateRecipe extends MultiRecipe {

    public BannerDuplicateRecipe(){
        super(UUID.fromString(TYPE_BANNER_DUPLICATE));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        int count = 0;
        for (Item input : inputs) {
            if (input.getId() != ItemID.BANNER) {
                return false;
            }
            count += 1;
        }
        if (count < 2) {
            return false;
        }
        if (outputItem.getId() == ItemID.BANNER) {
            return true;
        }
        return false;
    }
}
