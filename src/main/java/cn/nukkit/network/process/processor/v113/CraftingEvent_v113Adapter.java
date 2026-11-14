package cn.nukkit.network.process.processor.v113;

import cn.nukkit.Player;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.CraftingTransaction;
import cn.nukkit.inventory.transaction.ItemStackRequestCraftingTransaction;
import cn.nukkit.item.Item;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 1.1.x 合成按包内快照校验并在玩家背包结算；成功后静默清空镜像合成格，失败时保留。
 * <p>
 * Legacy crafting validates packet snapshots and settles on the player inventory; success clears
 * the mirror grid silently, failure preserves it.
 *
 * @author LT_Name
 */
final class CraftingEvent_v113Adapter {

    private static final int GRID_SIZE = 3;
    private static final int SMALL_GRID_CAPACITY = 4;

    private CraftingEvent_v113Adapter() {
    }

    /**
     * 校验并执行一次合成事件；input/output 均为客户端上报快照。
     * Validates and applies one crafting event; input/output are client-reported snapshots.
     */
    static boolean execute(Player player, CraftingRecipe recipe, Item[] input, Item[] output) {
        if (player == null || recipe == null) {
            return false;
        }

        if (recipe.requiresCraftingTable() && !hasCraftingTable(player)) {
            return false;
        }

        Item result = recipe.getResult();
        if (result == null || result.isNull()) {
            return false;
        }

        if (!matchesOutput(output, result)) {
            return false;
        }

        Item[] grid = buildGrid(input);
        if (grid == null) {
            return false;
        }

        List<Item> ingredients = matchInput(grid, recipe);
        if (ingredients == null || ingredients.isEmpty()
                || ingredients.size() > SMALL_GRID_CAPACITY && !hasCraftingTable(player)) {
            return false;
        }

        PlayerInventory inventory = player.getInventory();
        // 创造模式合成格物品来自创造面板，vanilla 也不消耗材料，只校验不预留。
        // Creative grids are filled from the creative panel and consume nothing, so skip reservation.
        if (!player.isCreative() && reserveIngredients(inventory, ingredients) == null) {
            return false;
        }

        // 潜影盒只继承盒子 NBT，避免命名染料覆盖盒内物品。
        // Shulker results inherit box NBT, never named-dye or client-output NBT.
        if (recipe instanceof UserDataShapelessRecipe) {
            result = result.clone();
            boolean shulkerResult = result.getId() == Item.SHULKER_BOX || result.getId() == Item.UNDYED_SHULKER_BOX;
            for (Item ingredient : ingredients) {
                if (ingredient.hasCompoundTag() && (!shulkerResult
                        || ingredient.getId() == Item.SHULKER_BOX || ingredient.getId() == Item.UNDYED_SHULKER_BOX)) {
                    result.setCompoundTag(ingredient.getCompoundTag());
                    break;
                }
            }
        }

        // 只读快照让 getTransaction() 恒非空（与 ItemStackRequest 路径一致），实际变更由下方结算完成。
        // Read-only snapshot keeps getTransaction() non-null; the settlement below performs the mutations.
        ItemStackRequestCraftingTransaction transaction = new ItemStackRequestCraftingTransaction(
                player, ingredients, result, recipe);
        CraftItemEvent event = new CraftItemEvent(transaction);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        // 插件可经 transaction.setPrimaryOutput() 改写产物，结算必须使用事件后的产物。
        // Plugins may rewrite the output via setPrimaryOutput(); settle the post-event output.
        Item eventOutput = transaction.getPrimaryOutput();
        if (eventOutput == null || eventOutput.isNull()) {
            return false;
        }
        Item settled = eventOutput.clone();

        // 合成事件可能改变背包；结算前重新预留，避免使用事件前的槽位记录。
        // Re-reserve after the crafting event, which may have changed the inventory.
        int[] used = player.isCreative() ? new int[inventory.getSize()] : reserveIngredients(inventory, ingredients);
        if (used == null) {
            return false;
        }
        List<Item> results = new ArrayList<>();
        results.add(settled);
        for (Item extraResult : recipe.getExtraResults()) {
            if (extraResult == null || extraResult.isNull() || extraResult.getCount() <= 0) {
                continue;
            }
            results.add(extraResult.clone());
        }
        List<Item> leftovers = settleInventory(player, used, results);
        if (leftovers == null) {
            return false;
        }
        for (Item leftover : leftovers) {
            if (leftover != null && !leftover.isNull() && leftover.getCount() > 0) {
                player.getLevel().dropItem(player, leftover);
            }
        }

        CraftingTransaction.awardCraftingAchievement(player, settled);

        return true;
    }

