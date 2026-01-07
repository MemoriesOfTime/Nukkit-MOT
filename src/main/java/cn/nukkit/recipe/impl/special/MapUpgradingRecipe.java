package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemMap;
import cn.nukkit.item.ItemPaper;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

public class MapUpgradingRecipe extends MultiRecipe {

    public MapUpgradingRecipe() {
        super(TYPE_MAP_UPGRADING);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        if (outputItem.getId() != ItemID.MAP && outputItem.getCount() != 1) {
            return false;
        }
        int filledMap = 0;
        int paper = 0;
        for (ItemDescriptor input : inputs) {
            if(input instanceof DefaultDescriptor descriptor) {
                if (descriptor.getItem() instanceof ItemMap) {
                    filledMap += 1;
                } else if (descriptor.getItem() instanceof ItemPaper) {
                    paper += 1;
                }
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