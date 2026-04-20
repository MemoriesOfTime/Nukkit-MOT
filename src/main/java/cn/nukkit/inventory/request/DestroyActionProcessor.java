package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.BeaconInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.DestroyAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.List;

public class DestroyActionProcessor implements ItemStackRequestActionProcessor<DestroyAction> {

    public static final String NO_RESPONSE_DESTROY_KEY = "noResponseForDestroyAction";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.DESTROY;
    }

    @Override
    public ActionResponse handle(DestroyAction action, Player player, ItemStackRequestContext context) {
        Boolean suppress = context.get(NO_RESPONSE_DESTROY_KEY);
        if (suppress != null && suppress) {
            return null;
        }

        ItemStackRequestSlotData src = action.getSource();
        Inventory inventory = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        if (inventory == null) {
            return context.error();
        }
        if (!player.isCreative() && !(inventory instanceof BeaconInventory)) {
            return context.error();
        }

        int slot = NetworkMapping.toInternalSlot(src.getContainer(), src.getSlot());
        int count = action.getCount();
        if (count <= 0) {
            return context.error();
        }

        Item item = inventory.getItem(slot);
        if (item.isNull() || item.getCount() < count) {
            return context.error();
        }
        if (validateStackNetworkId(item.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }

        if (item.getCount() == count) {
            inventory.clear(slot, false);
        } else {
            Item remaining = item.clone();
            remaining.setCount(item.getCount() - count);
            inventory.setItem(slot, remaining, false);
        }

        ItemStackResponseContainer container = TransferItemActionProcessor.buildContainer(inventory, slot, src);
        return context.success(List.of(container));
    }
}