    /**
     * BIG 直接允许；SMALL 且有保留的大合成格也允许，不检查当前窗口。
     * BIG qualifies; SMALL also qualifies with a retained big grid, regardless of the open window.
     */
    private static boolean hasCraftingTable(Player player) {
        if (player.craftingType == Player.CRAFTING_BIG) {
            return true;
        }
        return player.craftingType == Player.CRAFTING_SMALL
                && player.getCraftingGrid() instanceof BigCraftingGrid;
    }

    /**
     * 成功后静默清空镜像（含插件写入，不触发事件/回包），防止关闭界面重复返还。
     * Silently clears the mirror, including plugin writes, so closing the grid cannot refund twice.
     */
    private static void clearCraftingGrid(Player player) {
        CraftingGrid grid = player.getCraftingGrid();
        if (grid == null) {
            return;
        }
        Item air = Item.get(Item.AIR);
        for (int slot = 0; slot < grid.getSize(); slot++) {
            if (!grid.getItem(slot).isNull()) {
                grid.setItemForce(slot, air);
            }
        }
    }

    private static boolean matchesOutput(Item[] output, Item result) {
        if (output == null || output.length == 0) {
            return false;
        }
        Item requested = output[0];
        return requested != null
                && !requested.isNull()
                && sameItem(requested, result)
                && requested.getCount() == result.getCount();
    }

    /**
     * 按 3x3 行优先布局规范化快照，兼容 4 格输入并保留每格数量；超过 9 格只允许空槽。
     * Normalizes row-major 3x3 snapshots, accepting four cells and tolerating empty padding past nine.
     */
    private static Item[] buildGrid(Item[] input) {
        if (input == null || input.length == 0) {
            return null;
        }
        for (int i = GRID_SIZE * GRID_SIZE; i < input.length; i++) {
            if (input[i] != null && !input[i].isNull()) {
                return null;
            }
        }
        Item[] grid = new Item[GRID_SIZE * GRID_SIZE];
        for (int i = 0; i < grid.length; i++) {
            grid[i] = Item.get(Item.AIR);
        }
        int[] smallSlots = {0, 1, 3, 4};
        for (int i = 0; i < Math.min(input.length, grid.length); i++) {
            if (input[i] != null) {
                int slot = input.length == smallSlots.length ? smallSlots[i] : i;
                grid[slot] = input[i].clone();
            }
        }
        return grid;
    }

    /**
     * 无序配方每格只贡献一个单位，快照的整摞数量不是消耗数量。
     * Each shapeless cell contributes one unit; snapshot stack counts are not consumption counts.
     */
    private static Item singleCount(Item item) {
        if (item == null) {
            return Item.get(Item.AIR);
        }
        if (item.isNull()) {
            return item;
        }
        Item single = item.clone();
        single.setCount(1);
        return single;
    }

    /**
     * 返回匹配到的实际材料（保留 damage/NBT）；配方模板只用于校验，不用于扣料。
     * Returns matched inputs with their real damage/NBT; templates validate but never select stacks.
     */
    private static List<Item> matchInput(Item[] grid, CraftingRecipe recipe) {
        if (recipe instanceof ShapedRecipe shaped) {
            if (shaped.getWidth() <= 0 || shaped.getHeight() <= 0
                    || shaped.getWidth() > GRID_SIZE || shaped.getHeight() > GRID_SIZE) {
                return null;
            }
            return matchShaped(grid, shaped);
        }
        if (recipe instanceof ShapelessRecipe shapeless) {
            return matchShapeless(grid, shapeless);
        }
        return null;
    }

    private static List<Item> matchShaped(Item[] grid, ShapedRecipe recipe) {
        for (int offsetY = 0; offsetY <= GRID_SIZE - recipe.getHeight(); offsetY++) {
            for (int offsetX = 0; offsetX <= GRID_SIZE - recipe.getWidth(); offsetX++) {
                List<Item> ingredients = matchShapedAt(grid, recipe, offsetX, offsetY, false);
                if (ingredients != null) {
                    return ingredients;
                }
                if (recipe.isAssumeSymetry()) {
                    ingredients = matchShapedAt(grid, recipe, offsetX, offsetY, true);
                    if (ingredients != null) {
                        return ingredients;
                    }
                }
            }
        }
        return null;
    }

