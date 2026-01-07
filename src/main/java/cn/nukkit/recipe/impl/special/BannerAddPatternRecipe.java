package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.item.ItemID;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

public class BannerAddPatternRecipe extends MultiRecipe {

    public BannerAddPatternRecipe(){
        super(TYPE_BANNER_ADD_PATTERN);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        if (outputItem.getId() != ItemID.BANNER) {
            return false;
        }
        int dyeCount = 0;
        for (ItemDescriptor input : inputs) {
            if(input instanceof DefaultDescriptor descriptor) {
                if (descriptor.getItem() instanceof ItemDye) {
                    dyeCount += 1;
                }
            }
        }
        if (dyeCount < 3) {
            return false;
        }
        return true;
    }
}