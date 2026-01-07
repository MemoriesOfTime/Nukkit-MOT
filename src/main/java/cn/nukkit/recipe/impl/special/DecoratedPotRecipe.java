package cn.nukkit.recipe.impl.special;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBrick;
import cn.nukkit.item.ItemPotterySherd;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.MultiRecipe;

import java.util.Collection;

public class DecoratedPotRecipe extends MultiRecipe {

    public DecoratedPotRecipe() {
        super(TYPE_DECORATED_POT_RECIPE);
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, Collection<ItemDescriptor> inputs) {
        if (outputItem.getId() != Item.DECORATED_POT) {
            return false;
        }
        int brickCount = 0;
        int sherdCount = 0;
        for (ItemDescriptor input : inputs) {
            if(input instanceof DefaultDescriptor descriptor) {
                if (descriptor.getItem() instanceof ItemBrick) {
                    brickCount += 1;
                }
                if (descriptor.getItem() instanceof ItemPotterySherd) {
                    sherdCount += 1;
                }
            }
        }
        if (sherdCount + brickCount < 4) {
            return false;
        }
        return true;
    }
}