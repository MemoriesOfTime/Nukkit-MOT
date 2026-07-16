package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.descriptor.ItemDescriptorWithCount;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
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
                .map(CraftRecipeAutoProcessor::toEventItem)
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

        List<Item> consumedItems = collectConsumedItems(player, consumeActions);
        if (consumedItems == null || !validateConsumePlan(ingredients, consumedItems)) {
            return context.error();
        }

        if (recipe instanceof MultiRecipe multiRecipe) {
            context.put(CraftRecipeActionProcessor.TIMES_CRAFTED_KEY, Math.max(1, action.getTimesCrafted()));
            CraftResultsDeprecatedAction resultsAction = findCraftResultsAction(
                    context.getItemStackRequest().getActions(), context.getCurrentActionIndex() + 1);
            if (resultsAction == null || resultsAction.getResultItems() == null || resultsAction.getResultItems().length == 0) {
                return context.error();
            }
            Item output = resultsAction.getResultItems()[0];
            if (output == null || output.isNull()
                    || !validateAutoCraftingRecipe(player, multiRecipe, output, 1, consumedItems)) {
                return context.error();
            }
            context.put(CraftResultDeprecatedActionProcessor.MULTI_RESULTS_KEY, List.of(resultsAction.getResultItems()));
            return context.success();
        }

        Item recipeResult = recipe.getResult();
        if (recipeResult == null || recipeResult.isNull()) {
            return null;
        }
        int times = Math.max(1, action.getTimesCrafted());
        if (!validateAutoCraftingRecipe(player, recipe, recipeResult, times, consumedItems)) {
            return context.error();
        }
        Item output = recipeResult.clone();
        output.setCount(output.getCount() * times);
        if (recipe instanceof UserDataShapelessRecipe) {
            CraftRecipeActionProcessor.applyInputNbt(output, consumedItems);
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

    static List<ConsumeAction> findAllConsumeActions(ItemStackRequestAction[] actions, int startIndex) {
        List<ConsumeAction> found = new ArrayList<>();
        for (int i = startIndex; i < actions.length; i++) {
            if (actions[i] instanceof ConsumeAction consume) {
                found.add(consume);
            }
        }
        return found;
    }

    private static CraftResultsDeprecatedAction findCraftResultsAction(ItemStackRequestAction[] actions, int startIndex) {
        for (int i = startIndex; i < actions.length; i++) {
            if (actions[i] instanceof CraftResultsDeprecatedAction craftResults) {
                return craftResults;
            }
        }
        return null;
    }

    private static Item toEventItem(ItemDescriptorWithCount ingredient) {
        if (ingredient == null || ingredient.getDescriptor() == null) {
            return Item.get(Item.AIR);
        }
        Item item = ingredient.getDescriptor().toItem();
        if (item != null && !item.isNull() && ingredient.getCount() > 0) {
            item.setCount(ingredient.getCount());
        }
        return item;
    }

    private static List<Item> collectConsumedItems(Player player, List<ConsumeAction> consumeActions) {
        List<Item> actual = new ArrayList<>();
        for (ConsumeAction consume : consumeActions) {
            if (consume.getCount() <= 0) {
                return null;
            }
            var source = consume.getSource();
            if (source == null) {
                return null;
            }
            var inventory = NetworkMapping.getInventory(player, source.getContainer(), source.getDynamicId());
            if (inventory == null) {
                return null;
            }
            int slot = NetworkMapping.toInternalSlot(source.getContainer(), source.getSlot());
            Item item = inventory.getItem(slot);
            if (item.isNull() || item.getCount() < consume.getCount()) {
                return null;
            }
            if (hasStackNetworkIdMismatch(item.getStackNetId(), source.getStackNetworkId())) {
                return null;
            }
            Item consumed = item.clone();
            consumed.setCount(consume.getCount());
            actual.add(consumed);
        }
        return actual;
    }

    private static boolean validateConsumePlan(List<ItemDescriptorWithCount> ingredients, List<Item> consumedItems) {
        List<Item> actual = cloneItems(consumedItems);
        for (ItemDescriptorWithCount ingredient : ingredients) {
            if (ingredient == null || ingredient.getDescriptor() == null || ingredient.getCount() <= 0) {
                continue;
            }
            int remaining = ingredient.getCount();
            for (Item actualItem : new ArrayList<>(actual)) {
                if (!matchesDescriptor(ingredient, actualItem)) {
                    continue;
                }
                int amount = Math.min(actualItem.getCount(), remaining);
                actualItem.setCount(actualItem.getCount() - amount);
                remaining -= amount;
                if (actualItem.getCount() == 0) {
                    actual.remove(actualItem);
                }
                if (remaining == 0) {
                    break;
                }
            }
            if (remaining > 0) {
                return false;
            }
        }
        return actual.isEmpty();
    }

    private static boolean matchesDescriptor(ItemDescriptorWithCount ingredient, Item item) {
        if (item == null || item.isNull()) {
            return false;
        }
        if (ingredient.getDescriptor().match(item)) {
            return true;
        }
        Item expected = ingredient.getDescriptor().toItem();
        return expected != null
                && !expected.isNull()
                && expected.equals(item, expected.hasMeta(), expected.hasCompoundTag());
    }

    private static boolean validateAutoCraftingRecipe(Player player, Recipe recipe, Item output, int multiplier, List<Item> consumedItems) {
        List<Item> inputs = cloneItems(consumedItems);
        if (recipe instanceof MultiRecipe multiRecipe) {
            return multiRecipe.canExecute(player, output.clone(), inputs);
        }
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return true;
        }

        Item primaryOutput = output.clone();
        primaryOutput.setCount(primaryOutput.getCount() * Math.max(1, multiplier));
        List<Item> extraOutputs = CraftRecipeActionProcessor.scaleItems(craftingRecipe.getExtraResults(), Math.max(1, multiplier));
        // 与 validateCraftingRecipe 保持一致:匹配前封顶到"材料×multiplier",避免 matchItemList 严格相等误判。
        List<Item> cappedInputs = CraftRecipeActionProcessor.capInputsToIngredients(
                inputs, craftingRecipe.getIngredientsAggregate(), Math.max(1, multiplier));
        Recipe matched = player.getServer().getCraftingManager().matchRecipe(cappedInputs, primaryOutput, extraOutputs);
        return matched == recipe;
    }

    private static List<Item> cloneItems(List<Item> items) {
        List<Item> cloned = new ArrayList<>(items.size());
        for (Item item : items) {
            if (item != null && !item.isNull()) {
                cloned.add(item.clone());
            }
        }
        return cloned;
    }

    private static boolean hasStackNetworkIdMismatch(int serverNetId, int clientNetId) {
        return serverNetId > 0 && clientNetId > 0 && serverNetId != clientNetId;
    }
}
