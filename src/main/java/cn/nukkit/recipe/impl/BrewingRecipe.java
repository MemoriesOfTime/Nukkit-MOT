package cn.nukkit.recipe.impl;

import cn.nukkit.item.Item;
import cn.nukkit.recipe.RecipeType;

public class BrewingRecipe extends MixRecipe {

    public BrewingRecipe(Item input, Item ingredient, Item output) {
        super(input, ingredient, output);
    }

    @Override
    public RecipeType getType() {
        throw new UnsupportedOperationException();
    }
}