package cn.nukkit.recipe.impl;

import cn.nukkit.item.Item;
import cn.nukkit.recipe.Recipe;
import lombok.ToString;

@ToString
public abstract class MixRecipe implements Recipe {

    private final Item input;
    private final Item ingredient;
    private final Item output;

    public MixRecipe(Item input, Item ingredient, Item output) {
        this.input = input.clone();
        this.ingredient = ingredient.clone();
        this.output = output.clone();
    }

    public Item getIngredient() {
        return ingredient.clone();
    }

    public Item getInput() {
        return input.clone();
    }

    @Override
    public Item getResult() {
        return output.clone();
    }

    @Override
    public boolean isValidRecipe(int protocol) {
        return true;
    }
}
