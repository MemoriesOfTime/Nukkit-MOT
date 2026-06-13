package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.inventory.transaction.action.CraftingTakeResultAction;
import cn.nukkit.inventory.transaction.action.CraftingTransferMaterialAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;

import java.util.ArrayList;
import java.util.List;

final class CraftingEventV113Adapter {

    private CraftingEventV113Adapter() {
    }

    static boolean execute(Player player, CraftingRecipe recipe, Item requestedOutput) {
        if (player == null || recipe == null) {
            return false;
        }

        CraftingGrid grid = player.getCraftingGrid();
        if (grid == null) {
            return false;
        }

        int gridSize = grid instanceof BigCraftingGrid ? 3 : 2;
        if (recipe.requiresCraftingTable() && (player.craftingType != Player.CRAFTING_BIG || gridSize < 3)) {
            return false;
        }

        Item result = getServerResult(recipe, requestedOutput);
        if (result == null || result.isNull()) {
            return false;
        }

        CraftingPlan plan = createPlan(grid, gridSize, recipe);
        if (plan == null) {
            return false;
        }

        List<InventoryAction> actions = new ArrayList<>();
        for (Consumption consumption : plan.consumptions) {
            Item before = grid.getItem(consumption.slot);
            Item after = decrease(before, consumption.item.getCount());
            actions.add(new SlotChangeAction(grid, consumption.slot, before, after));
            actions.add(new CraftingTransferMaterialAction(air(), consumption.item, consumption.slot));
        }

        if (!addExtraResults(actions, grid, plan.simulatedGrid, recipe.getExtraResults())) {
            return false;
        }

        actions.add(new CraftingTakeResultAction(result, air()));
        if (!addItemActions(actions, player.getInventory(), result)) {
            return false;
        }

        CraftingTransaction transaction = new CraftingTransaction(player, actions);
        return transaction.execute();
    }

    private static Item getServerResult(CraftingRecipe recipe, Item requestedOutput) {
        Item recipeResult = recipe.getResult();
        if (requestedOutput != null && !requestedOutput.isNull()) {
            if (!sameItem(requestedOutput, recipeResult) || requestedOutput.getCount() != recipeResult.getCount()) {
                return null;
            }
        }
        return recipeResult.clone();
    }

