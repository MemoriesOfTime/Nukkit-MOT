package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.passive.EntityVillager;
import cn.nukkit.event.inventory.CraftItemEvent;
import cn.nukkit.event.inventory.EnchantItemEvent;
import cn.nukkit.event.inventory.SmithingTableEvent;
import cn.nukkit.event.inventory.StonecutterItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.CraftingDataPacket;
import cn.nukkit.network.protocol.PlayerEnchantOptionsPacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import cn.nukkit.utils.TradeRecipeBuildUtils;
import lombok.extern.log4j.Log4j2;

import java.util.*;

/**
 * Resolves the recipe referenced by a CraftRecipeAction and dispatches to one of
 * three branches depending on the network id range:
 * <ul>
 *     <li>{@code >= ENCH_RECIPEID}: enchantment option from
 *     {@link PlayerEnchantOptionsPacket#RECIPE_MAP}</li>
 *     <li>{@code >= TRADE_RECIPEID}: villager trade from
 *     {@link TradeRecipeBuildUtils#RECIPE_MAP}</li>
 *     <li>otherwise: regular crafting recipe in
 *     {@link cn.nukkit.inventory.CraftingManager}</li>
 * </ul>
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public class CraftRecipeActionProcessor implements ItemStackRequestActionProcessor<CraftRecipeAction> {

    public static final String RECIPE_NET_ID_KEY = "recipeNetId";
    public static final String ENCH_RECIPE_KEY = "enchRecipe";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RECIPE;
    }

    @Override
    public ActionResponse handle(CraftRecipeAction action, Player player, ItemStackRequestContext context) {
        int recipeNetId = action.getRecipeNetworkId();
        context.put(RECIPE_NET_ID_KEY, recipeNetId);

        // TRADE_RECIPEID (0x20000000) > ENCH_RECIPEID (0x10000000); dispatch in
        // descending order so a trade id does not get mistaken for an enchant id.
        if (recipeNetId >= TradeRecipeBuildUtils.TRADE_RECIPEID) {
            return handleTrade(action, player, context);
        }
        if (recipeNetId >= PlayerEnchantOptionsPacket.ENCH_RECIPEID) {
            return handleEnchant(action, player, context);
        }
        if (recipeNetId == CraftingDataPacket.SMITHING_ARMOR_TRIM_NETWORK_ID) {
            return handleSmithingTrim(player, context);
        }

        Recipe recipe = player.getServer().getCraftingManager().getRecipeByNetworkId(recipeNetId);
        if (recipe == null) {
            return context.error();
        }

        // Stonecutter 走独立事件链路（StonecutterItemEvent），不经 CraftItemEvent，
        // 与旧 StonecutterTransaction 保持语义一致。
        if (recipe instanceof StonecutterRecipe stonecutterRecipe) {
            return handleStonecutter(player, stonecutterRecipe, action, context);
        }

        // Fire CraftItemEvent before applying the recipe so plugins can veto SA
        // manual crafting. Input items come from the open crafting grid (big
        // workbench if opened, otherwise the 2x2 personal grid).
        CraftItemEvent craftEvent = new CraftItemEvent(player, collectCraftingInput(player), recipe);
        Server.getInstance().getPluginManager().callEvent(craftEvent);
        if (craftEvent.isCancelled()) {
            return context.error();
        }

        context.put(CreateActionProcessor.RECIPE_DATA_KEY, recipe);

        if (recipe instanceof MultiRecipe multiRecipe) {
            CraftResultsDeprecatedAction resultsAction = findCraftResultsAction(
                    context.getItemStackRequest().getActions(),
                    context.getCurrentActionIndex() + 1
            );
            if (resultsAction == null || resultsAction.getResultItems() == null || resultsAction.getResultItems().length == 0) {
                return context.error();
            }
            Item output = resultsAction.getResultItems()[0];
            if (output == null || output.isNull()) {
                return context.error();
            }
            if (!validateCraftingRecipe(player, multiRecipe, output, 1)) {
                return context.error();
            }
            if (!validateMultiRecipeConsumePlan(player, multiRecipe, output, context)) {
                return context.error();
            }
            return context.success();
        }

        // Smithing dispatch: trim recipes delegate to the inventory's trim logic;
        // transform recipes preserve the equipment's NBT onto the result.
        if (recipe instanceof SmithingTrimRecipe) {
            return handleSmithingTrim(player, context);
        }
        if (recipe instanceof SmithingTransformRecipe smithingTransform) {
            return handleSmithingUpgrade(smithingTransform, player, context);
        }

        Item recipeResult = recipe instanceof MultiRecipe multi ? multi.getResult() : recipe.getResult();
        if (recipeResult == null || recipeResult.isNull()) {
            return null;
        }
        int times = Math.max(1, action.getNumberOfRequestedCrafts());
        if (!validateCraftingRecipe(player, recipe, recipeResult, times)) {
            return context.error();
        }
        if (recipe instanceof CraftingRecipe craftingRecipe && !validateCraftingConsumePlan(player, craftingRecipe, times, context)) {
            return context.error();
        }
        Item output = recipeResult.clone();
        output.setCount(output.getCount() * times);
        if (recipe instanceof UserDataShapelessRecipe) {
            applyInputNbt(output, collectCraftingInputList(player));
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

    private ActionResponse handleEnchant(CraftRecipeAction action, Player player, ItemStackRequestContext context) {
        Inventory inventory = player.getTopWindow().orElse(null);
        if (!(inventory instanceof EnchantInventory enchantInventory)) {
            return context.error();
        }
        if (!enchantInventory.hasPublishedOption(action.getRecipeNetworkId())) {
            log.warn("{}: enchant recipe netId {} is not published for the current window",
                    player.getName(), action.getRecipeNetworkId());
            return context.error();
        }
        PlayerEnchantOptionsPacket.EnchantOptionData option =
                PlayerEnchantOptionsPacket.RECIPE_MAP.get(action.getRecipeNetworkId());
        if (option == null) {
            log.warn("{}: unknown enchant recipe netId {}", player.getName(), action.getRecipeNetworkId());
            return context.error();
        }
        Item first = enchantInventory.getInputSlot();
        if (first.isNull()) {
            return context.error();
        }
        List<Enchantment> enchantments = new ArrayList<>();
        for (PlayerEnchantOptionsPacket.EnchantData data : option.getEnchants0()) {
            Enchantment enchantment = Enchantment.getEnchantment(data.getType());
            if (enchantment != null) {
                if (enchantment.isTreasure() || enchantment.isCurse()) {
                    return context.error();
                }
                if (!isApplicableEnchant(enchantment, first)) {
                    return context.error();
                }
                enchantments.add(enchantment.setLevel(data.getLevel()));
            }
        }
        int cost = option.getPrimarySlot() + 1;
        if (!player.isCreative()) {
            if (player.getExperienceLevel() < cost || player.getExperienceLevel() < option.getMinLevel()) {
                return context.error();
            }
            Item reagent = enchantInventory.getReagentSlot();
            if (reagent.isNull() || reagent.getCount() < cost || !reagent.equals(Item.get(Item.DYE, 4), true, false)) {
                return context.error();
            }
            List<Item> expectedConsumes = new ArrayList<>(2);
            addExpectedConsumeItem(expectedConsumes, first, 1);
            addExpectedConsumeItem(expectedConsumes, reagent, cost);
            if (!validateExpectedConsumePlan(player, expectedConsumes, context)) {
                return context.error();
            }
        }

        Item output = first.clone();
        if (output.getId() == Item.BOOK) {
            output = Item.get(Item.ENCHANTED_BOOK);
        }
        output.setCount(1);
        if (!enchantments.isEmpty()) {
            output.addEnchantment(enchantments.toArray(Enchantment.EMPTY_ARRAY));
        }
        output.autoAssignStackNetworkId();

        EnchantItemEvent event = new EnchantItemEvent(enchantInventory, first.clone(), output, cost, player);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        Item finalOutput = event.getNewItem();
        int finalCost = event.getXpCost();

        if (!player.isCreative()) {
            context.onCommit(() -> player.setExperience(player.getExperience(), player.getExperienceLevel() - finalCost));
        }
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, finalOutput, false);
        context.onCommit(() -> enchantInventory.releasePublishedOption(action.getRecipeNetworkId()));
        context.put(ENCH_RECIPE_KEY, true);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                finalOutput.getCount(), finalOutput.getStackNetId(),
                finalOutput.hasCustomName() ? finalOutput.getCustomName() : "",
                finalOutput.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }

    private static boolean isApplicableEnchant(Enchantment enchantment, Item input) {
        return input.getId() == Item.BOOK || enchantment.canEnchant(input);
    }

    private ActionResponse handleTrade(CraftRecipeAction action, Player player, ItemStackRequestContext context) {
        Optional<Inventory> topWindow = player.getTopWindow();
        if (topWindow.isEmpty() || !(topWindow.get() instanceof TradeInventory tradeInventory)) {
            return context.error();
        }
        CompoundTag recipe = tradeInventory.getAssignedRecipe(action.getRecipeNetworkId());
        if (recipe == null) {
            log.warn("{}: trade recipe netId {} is not assigned to the current villager",
                    player.getName(), action.getRecipeNetworkId());
            return context.error();
        }
        int times = Math.max(1, action.getNumberOfRequestedCrafts());
        int maxUses = recipe.contains("maxUses") ? recipe.getInt("maxUses") : Integer.MAX_VALUE;
        int uses = recipe.contains("uses") ? recipe.getInt("uses") : 0;
        if (uses + times > maxUses) {
            return context.error();
        }

        Item buyA = tradeInventory.getUnclonedItem(0);
        Item buyB = tradeInventory.getUnclonedItem(1);
        boolean hasBuyA = recipe.contains("buyA");
        boolean hasBuyB = recipe.contains("buyB");

        if (hasBuyA && checkTrade(recipe.getCompound("buyA"), buyA, times)) {
            return context.error();
        }
        if (hasBuyB && checkTrade(recipe.getCompound("buyB"), buyB, times)) {
            return context.error();
        }

        List<Item> expectedConsumes = new ArrayList<>(2);
        if (hasBuyA) {
            addExpectedTradeConsumeItem(expectedConsumes, recipe.getCompound("buyA"), times);
        }
        if (hasBuyB) {
            addExpectedTradeConsumeItem(expectedConsumes, recipe.getCompound("buyB"), times);
        }
        if (!validateExpectedConsumePlan(player, expectedConsumes, context)) {
            return context.error();
        }

        Item output = NBTIO.getItemHelper(recipe.getCompound("sell"));
        if (output == null || output.isNull()) {
            return context.error();
        }
        output.setCount(output.getCount() * times);
        output.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, output, false);

        int rewardExp = recipe.contains("rewardExp") ? recipe.getInt("rewardExp") : 0;
        EntityVillager villager = tradeInventory.getHolder();
        int traderExp = recipe.contains("traderExp") ? recipe.getInt("traderExp") : 0;
        context.onCommit(() -> {
            recipe.putInt("uses", uses + times);
            if (rewardExp > 0) {
                player.addExperience(rewardExp * times);
            }
            if (villager != null) {
                villager.namedTag.putBoolean("traded", true);
                if (traderExp > 0) {
                    villager.addExperience(traderExp * times);
                }
            }
        });

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

    private boolean checkTrade(CompoundTag expected, Item actual, int multiplier) {
        if (actual == null || actual.isNull()) {
            return true;
        }
        int required = Math.max(expected.getByte("Count") * Math.max(1, multiplier), 1);
        if (actual.getCount() < required) {
            return true;
        }
        String expectedName = expected.getString("Name");
        String actualName = actual.getNamespaceId();
        if (expectedName != null && !expectedName.isEmpty() && !expectedName.equals(actualName)) {
            return true;
        }
        if (expected.contains("Damage") && expected.getShort("Damage") != actual.getDamage()) {
            return true;
        }
        if (expected.contains("tag")) {
            CompoundTag expectedTag = expected.getCompound("tag");
            CompoundTag actualTag = actual.getNamedTag();
            if (actualTag == null || !expectedTag.equals(actualTag)) {
                return true;
            }
        }
        return false;
    }

    private static void addExpectedTradeConsumeItem(List<Item> expectedConsumes, CompoundTag tag, int times) {
        Item item = NBTIO.getItemHelper(tag);
        if (item == null || item.isNull()) {
            return;
        }
        addExpectedConsumeItem(expectedConsumes, item, Math.max(1, item.getCount()) * Math.max(1, times));
    }

    /**
     * Collects non-empty items from the player's active crafting grid (big
     * workbench if one is open, otherwise the personal 2x2 grid). Used as the
     * {@code input} parameter of {@link CraftItemEvent} so plugin listeners can
     * inspect what the client intends to consume.
     */
    private static Item[] collectCraftingInput(Player player) {
        List<Item> items = collectCraftingInputList(player);
        return items.toArray(Item.EMPTY_ARRAY);
    }

    static List<Item> collectCraftingInputList(Player player) {
        CraftingGrid grid = getActiveCraftingGrid(player);
        int size = grid.getSize();
        List<Item> items = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            Item item = grid.getUnclonedItem(i);
            if (item != null && !item.isNull()) {
                items.add(item.clone());
            }
        }
        return items;
    }

    /**
     * Copies the NBT of the first NBT-carrying input onto the output, so {@link UserDataShapelessRecipe} dyeing keeps container contents.
     */
    static void applyInputNbt(Item output, List<Item> inputs) {
        for (Item inputItem : inputs) {
            if (inputItem != null && !inputItem.isNull() && inputItem.hasCompoundTag()) {
                output.setCompoundTag(inputItem.getCompoundTag());
                return;
            }
        }
    }

    private static CraftingGrid getActiveCraftingGrid(Player player) {
        Inventory top = player.getTopWindow().orElse(null);
        if (top instanceof CraftingGrid openGrid) {
            return openGrid;
        }
        CraftingGrid grid = player.getCraftingGrid();
        return grid != null ? grid : player.getUIInventory().getCraftingGrid();
    }

    static boolean validateCraftingRecipe(Player player, Recipe recipe, Item output, int multiplier) {
        List<Item> inputs = collectCraftingInputList(player);
        if (recipe instanceof MultiRecipe multiRecipe) {
            return multiRecipe.canExecute(player, output.clone(), inputs);
        }
        if (!(recipe instanceof CraftingRecipe craftingRecipe)) {
            return true;
        }

        Item primaryOutput = output.clone();
        primaryOutput.setCount(primaryOutput.getCount() * Math.max(1, multiplier));
        List<Item> extraOutputs = scaleItems(craftingRecipe.getExtraResults(), Math.max(1, multiplier));
        List<Item> cappedInputs = capInputsToIngredients(inputs, craftingRecipe.getIngredientsAggregate(), Math.max(1, multiplier));
        Recipe matched = player.getServer().getCraftingManager().matchRecipe(cappedInputs, primaryOutput, extraOutputs);
        if (matched == recipe) {
            return true;
        }
        // 兜底：插件若绕过去重注册了重复配方，matchRecipe 返回的实例与 client 所选不同；
        // 此时按输出、副产物、材料聚合做内容等价比较，等价即放行。
        // <p>Fallback: accept a content-equivalent recipe when matchRecipe returns a different instance.
        if (matched instanceof CraftingRecipe matchedCR
                && matchedCR.getResult().equals(craftingRecipe.getResult())
                && matchedCR.getIngredientsAggregate().equals(craftingRecipe.getIngredientsAggregate())
                && matchedCR.getExtraResults().equals(craftingRecipe.getExtraResults())) {
            log.debug("{}: accepted content-equivalent recipe (client recipeId={}, server matched recipeId={})",
                    player.getName(), craftingRecipe.getRecipeId(), matchedCR.getRecipeId());
            return true;
        }
        log.debug("{}: crafting recipe validation failed, no match for client-selected recipe (recipeId={})",
                player.getName(), craftingRecipe.getRecipeId());
        return false;
    }


    /**
     * 将合成格物品数量封顶到"配方材料×multiplier",使 matchItemList 的严格相等匹配在玩家放入整堆材料时仍成立。
     * <p>
     * Caps grid item counts at "ingredient×multiplier" so matchItemList's exact-equality match holds even with full stacks.
     */
    static List<Item> capInputsToIngredients(List<Item> inputs, List<Item> ingredientsAggregate, int multiplier) {
        List<Item> need = new ArrayList<>();
        for (Item ingredient : ingredientsAggregate) {
            if (ingredient == null || ingredient.isNull()) {
                continue;
            }
            Item clone = ingredient.clone();
            clone.setCount(clone.getCount() * multiplier);
            need.add(clone);
        }
        List<Item> capped = new ArrayList<>(inputs.size());
        for (Item input : inputs) {
            if (input == null || input.isNull()) {
                continue;
            }
            Item clone = input.clone();
            int allowed = 0;
            for (Item n : need) {
                if (n.getCount() > 0 && n.equals(clone, n.hasMeta(), n.hasCompoundTag())) {
                    int take = Math.min(n.getCount(), clone.getCount());
                    allowed += take;
                    n.setCount(n.getCount() - take);
                }
            }
            if (allowed <= 0) {
                // 配额耗尽或配方不需要此材料:跳过,否则总量超标会让 matchItemList 失败。
                continue;
            }
            clone.setCount(Math.min(clone.getCount(), allowed));
            capped.add(clone);
        }
        return capped;
    }

    static List<Item> scaleItems(List<Item> items, int multiplier) {
        List<Item> scaled = new ArrayList<>(items.size());
        for (Item item : items) {
            if (item == null || item.isNull()) {
                continue;
            }
            Item clone = item.clone();
            clone.setCount(clone.getCount() * multiplier);
            scaled.add(clone);
        }
        return scaled;
    }

    private static boolean validateCraftingConsumePlan(Player player, CraftingRecipe recipe, int times, ItemStackRequestContext context) {
        List<Item> expected = new ArrayList<>();
        for (Item ingredient : recipe.getIngredientsAggregate()) {
            addExpectedConsumeItem(expected, ingredient, ingredient == null ? 0 : ingredient.getCount() * times);
        }

        return validateExpectedConsumePlan(player, expected, context);
    }

    static boolean validateMultiRecipeConsumePlan(Player player, MultiRecipe recipe, Item output, ItemStackRequestContext context) {
        CraftingGrid grid = getActiveCraftingGrid(player);
        Set<Integer> occupiedSlots = new HashSet<>();
        for (int slot = 0; slot < grid.getSize(); slot++) {
            Item item = grid.getUnclonedItem(slot);
            if (item != null && !item.isNull()) {
                occupiedSlots.add(slot);
            }
        }
        if (occupiedSlots.isEmpty()) {
            return false;
        }

        List<ConsumeAction> consumeActions = CraftRecipeAutoProcessor.findAllConsumeActions(
                context.getItemStackRequest().getActions(),
                context.getCurrentActionIndex() + 1);
        if (consumeActions.size() != occupiedSlots.size()) {
            return false;
        }

        Set<Integer> consumedSlots = new HashSet<>();
        List<Item> consumedItems = new ArrayList<>(consumeActions.size());
        for (ConsumeAction consume : consumeActions) {
            if (consume.getCount() <= 0) {
                return false;
            }
            var source = consume.getSource();
            if (source == null) {
                return false;
            }
            var inventory = NetworkMapping.getInventory(player, source.getContainer(), source.getDynamicId());
            if (inventory != grid) {
                return false;
            }
            int slot = NetworkMapping.toInternalSlot(source.getContainer(), source.getSlot());
            if (!occupiedSlots.contains(slot) || !consumedSlots.add(slot)) {
                return false;
            }
            Item item = inventory.getItem(slot);
            if (item.isNull() || item.getCount() < consume.getCount()) {
                return false;
            }
            if (hasStackNetworkIdMismatch(item.getStackNetId(), source.getStackNetworkId())) {
                return false;
            }
            Item consumed = item.clone();
            consumed.setCount(consume.getCount());
            consumedItems.add(consumed);
        }

        return consumedSlots.size() == occupiedSlots.size()
                && recipe.canExecute(player, output.clone(), consumedItems);
    }

    private static boolean hasStackNetworkIdMismatch(int serverNetId, int clientNetId) {
        return serverNetId > 0 && clientNetId > 0 && serverNetId != clientNetId;
    }

    static void addExpectedConsumeItem(List<Item> expected, Item item, int count) {
        if (item == null || item.isNull() || count <= 0) {
            return;
        }
        Item expectedItem = item.clone();
        expectedItem.setCount(count);
        expected.add(expectedItem);
    }

    static boolean validateExpectedConsumePlan(Player player, List<Item> expected, ItemStackRequestContext context) {
        List<Item> expectedConsumes = new ArrayList<>(expected.size());
        for (Item item : expected) {
            if (item == null || item.isNull() || item.getCount() <= 0) {
                continue;
            }
            expectedConsumes.add(item.clone());
        }
        if (expectedConsumes.isEmpty()) {
            return true;
        }

        List<ConsumeAction> consumeActions = CraftRecipeAutoProcessor.findAllConsumeActions(
                context.getItemStackRequest().getActions(),
                context.getCurrentActionIndex() + 1);

        List<Item> actual = new ArrayList<>();
        for (ConsumeAction consume : consumeActions) {
            if (consume.getCount() <= 0) {
                return false;
            }
            var source = consume.getSource();
            var inventory = NetworkMapping.getInventory(player, source.getContainer(), source.getDynamicId());
            if (inventory == null) {
                return false;
            }
            int slot = NetworkMapping.toInternalSlot(source.getContainer(), source.getSlot());
            Item item = inventory.getItem(slot);
            if (item.isNull() || item.getCount() < consume.getCount()) {
                return false;
            }
            Item consumed = item.clone();
            consumed.setCount(consume.getCount());
            actual.add(consumed);
        }

        return Recipe.matchItemList(actual, expectedConsumes);
    }

    private static CraftResultsDeprecatedAction findCraftResultsAction(ItemStackRequestAction[] actions, int startIndex) {
        for (int i = startIndex; i < actions.length; i++) {
            if (actions[i] instanceof CraftResultsDeprecatedAction craftResults) {
                return craftResults;
            }
        }
        return null;
    }

    /**
     * Handles Smithing "transform" recipes (e.g. Netherite upgrade). The result
     * inherits the original equipment's NBT (enchantments, custom name,
     * durability) while switching to the recipe's result item type.
     */
    private ActionResponse handleSmithingUpgrade(SmithingTransformRecipe recipe, Player player, ItemStackRequestContext context) {
        Inventory inventory = player.getTopWindow().orElse(null);
        if (!(inventory instanceof SmithingInventory smithingInventory)) {
            return context.error();
        }
        Item equipment = smithingInventory.getEquipment();
        Item ingredient = smithingInventory.getIngredient();
        Item template = smithingInventory.getTemplate();
        SmithingRecipe matchedRecipe = player.getServer().getCraftingManager()
                .matchSmithingRecipe(new ArrayList<>(List.of(equipment, ingredient, template)));
        if (matchedRecipe != recipe) {
            return context.error();
        }
        Item result = recipe.getFinalResult(equipment, template);
        if (result == null || result.isNull()) {
            return context.error();
        }
        if (!validateSmithingConsumePlan(player, context, equipment, ingredient, template)) {
            return context.error();
        }
        if (!fireSmithingEvent(smithingInventory, result, player)) {
            return context.error();
        }
        result.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, result, false);
        return buildCreatedOutputResponse(context, result);
    }

    /**
     * Handles Smithing "trim" recipes: applies a cosmetic trim (pattern +
     * material) to armor. Reuses {@link SmithingInventory#getTrimOutPutItem()}
     * which already implements the vanilla trim NBT composition.
     */
    private ActionResponse handleSmithingTrim(Player player, ItemStackRequestContext context) {
        Inventory inventory = player.getTopWindow().orElse(null);
        if (!(inventory instanceof SmithingInventory smithingInventory)) {
            return context.error();
        }
        Item result = smithingInventory.getTrimOutPutItem();
        if (result == null || result.isNull()) {
            return context.error();
        }
        if (!validateSmithingConsumePlan(player, context,
                smithingInventory.getEquipment(), smithingInventory.getIngredient(), smithingInventory.getTemplate())) {
            return context.error();
        }
        if (!fireSmithingEvent(smithingInventory, result, player)) {
            return context.error();
        }
        result.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, result, false);
        return buildCreatedOutputResponse(context, result);
    }

    private static boolean validateSmithingConsumePlan(Player player, ItemStackRequestContext context,
                                                       Item equipment, Item ingredient, Item template) {
        List<Item> expectedConsumes = new ArrayList<>(3);
        addExpectedConsumeItem(expectedConsumes, equipment, 1);
        addExpectedConsumeItem(expectedConsumes, ingredient, 1);
        addExpectedConsumeItem(expectedConsumes, template, 1);
        return validateExpectedConsumePlan(player, expectedConsumes, context);
    }

    /**
     * Mirror {@code SmithingTransaction.execute()}: plugins receive the full set
     * of input slots + projected output so they can veto smithing-table usage.
     * Returns {@code false} when the event is cancelled.
     */
    private static boolean fireSmithingEvent(SmithingInventory inventory, Item result, Player player) {
        SmithingTableEvent event = new SmithingTableEvent(
                inventory,
                inventory.getEquipment().clone(),
                result.clone(),
                inventory.getIngredient().clone(),
                inventory.getTemplate().clone(),
                player
        );
        Server.getInstance().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    /**
     * Resolve the stonecutter recipe against the currently open
     * {@link StonecutterInventory} and fire {@link StonecutterItemEvent}.
     * Mirrors the legacy {@code StonecutterTransaction} flow — in particular it
     * does NOT fire {@link CraftItemEvent}, so plugins listening only to
     * StonecutterItemEvent behave the same as before.
     */
    private ActionResponse handleStonecutter(Player player, StonecutterRecipe recipe, CraftRecipeAction action, ItemStackRequestContext context) {
        Inventory top = player.getTopWindow().orElse(null);
        if (!(top instanceof StonecutterInventory stonecutterInventory)) {
            return context.error();
        }
        Item input = stonecutterInventory.getInput().clone();
        if (input.isNull()) {
            return context.error();
        }
        int times = Math.max(1, action.getNumberOfRequestedCrafts());
        Item ingredient = recipe.getIngredient();
        if (!ingredient.equals(input, ingredient.hasMeta(), false)
                || input.getCount() < ingredient.getCount() * times) {
            return context.error();
        }
        Item output = recipe.getResult();
        output.setCount(output.getCount() * times);

        List<Item> expectedConsumes = new ArrayList<>(1);
        addExpectedConsumeItem(expectedConsumes, ingredient, ingredient.getCount() * times);
        if (!validateExpectedConsumePlan(player, expectedConsumes, context)) {
            return context.error();
        }

        StonecutterItemEvent event = new StonecutterItemEvent(stonecutterInventory, input, output.clone(), player);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        context.put(CreateActionProcessor.RECIPE_DATA_KEY, recipe);
        output.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, output, false);
        return buildCreatedOutputResponse(context, output);
    }

    private ActionResponse buildCreatedOutputResponse(ItemStackRequestContext context, Item output) {
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
