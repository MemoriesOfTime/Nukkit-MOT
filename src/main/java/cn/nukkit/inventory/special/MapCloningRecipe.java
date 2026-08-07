package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;

import java.util.List;
import java.util.UUID;

public class MapCloningRecipe extends MultiRecipe {

    public MapCloningRecipe(){
        super(UUID.fromString(TYPE_MAP_CLONING));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getId() != Item.MAP) {
            return false;
        }
        int filledMap = 0;
        int emptyMap = 0;
        for (Item input : inputs) {
            if (input.getId() == Item.MAP) {
                filledMap += input.getCount();
            } else if (input.getId() == Item.EMPTY_MAP) {
                emptyMap += input.getCount();
            }
        }
        if (filledMap == 0 || emptyMap == 0) {
            return false;
        }
        if (outputItem.getCount() - 1 != emptyMap) {
            return false;
        }
        return true;
    }
}
