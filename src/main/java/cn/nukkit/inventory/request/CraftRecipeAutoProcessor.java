package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.AutoCraftRecipeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Auto-craft variant of CraftRecipeAction (triggered by shift-click on recipe
 * book). Unlike CRAFT_RECIPE, the client ships the concrete ingredient
 * descriptors it intends to consume, so the server validates against the
 * resolved recipe and the follow-up CONSUME chain.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public class CraftRecipeAutoProcessor implements ItemStackRequestActionProcessor<AutoCraftRecipeAction> {

    public static final String TIMES_CRAFTED_KEY = "timesCrafted";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RECIPE_AUTO;
    }

    @Override
    public ActionResponse handle(AutoCraftRecipeAction action, Player player, ItemStackRequestContext context) {
        Recipe recipe = player.getServer().getCraftingManager().getRecipeByNetworkId(action.getRecipeNetworkId());
        if (recipe == null) {
            return context.error();
        }

        List<ItemDescriptorWithCount> ingredients = action.getIngredients();
        if (ingredients == null) {
            ingredients = List.of();
        }
        Item[] eventItems = ingredients.stream()
                .map(i -> i.getDescriptor().toItem())
                .toArray(Item[]::new);

        CraftItemEvent craftItemEvent = new CraftItemEvent(player, eventItems, recipe);
        player.getServer().getPluginManager().callEvent(craftItemEvent);
        if (craftItemEvent.isCancelled()) {
            return context.error();
        }

        context.put(CraftRecipeActionProcessor.RECIPE_NET_ID_KEY, action.getRecipeNetworkId());
        context.put(CreateActionProcessor.RECIPE_DATA_KEY, recipe);
        context.put(TIMES_CRAFTED_KEY, action.getTimesCrafted());

        int consumeActionCountNeeded = 0;
        for (ItemDescriptorWithCount ingredient : ingredients) {
            if (ingredient != null && ingredient.getCount() > 0) {
                consumeActionCountNeeded++;
            }
        }
        List<ConsumeAction> consumeActions = findAllConsumeActions(
                context.getItemStackRequest().getActions(),
                context.getCurrentActionIndex() + 1);
        if (consumeActions.size() < consumeActionCountNeeded) {
            log.warn("{}: auto-craft consume action count mismatch. expected={} actual={}",
                    player.getName(), consumeActionCountNeeded, consumeActions.size());
            return context.error();
        }

        Item recipeResult = recipe instanceof MultiRecipe multi ? multi.getResult() : recipe.getResult();
        if (recipeResult == null || recipeResult.isNull()) {
            return null;
        }
        int times = Math.max(1, action.getTimesCrafted());
        Item output = recipeResult.clone();
        output.setCount(output.getCount() * times);
        output.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, output, false);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                0, 0, output.getCount(), output.getStackNetId(),
                output.hasCustomName() ? output.getCustomName() : "",
                output.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }

    static List<ConsumeAction> findAllConsumeActions(ItemStackRequestAction[] actions, int startIndex) {
        List<ConsumeAction> found = new ArrayList<>();
        for (int i = startIndex; i < actions.length; i++) {
            if (actions[i] instanceof ConsumeAction consume) {
                found.add(consume);
            }
        }
        return found;
    }
}
