package cn.nukkit.recipe.impl;

import cn.nukkit.item.Item;
import cn.nukkit.recipe.CraftingRecipe;
import cn.nukkit.recipe.Recipe;
import cn.nukkit.recipe.RecipeRegistry;
import cn.nukkit.recipe.RecipeType;
import cn.nukkit.recipe.descriptor.DefaultDescriptor;
import cn.nukkit.recipe.descriptor.ItemDescriptor;
import cn.nukkit.recipe.impl.data.RecipeUnlockingRequirement;
import lombok.Getter;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ShapelessRecipe implements CraftingRecipe {

    private String recipeId;

    private final Item output;

    private long least, most;

    private final Collection<ItemDescriptor> ingredients;

    private final int priority;

    @Getter
    private final int networkId;

    /**
     * @since v685
     */
    private final RecipeUnlockingRequirement requirement;

    public ShapelessRecipe(Item result, Collection<ItemDescriptor> ingredients) {
        this(null, 10, result, ingredients);
    }

    public ShapelessRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients) {
        this(recipeId, priority, result, ingredients, null);
    }

    public ShapelessRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients, Integer networkId) {
        this(recipeId, priority, result, ingredients, networkId, RecipeUnlockingRequirement.ALWAYS_UNLOCKED);
    }

    public ShapelessRecipe(String recipeId, int priority, Item result, Collection<ItemDescriptor> ingredients, Integer networkId, RecipeUnlockingRequirement requirement) {
        this.recipeId = recipeId;
        this.priority = priority;
        this.output = result.clone();
        if (ingredients.size() > 9) {
            throw new IllegalArgumentException("Shapeless recipes cannot have more than 9 ingredients");
        }
        this.ingredients = ingredients;

        this.networkId = networkId != null ? networkId : ++RecipeRegistry.NEXT_NETWORK_ID;
        this.requirement = requirement;
    }

    @Override
    public Item getResult() {
        return this.output.clone();
    }

    @Override
    public String getRecipeId() {
        return this.recipeId;
    }

    @Override
    public UUID getId() {
        return new UUID(least, most);
    }

    @Override
    public void setId(UUID uuid) {
        this.least = uuid.getLeastSignificantBits();
        this.most = uuid.getMostSignificantBits();

        if (this.recipeId == null) {
            this.recipeId = this.getId().toString();
        }
    }

    public Collection<ItemDescriptor> getIngredientList() {
        return this.ingredients;
    }

    public int getIngredientCount() {
        return ingredients.size();
    }

    @Override
    public RecipeType getType() {
        return RecipeType.SHAPELESS;
    }

    @Override
    public boolean requiresCraftingTable() {
        return this.ingredients.size() > 4;
    }

    @Override
    public List<Item> getExtraResults() {
        return new ArrayList<>();
    }

    @Override
    public int getPriority() {
        return this.priority;
    }

    /**
     * Returns whether the specified list of crafting grid inputs and outputs matches this recipe. Outputs DO NOT
     * include the primary result item.
     *
     * @param inputList  list of items taken from the crafting grid
     * @param extraOutputList list of items put back into the crafting grid (secondary results)
     * @return bool
     */
    @Override
    public boolean matchItems(List<Item> inputList, List<Item> extraOutputList) {
        List<Item> haveInputs = new ArrayList<>();
        for (Item item : inputList) {
            if (item.isNull())
                continue;
            haveInputs.add(item.clone());
        }


        List<Item> haveOutputs = new ArrayList<>();
        for (Item item : extraOutputList) {
            if (item.isNull())
                continue;
            haveOutputs.add(item.clone());
        }

        List<ItemDescriptor> needOutputs = new ArrayList<>();
        for (Item item : getExtraResults()) {
            if (item.isNull())
                continue;
            needOutputs.add(new DefaultDescriptor(item));
        }

        return Recipe.matchItemList(haveOutputs, needOutputs);
    }

    @Override
    public RecipeUnlockingRequirement getRequirement() {
        return this.requirement;
    }

    @Override
    public boolean isValidRecipe(int protocol) {
        for(ItemDescriptor item : this.ingredients) {
            if(!item.putRecipe(null, protocol)) {
                return false;
            }
        }
        return new DefaultDescriptor(this.output).putRecipe(null, protocol);
    }
}
