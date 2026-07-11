package cn.nukkit.inventory;

import cn.nukkit.item.Item;

import java.util.UUID;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class FurnaceRecipe implements SmeltingRecipe {

    protected final Item output;

    protected Item ingredient;

    private String recipeId;

    private UUID id;

    private int networkId;

    @Deprecated
    public FurnaceRecipe(Item result, Item ingredient) {
        this(null, result, ingredient);
    }

    public FurnaceRecipe(String recipeId, Item result, Item ingredient) {
        this.output = result.clone();
        this.ingredient = ingredient.clone();
        this.recipeId = recipeId;
        this.networkId = ++CraftingManager.NEXT_NETWORK_ID;
    }

    public String getRecipeId() {
        return recipeId;
    }

    public UUID getId() {
        return id;
    }

    public int getNetworkId() {
        return networkId;
    }

    public void setId(UUID id) {
        this.id = id;
        if (this.recipeId == null) {
            this.recipeId = this.id.toString();
        }
    }

    public void setInput(Item item) {
        this.ingredient = item.clone();
    }

    @Override
    public Item getInput() {
        return this.ingredient.clone();
    }

    @Override
    public Item getResult() {
        return this.output.clone();
    }

    @Override
    public void registerToCraftingManager(CraftingManager manager) {
        manager.registerFurnaceRecipe(this);
    }

    @Override
    public RecipeType getType() {
        return this.ingredient.hasMeta() ? RecipeType.FURNACE_DATA : RecipeType.FURNACE;
    }
}
