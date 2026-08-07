package cn.nukkit.inventory;

import cn.nukkit.item.Item;

import java.util.UUID;

/**
 * Represents a stonecutter recipe.
 */
public class StonecutterRecipe implements Recipe {

    private String recipeId;
    private final Item result;
    private final Item ingredient;
    private final int priority;
    private final int networkId;

    private long least, most;

    public StonecutterRecipe(String recipeId, int priority, Item result, Item ingredient) {
        this(recipeId, priority, result, ingredient, null);
    }

    public StonecutterRecipe(String recipeId, int priority, Item result, Item ingredient, Integer networkId) {
        this.recipeId = recipeId;
        this.priority = priority;
        this.result = result.clone();
        this.ingredient = ingredient.clone();
        this.networkId = networkId != null ? networkId : ++CraftingManager.NEXT_NETWORK_ID;
    }

    @Override
    public Item getResult() {
        return this.result.clone();
    }

    public String getRecipeId() {
        return this.recipeId;
    }

    public UUID getId() {
        return new UUID(least, most);
    }

    public void setId(UUID uuid) {
        this.least = uuid.getLeastSignificantBits();
        this.most = uuid.getMostSignificantBits();

        if (this.recipeId == null) {
            this.recipeId = this.getId().toString();
        }
    }

    public Item getIngredient() {
        return this.ingredient.clone();
    }

    public int getPriority() {
        return this.priority;
    }

    public int getNetworkId() {
        return this.networkId;
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerStonecutterRecipe(this);
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SHAPELESS;
    }
}
