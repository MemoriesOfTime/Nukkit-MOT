package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftResultsDeprecatedAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;

import java.util.List;

/**
 * Legacy craft results pathway. Modern clients still emit this for multi-output
 * recipes so the output is known before DESTROY actions run. We also use it to
 * suppress the default creative-destroy response for the subsequent DESTROY.
 */
public class CraftResultDeprecatedActionProcessor implements ItemStackRequestActionProcessor<CraftResultsDeprecatedAction> {

    public static final String MULTI_RESULTS_KEY = "multiResults";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RESULTS_DEPRECATED;
    }

    @Override
    public ActionResponse handle(CraftResultsDeprecatedAction action, Player player, ItemStackRequestContext context) {
        Recipe recipe = context.get(CreateActionProcessor.RECIPE_DATA_KEY);
        if (recipe instanceof MultiRecipe) {
            Item[] results = action.getResultItems();
            if (results != null && results.length > 0) {
                Item output = results[0].clone();
                // Scale the single-craft result by the requested craft count (#798).
                Integer times = context.get(CraftRecipeActionProcessor.TIMES_CRAFTED_KEY);
                if (times != null && times > 1) {
                    output.setCount(output.getCount() * times);
                }
                if (!CraftRecipeActionProcessor.validateCraftingRecipe(player, recipe, output, 1)) {
                    return context.error();
                }
                context.put(MULTI_RESULTS_KEY, List.of(results));
                output.autoAssignStackNetworkId();
                player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, output, false);
                ItemStackResponseSlot slot = new ItemStackResponseSlot(
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                        PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                        output.getCount(), output.getStackNetId(),
                        output.hasCustomName() ? output.getCustomName() : "",
                        output.getDamage(), ""
                );
                return context.success(List.of(new ItemStackResponseContainer(
                        ContainerSlotType.CREATED_OUTPUT,
                        List.of(slot),
                        new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
                )));
            }
        }
        context.put(DestroyActionProcessor.NO_RESPONSE_DESTROY_KEY, true);
        return null;
    }
}
