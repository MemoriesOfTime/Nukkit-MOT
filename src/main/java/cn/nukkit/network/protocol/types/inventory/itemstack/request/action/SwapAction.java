package cn.nukkit.network.protocol.types.inventory.itemstack.request.action;

import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import lombok.Value;

/**
 * SwapStackRequestActionData is sent by the client to swap the item in its cursor with an item present in another
 * container. The two item stacks swap places.
 */
@Value
public class SwapAction implements ItemStackRequestAction {
    ItemStackRequestSlotData source;
    ItemStackRequestSlotData destination;

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.SWAP;
    }
}
