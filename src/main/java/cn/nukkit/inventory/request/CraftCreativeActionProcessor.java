package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftCreativeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;

/**
 * Handles creative-mode item creation. The client sends this when it wants to
 * take a stack out of the creative catalog; the server materialises the item
 * into CREATED_OUTPUT for a subsequent TAKE action to move elsewhere.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class CraftCreativeActionProcessor implements ItemStackRequestActionProcessor<CraftCreativeAction> {

    public static final String CRAFT_CREATIVE_KEY = "craft_creative_key";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_CREATIVE;
    }

    @Override
    public ActionResponse handle(CraftCreativeAction action, Player player, ItemStackRequestContext context) {
        if (!player.isCreative()) {
            return context.error();
        }

        // creativeItemNetworkId is 1-based; the catalog is indexed from 0. Use the
        // player's actual game version so the right catalog is queried for NetEase
        // and older clients.
        Item item = Item.getCreativeItem(player.getGameVersion(), action.getCreativeItemNetworkId() - 1);
        if (item == null || item.isNull()) {
            return context.error();
        }

        item = item.clone();
        int requestedCount = action.getNumberOfRequestedCrafts() <= 0
                ? item.getMaxStackSize()
                : action.getNumberOfRequestedCrafts();
        item.setCount(Math.min(item.getMaxStackSize(), requestedCount));
        item.autoAssignStackNetworkId();

        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, item, false);
        context.put(CRAFT_CREATIVE_KEY, true);
        return null;
    }
}
