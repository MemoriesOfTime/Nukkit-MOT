package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBanner;
import cn.nukkit.item.ItemID;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

public class BannerDuplicateRecipe extends MultiRecipe {

    public BannerDuplicateRecipe() {
        super(TYPE_BANNER_DUPLICATE);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        int count = 0;
        for (ItemDescriptor input : inputs) {
            if(input instanceof DefaultDescriptor descriptor) {
                if (!(descriptor.getItem() instanceof ItemBanner)) {
                    return false;
                }
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