package cn.nukkit.inventory;

import cn.nukkit.inventory.data.RecipeUnlockingRequirement;
import cn.nukkit.item.Item;
import lombok.ToString;

import java.util.Arrays;
import java.util.List;

/**
 * Smithing trim recipe: applies an armor trim pattern + material to an equipment
 * item using a trim template. Unlike {@link SmithingRecipe} (transform), the trim
 * recipe does not change the base item — only applies cosmetic trim NBT.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@ToString
public class SmithingTrimRecipe extends SmithingRecipe {

    public SmithingTrimRecipe(String recipeId, int priority, Item equipment, Item ingredient, Item template) {
        super(recipeId, priority, Arrays.asList(equipment, ingredient, template), Item.AIR_ITEM.clone());
    }

    @Override
    public Item getResult() {
        return Item.AIR_ITEM.clone();
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SMITHING_TRIM;
    }

    @Override
    public RecipeUnlockingRequirement getRequirement() {
        return RecipeUnlockingRequirement.ALWAYS_UNLOCKED;
    }

    @Override
    public List<Item> getIngredientsAggregate() {
        return super.getIngredientsAggregate();
    }
}
