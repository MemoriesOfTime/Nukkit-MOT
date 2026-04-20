package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftCreativeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;

import java.util.List;

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
        item.setCount(item.getMaxStackSize());
        item.autoAssignStackNetworkId();

        player.getUIInventory().setItem(PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT, item, false);
        context.put(CRAFT_CREATIVE_KEY, true);

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                0, 0, item.getCount(), item.getStackNetId(),
                item.hasCustomName() ? item.getCustomName() : "",
                item.getDamage(), ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.CREATED_OUTPUT,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.CREATED_OUTPUT, null)
        )));
    }
}
