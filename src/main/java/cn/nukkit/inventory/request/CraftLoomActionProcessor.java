package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.LoomItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.LoomInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBanner;
import cn.nukkit.item.ItemDye;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftLoomAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import cn.nukkit.utils.BannerPattern;
import cn.nukkit.utils.DyeColor;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Loom pattern/color application. Replicates LoomTransaction's logic: takes the
 * banner + dye (+ optional pattern item) from the LoomInventory and writes the
 * patterned result to CREATED_OUTPUT. The subsequent CONSUME/TAKE actions
 * remove the ingredients and deliver the result to the player.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public class CraftLoomActionProcessor implements ItemStackRequestActionProcessor<CraftLoomAction> {

    public static final String LOOM_PATTERN_KEY = "loomPatternId";
    public static final String LOOM_TIMES_KEY = "loomTimesCrafted";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_LOOM;
    }

    @Override
    public ActionResponse handle(CraftLoomAction action, Player player, ItemStackRequestContext context) {
        context.put(LOOM_PATTERN_KEY, action.getPatternId());
        context.put(LOOM_TIMES_KEY, action.getTimesCrafted());

        Optional<Inventory> topWindow = player.getTopWindow();
        if (topWindow.isEmpty() || !(topWindow.get() instanceof LoomInventory loomInventory)) {
            return context.error();
        }

        Item banner = loomInventory.getBanner();
        Item dye = loomInventory.getDye();
        if (banner == null || banner.isNull() || !(banner instanceof ItemBanner bannerItem)
                || dye == null || dye.isNull()) {
            return context.error();
        }

        BannerPattern.Type patternType = null;
        String patternId = action.getPatternId();
        if (patternId != null && !patternId.isBlank()) {
            patternType = BannerPattern.Type.getByName(patternId);
            if (patternType == null) {
                return context.error();
            }
        }

        DyeColor dyeColor = DyeColor.BLACK;
        if (dye instanceof ItemDye itemDye) {
            dyeColor = itemDye.getDyeColor();
        }

        int times = Math.max(1, action.getTimesCrafted());
        ItemBanner result = (ItemBanner) bannerItem.clone();
        result.setCount(times);
        if (patternType != null) {
            result.addPattern(new BannerPattern(patternType, dyeColor));
        } else {
            result.setBaseColor(dyeColor);
        }

        LoomItemEvent event = new LoomItemEvent(loomInventory, result.clone(), player);
        Server.getInstance().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        List<Item> expectedConsumes = new ArrayList<>(2);
        CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, banner, times);
        CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, dye, times);
        if (!CraftRecipeActionProcessor.validateExpectedConsumePlan(player, expectedConsumes, context)) {
            return context.error();
        }

        result.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, result, false);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                0, 0, result.getCount(), result.getStackNetId(),
                result.hasCustomName() ? result.getCustomName() : "",
                result.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }
}
