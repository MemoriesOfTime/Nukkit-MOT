package cn.nukkit.recipe.impl;

import cn.nukkit.item.Item;
import cn.nukkit.recipe.RecipeType;

public class ContainerRecipe extends MixRecipe {

    public ContainerRecipe(Item input, Item ingredient, Item output) {
        super(input, ingredient, output);
    }

    @Override
    public RecipeType getType() {
        throw new UnsupportedOperationException();
    }
}
