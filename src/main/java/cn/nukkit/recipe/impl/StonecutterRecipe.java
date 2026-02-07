package cn.nukkit.recipe.impl;


import cn.nukkit.item.Item;
import cn.nukkit.recipe.RecipeType;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.data.RecipeUnlockingRequirement;

import java.util.Collection;

public class StonecutterRecipe extends ShapelessRecipe {

    public StonecutterRecipe(Item result, Collection<ItemDescriptor> ingredients) {
        super(result, ingredients);
    }

    public StonecutterRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients) {
        super(recipeId, priority, result, ingredients);
    }

    public StonecutterRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients, Integer networkId) {
        super(recipeId, priority, result, ingredients, networkId);
    }

    public StonecutterRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients, Integer networkId, RecipeUnlockingRequirement requirement) {
        super(recipeId, priority, result, ingredients, networkId, requirement);
    }

    public void addResult(ItemDescriptor descriptor) {
        this.getIngredientList().add(descriptor);
    }

    @Override
    public RecipeType getType() {
        return RecipeType.STONECUTTER;
    }
}
