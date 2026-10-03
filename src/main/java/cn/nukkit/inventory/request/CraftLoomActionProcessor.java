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

    /**
     * Vanilla limit on stacked patterns per banner. The 7th visual layer is the
     * banner's base colour, so {@code Patterns} list holds at most 6 entries
     * before further additions are rejected.
     */
    private static final int MAX_BANNER_PATTERNS = 6;

    /**
     * Illager/ominous banner — {@code Type=1} on the banner NBT. Vanilla refuses
     * to apply any further pattern to it so loom output is the original banner.
     */
    private static final int OMINOUS_BANNER_TYPE = 1;

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

        // Vanilla: ominous banners (Type=1) reject all loom operations.
        if (bannerItem.hasCompoundTag()
                && bannerItem.getNamedTag().contains("Type")
                && bannerItem.getNamedTag().getInt("Type") == OMINOUS_BANNER_TYPE) {
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

        // Vanilla: applying a new pattern requires the banner has < 6 patterns.
        // Pure dye operations (no patternType) only repaint the base and are
        // unaffected by the limit.
        if (patternType != null && bannerItem.getPatternsSize() >= MAX_BANNER_PATTERNS) {
            return context.error();
        }

        // Vanilla: "special" patterns (creeper/skull/flower/mojang/flow/guster)
        // require a matching banner-pattern item in the material slot. The
        // pattern item itself is NOT consumed (acts like a tool).
        Item materialItem = loomInventory.getPattern();
        if (patternType != null && requiresPatternItem(patternType)) {
            if (materialItem == null || materialItem.isNull()
                    || !isMatchingPatternItem(patternType, materialItem)) {
                return context.error();
            }
        }

        DyeColor dyeColor = DyeColor.BLACK;
        if (dye instanceof ItemDye itemDye) {
            dyeColor = itemDye.getDyeColor();
        }

        int times = Math.max(1, action.getTimesCrafted());

        // Validate the consume plan before firing the event so a rejected
        // request never surfaces to plugin handlers as a success.
        List<Item> expectedConsumes = new ArrayList<>(2);
        CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, banner, times);
        CraftRecipeActionProcessor.addExpectedConsumeItem(expectedConsumes, dye, times);
        if (!CraftRecipeActionProcessor.validateExpectedConsumePlan(player, expectedConsumes, context)) {
            return context.error();
        }

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

        result.autoAssignStackNetworkId();
        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, result, false);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT,
                result.getCount(), result.getStackNetId(),
                result.hasCustomName() ? result.getCustomName() : "",
                result.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }

    private static boolean requiresPatternItem(BannerPattern.Type type) {
        return switch (type) {
            case PATTERN_CREEPER, PATTERN_SKULL, PATTERN_FLOWER, PATTERN_MOJANG,
                 PATTERN_FLOW, PATTERN_GUSTER,
                 PATTERN_BRICK, PATTERN_CURLY_BORDER -> true;
            default -> false;
        };
    }

    private static boolean isMatchingPatternItem(BannerPattern.Type type, Item material) {
        return switch (type) {
            case PATTERN_FLOW -> Item.FLOW_BANNER_PATTERN.equals(material.getNamespaceId());
            case PATTERN_GUSTER -> Item.GUSTER_BANNER_PATTERN.equals(material.getNamespaceId());
            // Legacy banner pattern item (id 434) uses meta to distinguish
            // creeper/skull/flower/mojang/bricks/curly_border variants; client
            // UI gates this by greying out incompatible patterns, so we accept
            // any meta here.
            case PATTERN_CREEPER, PATTERN_SKULL, PATTERN_FLOWER, PATTERN_MOJANG,
                 PATTERN_BRICK, PATTERN_CURLY_BORDER ->
                    material.getId() == Item.BANNER_PATTERN;
            default -> true;
        };
    }
}
