package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemID;

import java.util.List;
import java.util.UUID;

public class BannerAddPatternRecipe extends MultiRecipe {

    public BannerAddPatternRecipe(){
        super(UUID.fromString(TYPE_BANNER_ADD_PATTERN));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() != ItemID.BANNER) {
            return false;
        }
        int dyeCount = 0;
        for (Item input : inputs) {
            if (input instanceof ItemDye) {
                dyeCount += 1;
            }
        }
        if (dyeCount < 3) {
            return false;
        }
        return true;
    }
}
