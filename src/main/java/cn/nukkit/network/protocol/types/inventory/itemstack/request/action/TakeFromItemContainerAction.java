package cn.nukkit.network.protocol.types.inventory.itemstack.request.action;

import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import lombok.Value;

@Value
public class TakeFromItemContainerAction implements TransferItemStackRequestAction {
    int count;
    ItemStackRequestSlotData source;
    ItemStackRequestSlotData destination;

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.TAKE_FROM_ITEM_CONTAINER;
    }
}
