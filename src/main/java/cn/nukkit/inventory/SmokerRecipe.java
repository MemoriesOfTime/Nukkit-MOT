package cn.nukkit.inventory;

import cn.nukkit.item.Item;

public class SmokerRecipe extends FurnaceRecipe {

    public SmokerRecipe(Item result, Item ingredient) {
        super(result, ingredient);
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerSmokerRecipe(this);
    }
}
