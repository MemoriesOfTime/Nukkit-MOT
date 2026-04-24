package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ConsumeAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.List;

public class ConsumeActionProcessor implements ItemStackRequestActionProcessor<ConsumeAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CONSUME;
    }

    @Override
    public ActionResponse handle(ConsumeAction action, Player player, ItemStackRequestContext context) {
        ItemStackRequestSlotData src = action.getSource();
        Inventory inventory = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        if (inventory == null) {
            return context.error();
        }

        int slot = NetworkMapping.toInternalSlot(src.getContainer(), src.getSlot());
        int count = action.getCount();
        if (count <= 0) {
            return context.error();
        }

        Item item = inventory.getUnclonedItem(slot);
        if (item.isNull() || item.getCount() < count) {
            return context.error();
        }
        if (validateStackNetworkId(item.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }

        if (item.getCount() == count) {
            if (!inventory.clear(slot, false)) {
                return context.error();
            }
        } else {
            Item remaining = item.clone();
            remaining.setCount(item.getCount() - count);
            if (!inventory.setItem(slot, remaining, false)) {
                return context.error();
            }
        }

        ItemStackResponseContainer container = TransferItemActionProcessor.buildContainer(inventory, slot, src);
        return context.success(List.of(container));
    }
}
