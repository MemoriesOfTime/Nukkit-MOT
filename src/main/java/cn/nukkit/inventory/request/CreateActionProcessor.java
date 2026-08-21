package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.CraftingRecipe;
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

import java.util.*;

/**
 * CreateAction is sent by the client for multi-output recipes to pick a specific
 * result slot (firework star variants, banner patterns) and to collect recipe
 * leftovers such as the empty buckets returned when milk is used to craft a cake.
 * Resolves the recipe cached by CraftRecipeAction and writes the chosen result
 * into CREATED_OUTPUT.
 * <p>
 * 客户端按 SAI 契约先发 Consume 清空合成格再发 Create，因此这里不得重读合成格做配方
 * 校验（CraftRecipeAction 已对完整格子校验过）；改为限制每个输出下标仅能 Create 一次。
 */
public class CreateActionProcessor implements ItemStackRequestActionProcessor<CreateAction> {

    public static final String RECIPE_DATA_KEY = "recipe";
    /** 多输出配方暂存的完整输出（主产物+副产物） */
    public static final String RECIPE_OUTPUTS_KEY = "recipeOutputs";
    /** 本请求内已被 Create 的输出下标，防止重复 Create+Place 复制产物 */
    public static final String CREATED_SLOTS_KEY = "createdSlots";

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
            List<Item> storedOutputs = context.get(RECIPE_OUTPUTS_KEY);
            if (storedOutputs != null) {
                results = storedOutputs;
            } else if (recipe instanceof CraftingRecipe craftingRecipe) {
                results = craftingRecipe.getAllResults();
            } else {
                results = List.of(recipe.getResult());
            }
        }
        int slot = action.getSlot();
        if (slot < 0 || slot >= results.size()) {
            return context.error();
        }

        Set<Integer> createdSlots = context.get(CREATED_SLOTS_KEY);
        if (createdSlots == null) {
            createdSlots = new HashSet<>();
            context.put(CREATED_SLOTS_KEY, createdSlots);
        }
        if (!createdSlots.add(slot)) {
            return context.error();
        }

        Item output = results.get(slot).clone();
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
