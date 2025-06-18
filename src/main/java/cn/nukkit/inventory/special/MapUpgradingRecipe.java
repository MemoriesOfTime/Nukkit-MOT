

package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class MapUpgradingRecipe extends MultiRecipe {

    public MapUpgradingRecipe(){
        super(UUID.fromString(TYPE_MAP_UPGRADING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() != ItemID.MAP && outputItem.getCount() != 1) {
            return false;
        }
        int filledMap = 0;
        int paper = 0;
        for (Item input : inputs) {
            if (input.getId() == ItemID.MAP) {
                filledMap += 1;
            } else if (input.getId() == ItemID.PAPER) {
                paper += 1;
            }
        }
        if (filledMap != 1) {
            return false;
        }
        if (paper != 8) {
            return false;
        }
        return true;
    }
}
