package cn.nukkit.inventory;

import cn.nukkit.inventory.data.RecipeUnlockingRequirement;
import cn.nukkit.item.Item;
import lombok.ToString;

import java.util.Collection;
import java.util.List;

/**
 * Smithing transform recipe: upgrades an equipment item by combining it with a
 * material and an upgrade template (e.g. Netherite upgrade). Unlike
 * {@link SmithingTrimRecipe} (cosmetic only), the transform recipe changes the
 * base item to the recipe's result and preserves the original equipment's NBT
 * (enchantments, custom name, durability). Exists primarily as a type marker so
 * the {@code CraftRecipeActionProcessor} can dispatch to
 * {@code handleSmithingUpgrade} via {@code instanceof}.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@ToString
public class SmithingTransformRecipe extends SmithingRecipe {

    public SmithingTransformRecipe(String recipeId, int priority, Collection<Item> ingredients, Item result) {
        super(recipeId, priority, ingredients, result);
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SMITHING_TRANSFORM;
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
