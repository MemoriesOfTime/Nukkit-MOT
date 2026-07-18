package cn.nukkit.inventory;

import cn.nukkit.item.Item;

import java.util.Collection;

/**
 * "shulker_box" 型 (type 5) 无序合成配方：合成输出需继承输入物品携带的 NBT (如潜影盒/收纳袋内容)，避免染色时丢失。
 * <p>
 * Shapeless variant for "shulker_box" (type 5) recipes: crafting output inherits the input item's NBT (e.g. shulker box/bundle contents) so dyeing does not wipe them.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class UserDataShapelessRecipe extends ShapelessRecipe {

    public UserDataShapelessRecipe(Item result, Collection<Item> ingredients) {
        super(result, ingredients);
    }

    public UserDataShapelessRecipe(String recipeId, int priority, Item result, Collection<Item> ingredients) {
        super(recipeId, priority, result, ingredients);
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SHULKER_BOX;
    }
}