    private static List<Item> matchShapedAt(Item[] grid, ShapedRecipe recipe, int offsetX, int offsetY, boolean mirrored) {
        List<Item> ingredients = new ArrayList<>();
        for (int y = 0; y < GRID_SIZE; y++) {
            for (int x = 0; x < GRID_SIZE; x++) {
                int recipeX = x - offsetX;
                int recipeY = y - offsetY;
                Item actual = grid[y * GRID_SIZE + x];
                boolean actualPresent = actual != null && !actual.isNull();

                boolean inside = recipeX >= 0 && recipeX < recipe.getWidth()
                        && recipeY >= 0 && recipeY < recipe.getHeight();
                Item expected = inside
                        ? recipe.getIngredient(mirrored ? recipe.getWidth() - 1 - recipeX : recipeX, recipeY)
                        : null;
                boolean expectedPresent = expected != null && !expected.isNull();

                if (!expectedPresent) {
                    if (actualPresent) {
                        return null;
                    }
                } else if (!actualPresent || !sameItem(actual, expected)
                        || actual.getCount() < expected.getCount()) {
                    return null;
                } else {
                    Item consumed = actual.clone();
                    consumed.setCount(expected.getCount());
                    ingredients.add(consumed);
                }
            }
        }
        return ingredients;
    }

    private static List<Item> matchShapeless(Item[] grid, ShapelessRecipe recipe) {
        List<Item> ingredients = new ArrayList<>();
        for (Item item : grid) {
            if (item != null && !item.isNull()) {
                ingredients.add(singleCount(item));
            }
        }

        // 无序配方的 count 按多个格子展开，每个匹配的实际格子贡献一个单位。
        // Expand shapeless counts over multiple cells, one unit from each actual cell.
        List<Item> needed = new ArrayList<>();
        for (Item ingredient : recipe.getIngredientList()) {
            if (ingredient != null && !ingredient.isNull()
                    && ingredient.getCount() > grid.length - needed.size()) {
                return null;
            }
            addIngredientUnits(needed, ingredient);
        }
        if (ingredients.size() != needed.size()) {
            return null;
        }
        int[] matched = new int[ingredients.size()];
        Arrays.fill(matched, -1);
        for (int ingredient = 0; ingredient < needed.size(); ingredient++) {
            if (!assignIngredient(ingredient, needed, ingredients, matched, new boolean[matched.length])) {
                return null;
            }
        }
        return ingredients;
    }

    // 通配材料可挪到另一个槽位，避免抢占后续指定 damage/NBT 的材料。
    // Reassign wildcard matches instead of stealing a later ingredient's specific damage/NBT.
    private static boolean assignIngredient(int ingredient, List<Item> needed, List<Item> actual,
                                            int[] matched, boolean[] visited) {
        for (int slot = 0; slot < actual.size(); slot++) {
            if (visited[slot] || !sameItem(actual.get(slot), needed.get(ingredient))) {
                continue;
            }
            visited[slot] = true;
            if (matched[slot] < 0 || assignIngredient(matched[slot], needed, actual, matched, visited)) {
                matched[slot] = ingredient;
                return true;
            }
        }
        return false;
    }

    private static void addIngredientUnit(List<Item> ingredients, Item ingredient) {
        if (ingredient == null || ingredient.isNull()) {
            return;
        }
        Item single = ingredient.clone();
        single.setCount(1);
        ingredients.add(single);
    }

    private static void addIngredientUnits(List<Item> ingredients, Item ingredient) {
        if (ingredient == null || ingredient.isNull()) {
            return;
        }
        int units = Math.max(1, ingredient.getCount());
        for (int i = 0; i < units; i++) {
            addIngredientUnit(ingredients, ingredient);
        }
    }

    /**
     * 按实际物品身份预留材料，允许跨多个背包槽位扣除；材料不足返回 {@code null}。
     * Reserves real item identities across slots; returns per-slot amounts, or {@code null} if short.
     */
    private static int[] reserveIngredients(PlayerInventory inventory, List<Item> ingredients) {
        int[] used = new int[inventory.getSize()];
        for (Item ingredient : ingredients) {
            int remaining = ingredient.getCount();
            for (int i = 0; i < inventory.getSize() && remaining > 0; i++) {
                Item current = inventory.getItem(i);
                if (current == null || current.isNull() || !current.equals(ingredient, true, true)) {
                    continue;
                }
                int amount = Math.min(remaining, current.getCount() - used[i]);
                used[i] += amount;
                remaining -= amount;
            }
            if (remaining > 0) {
                return null;
            }
        }
        return used;
    }