    private static CraftingPlan createPlan(CraftingGrid grid, int gridSize, CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe) {
            return matchShaped(grid, gridSize, (ShapedRecipe) recipe);
        }
        if (recipe instanceof ShapelessRecipe) {
            return matchShapeless(grid, gridSize, (ShapelessRecipe) recipe);
        }
        return null;
    }

    private static CraftingPlan matchShaped(CraftingGrid grid, int gridSize, ShapedRecipe recipe) {
        for (int offsetY = 0; offsetY <= gridSize - recipe.getHeight(); offsetY++) {
            for (int offsetX = 0; offsetX <= gridSize - recipe.getWidth(); offsetX++) {
                CraftingPlan plan = matchShapedAt(grid, gridSize, recipe, offsetX, offsetY, false);
                if (plan != null) {
                    return plan;
                }
                if (recipe.isAssumeSymetry()) {
                    plan = matchShapedAt(grid, gridSize, recipe, offsetX, offsetY, true);
                    if (plan != null) {
                        return plan;
                    }
                }
            }
        }
        return null;
    }

    private static CraftingPlan matchShapedAt(CraftingGrid grid, int gridSize, ShapedRecipe recipe,
                                             int offsetX, int offsetY, boolean mirrored) {
        Item[] simulatedGrid = snapshot(grid, gridSize);
        List<Consumption> consumptions = new ArrayList<>();

        for (int y = 0; y < gridSize; y++) {
            for (int x = 0; x < gridSize; x++) {
                int recipeX = x - offsetX;
                int recipeY = y - offsetY;
                int slot = y * gridSize + x;
                Item actual = simulatedGrid[slot];
                Item expected = air();

                if (recipeX >= 0 && recipeX < recipe.getWidth() && recipeY >= 0 && recipeY < recipe.getHeight()) {
                    int ingredientX = mirrored ? recipe.getWidth() - 1 - recipeX : recipeX;
                    expected = recipe.getIngredient(ingredientX, recipeY);
                }

                if (expected.isNull()) {
                    if (!actual.isNull()) {
                        return null;
                    }
                    continue;
                }

                if (!sameItem(actual, expected) || actual.getCount() < expected.getCount()) {
                    return null;
                }

                Item consumed = expected.clone();
                consumptions.add(new Consumption(slot, consumed));
                simulatedGrid[slot] = decrease(actual, consumed.getCount());
            }
        }

        return consumptions.isEmpty() ? null : new CraftingPlan(consumptions, simulatedGrid);
    }

    private static CraftingPlan matchShapeless(CraftingGrid grid, int gridSize, ShapelessRecipe recipe) {
        Item[] simulatedGrid = snapshot(grid, gridSize);
        boolean[] usedSlots = new boolean[gridSize * gridSize];
        List<Consumption> consumptions = new ArrayList<>();

        for (Item ingredient : recipe.getIngredientList()) {
            if (ingredient == null || ingredient.isNull()) {
                continue;
            }
            int slot = findUnusedMatchingSlot(simulatedGrid, usedSlots, ingredient);
            if (slot < 0) {
                return null;
            }
            usedSlots[slot] = true;
            Item consumed = ingredient.clone();
            consumptions.add(new Consumption(slot, consumed));
            simulatedGrid[slot] = decrease(simulatedGrid[slot], consumed.getCount());
        }

        for (int i = 0; i < simulatedGrid.length; i++) {
            if (!usedSlots[i] && !simulatedGrid[i].isNull()) {
                return null;
            }
        }

        return consumptions.isEmpty() ? null : new CraftingPlan(consumptions, simulatedGrid);
    }

    private static int findUnusedMatchingSlot(Item[] simulatedGrid, boolean[] usedSlots, Item ingredient) {
        for (int i = 0; i < simulatedGrid.length; i++) {
            Item actual = simulatedGrid[i];
            if (!usedSlots[i] && sameItem(actual, ingredient) && actual.getCount() >= ingredient.getCount()) {
                return i;
            }
        }
        return -1;
    }

    private static boolean addExtraResults(List<InventoryAction> actions, CraftingGrid grid,
                                           Item[] simulatedGrid, List<Item> extraResults) {
        for (Item extraResult : extraResults) {
            if (extraResult == null || extraResult.isNull()) {
                continue;
            }
            if (!addGridItemActions(actions, grid, simulatedGrid, extraResult.clone())) {
                return false;
            }
        }
        return true;
    }

    private static boolean addGridItemActions(List<InventoryAction> actions, CraftingGrid grid,
                                              Item[] simulatedGrid, Item item) {
        for (int i = 0; i < simulatedGrid.length && item.getCount() > 0; i++) {
            Item current = simulatedGrid[i];
            if (current.isNull() || !sameStack(current, item)) {
                continue;
            }
            int maxStackSize = Math.min(current.getMaxStackSize(), grid.getMaxStackSize());
            int amount = Math.min(maxStackSize - current.getCount(), item.getCount());
            if (amount <= 0) {
                continue;
            }
            Item target = current.clone();
            target.setCount(target.getCount() + amount);
            actions.add(new SlotChangeAction(grid, i, current, target));
            actions.add(new CraftingTransferMaterialAction(extraResult(amount, item), air(), i));
            simulatedGrid[i] = target;
            item.setCount(item.getCount() - amount);
        }

        for (int i = 0; i < simulatedGrid.length && item.getCount() > 0; i++) {
            Item current = simulatedGrid[i];
            if (!current.isNull()) {
                continue;
            }
            int amount = Math.min(Math.min(item.getMaxStackSize(), grid.getMaxStackSize()), item.getCount());
            Item target = item.clone();
            target.setCount(amount);
            actions.add(new SlotChangeAction(grid, i, current, target));
            actions.add(new CraftingTransferMaterialAction(target, air(), i));
            simulatedGrid[i] = target;
            item.setCount(item.getCount() - amount);
        }

        return item.getCount() <= 0;
    }

    private static Item extraResult(int amount, Item item) {
        Item result = item.clone();
        result.setCount(amount);
        return result;
    }

    private static boolean addItemActions(List<InventoryAction> actions, Inventory inventory, Item item) {
        Item remaining = item.clone();
        for (int i = 0; i < inventory.getSize() && remaining.getCount() > 0; i++) {
            Item current = inventory.getItem(i);
            if (current.isNull() || !sameStack(current, remaining)) {
                continue;
            }
            int maxStackSize = Math.min(current.getMaxStackSize(), inventory.getMaxStackSize());
            int amount = Math.min(maxStackSize - current.getCount(), remaining.getCount());
            if (amount <= 0) {
                continue;
            }
            Item target = current.clone();
            target.setCount(target.getCount() + amount);
            actions.add(new SlotChangeAction(inventory, i, current, target));
            remaining.setCount(remaining.getCount() - amount);
        }

        for (int i = 0; i < inventory.getSize() && remaining.getCount() > 0; i++) {
            Item current = inventory.getItem(i);
            if (!current.isNull()) {
                continue;
            }
            int amount = Math.min(Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize()), remaining.getCount());
            Item target = remaining.clone();
            target.setCount(amount);
            actions.add(new SlotChangeAction(inventory, i, current, target));
            remaining.setCount(remaining.getCount() - amount);
        }

        return remaining.getCount() <= 0;
    }

    private static Item[] snapshot(CraftingGrid grid, int gridSize) {
        Item[] items = new Item[gridSize * gridSize];
        for (int i = 0; i < items.length; i++) {
            items[i] = grid.getItem(i);
        }
        return items;
    }

    private static Item decrease(Item item, int amount) {
        Item result = item.clone();
        result.setCount(result.getCount() - amount);
        return result.getCount() > 0 ? result : air();
    }

    private static boolean sameItem(Item actual, Item expected) {
        return actual != null && expected != null
                && !actual.isNull()
                && actual.equals(expected, expected.hasMeta(), expected.hasCompoundTag());
    }

    private static boolean sameStack(Item current, Item item) {
        return current != null && item != null
                && !current.isNull()
                && current.equals(item, item.hasMeta(), item.hasCompoundTag());
    }

    private static Item air() {
        return Item.get(Item.AIR);
    }

    private static final class CraftingPlan {
        private final List<Consumption> consumptions;
        private final Item[] simulatedGrid;

        private CraftingPlan(List<Consumption> consumptions, Item[] simulatedGrid) {
            this.consumptions = consumptions;
            this.simulatedGrid = simulatedGrid;
        }
    }

    private static final class Consumption {
        private final int slot;
        private final Item item;

        private Consumption(int slot, Item item) {
            this.slot = slot;
            this.item = item;
        }
    }
}
