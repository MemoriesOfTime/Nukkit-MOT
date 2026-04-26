package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CreateAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

/**
 * CreateAction is sent by the client for multi-output recipes to pick a specific
 * result slot from the recipe's outputs (typically firework stars, banner
 * patterns). Resolves the recipe cached by CraftRecipeAction and writes the
 * chosen result into CREATED_OUTPUT.
 */
public class CreateActionProcessor implements ItemStackRequestActionProcessor<CreateAction> {

    public static final String RECIPE_DATA_KEY = "recipe";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CREATE;
    }

    @Override
    public ActionResponse handle(CreateAction action, Player player, ItemStackRequestContext context) {
        Recipe recipe = context.get(RECIPE_DATA_KEY);
        if (recipe == null) {
            // Look back through the request for a CraftRecipeAction
            Optional<ItemStackRequestAction> cra = Arrays.stream(context.getItemStackRequest().getActions())
                    .filter(a -> a instanceof CraftRecipeAction)
                    .findFirst();
            if (cra.isEmpty()) {
                return context.error();
            }
            int recipeNetId = ((CraftRecipeAction) cra.get()).getRecipeNetworkId();
            recipe = player.getServer().getCraftingManager().getRecipeByNetworkId(recipeNetId);
            if (recipe == null) {
                return context.error();
            }
        }

        List<Item> results;
        if (recipe instanceof MultiRecipe) {
            results = context.get(CraftResultDeprecatedActionProcessor.MULTI_RESULTS_KEY);
            if (results == null || results.isEmpty()) {
                return context.error();
            }
        } else {
            results = List.of(recipe.getResult());
        }
        int slot = action.getSlot();
        if (slot < 0 || slot >= results.size()) {
            return context.error();
        }

        Item output = results.get(slot).clone();
        if (!CraftRecipeActionProcessor.validateCraftingRecipe(player, recipe, output, 1)) {
            return context.error();
        }
        output.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, output, false);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                output.getCount(), output.getStackNetId(),
                output.hasCustomName() ? output.getCustomName() : "",
                output.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }
}