    /**
     * 扣料/入包事件全部审核通过后才提交，审核期间背包必须保持原快照；插件直接改包则中止。
     * Approves every debit/result event before committing; aborts if a plugin mutated the inventory.
     */
    private static List<Item> settleInventory(Player player, int[] used, List<Item> results) {
        PlayerInventory inventory = player.getInventory();
        Item[] before = new Item[used.length];
        Item[] debited = new Item[used.length];
        Item[] after = new Item[used.length];
        for (int slot = 0; slot < used.length; slot++) {
            before[slot] = inventory.getItem(slot);
            debited[slot] = before[slot].clone();
            if (used[slot] > 0) {
                debited[slot].setCount(before[slot].getCount() - used[slot]);
                if (debited[slot].isNull()) {
                    debited[slot] = Item.get(Item.AIR, 0, 0);
                }
            }
            after[slot] = debited[slot].clone();
        }
        List<Item> leftovers = new ArrayList<>();
        for (Item result : results) {
            Item remaining = result.clone();
            for (int pass = 0; pass < 2 && !remaining.isNull(); pass++) {
                for (int slot = 0; slot < after.length && !remaining.isNull(); slot++) {
                    Item current = after[slot];
                    boolean empty = current.isNull();
                    if (pass == 0 ? empty || !current.equals(remaining, true, true) : !empty) {
                        continue;
                    }
                    int count = empty ? 0 : current.getCount();
                    int amount = Math.min(remaining.getCount(),
                            Math.min(remaining.getMaxStackSize(), inventory.getMaxStackSize()) - count);
                    if (amount <= 0) {
                        continue;
                    }
                    Item target = remaining.clone();
                    target.setCount(count + amount);
                    after[slot] = target;
                    remaining.setCount(remaining.getCount() - amount);
                }
            }
            if (!remaining.isNull()) {
                leftovers.add(remaining);
            }
        }

        // 保留独立的扣料事件，避免产物覆盖同一槽位时绕过插件的扣料保护。
        // Keep debit events separate, even when a result later reuses the same slot.
        if (!approveInventoryChanges(inventory, before, before, debited)
                || !approveInventoryChanges(inventory, before, debited, after)) {
            return null;
        }
        // 提交期间不触发可取消事件；全部槽位就绪后再发包，避免回包事件看到半成品。
        // Commit without cancellable callbacks, then publish only the completed inventory.
        for (int slot = 0; slot < before.length; slot++) {
            if (!before[slot].equalsExact(after[slot])) {
                inventory.setItemForce(slot, after[slot]);
            }
        }
        clearCraftingGrid(player);
        for (int slot = 0; slot < before.length; slot++) {
            if (!before[slot].equalsExact(after[slot])) {
                inventory.sendSlot(slot, inventory.getViewers());
            }
        }
        return leftovers;
    }

    private static boolean approveInventoryChanges(PlayerInventory inventory, Item[] snapshot,
                                                   Item[] before, Item[] after) {
        for (int slot = 0; slot < before.length; slot++) {
            if (before[slot].equalsExact(after[slot])) {
                continue;
            }
            EntityInventoryChangeEvent event = new EntityInventoryChangeEvent(
                    inventory.getHolder(), before[slot].clone(), after[slot].clone(), slot);
            inventory.getHolder().getServer().getPluginManager().callEvent(event);
            if (event.isCancelled() || event.getNewItem() == null
                    || !event.getNewItem().equalsExact(after[slot])
                    || !matchesInventorySnapshot(inventory, snapshot)) {
                return false;
            }
        }
        return true;
    }

    private static boolean matchesInventorySnapshot(PlayerInventory inventory, Item[] snapshot) {
        if (inventory.getSize() != snapshot.length) {
            return false;
        }
        for (int slot = 0; slot < snapshot.length; slot++) {
            if (!inventory.getItem(slot).equalsExact(snapshot[slot])) {
                return false;
            }
        }
        return true;
    }

    private static boolean sameItem(Item actual, Item expected) {
        return actual != null && expected != null
                && !actual.isNull()
                && actual.equals(expected, expected.hasMeta(), expected.hasCompoundTag());
    }
}
