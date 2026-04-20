package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDurable;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.MineBlockAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;

import java.util.List;

public class MineBlockActionProcessor implements ItemStackRequestActionProcessor<MineBlockAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.MINE_BLOCK;
    }

    @Override
    public ActionResponse handle(MineBlockAction action, Player player, ItemStackRequestContext context) {
        PlayerInventory inventory = player.getInventory();
        int heldItemIndex = inventory.getHeldItemIndex();
        if (heldItemIndex != action.getHotbarSlot()) {
            return context.error();
        }

        Item itemInHand = inventory.getItem(heldItemIndex);
        if (validateStackNetworkId(itemInHand.getStackNetId(), action.getStackNetworkId())) {
            return context.error();
        }

        // Reconcile client prediction with server-side durability. If the client
        // predicted a different damage value than what the server holds, force the
        // authoritative item state back to the client so future actions use the
        // correct netId + damage.
        if (!itemInHand.isNull() && itemInHand instanceof ItemDurable && action.getPredictedDurability() != 0) {
            if (itemInHand.getDamage() != action.getPredictedDurability()) {
                InventorySlotPacket packet = new InventorySlotPacket();
                packet.inventoryId = ContainerIds.INVENTORY;
                packet.slot = heldItemIndex;
                packet.item = itemInHand;
                packet.containerNameData = new FullContainerName(ContainerSlotType.HOTBAR, null);
                player.dataPacket(packet);
            }
        }

        ItemStackResponseSlot responseSlot = new ItemStackResponseSlot(
                heldItemIndex, heldItemIndex,
                itemInHand.isNull() ? 0 : itemInHand.getCount(),
                itemInHand.getStackNetId(),
                itemInHand.hasCustomName() ? itemInHand.getCustomName() : "",
                itemInHand.getDamage(),
                ""
        );
        return context.success(List.of(new ItemStackResponseContainer(
                ContainerSlotType.HOTBAR_AND_INVENTORY,
                List.of(responseSlot),
                new FullContainerName(ContainerSlotType.HOTBAR_AND_INVENTORY, null)
        )));
    }
}
