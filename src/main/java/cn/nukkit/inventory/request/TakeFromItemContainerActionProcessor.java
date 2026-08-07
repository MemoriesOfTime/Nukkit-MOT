package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeFromItemContainerAction;

public class TakeFromItemContainerActionProcessor extends TransferItemActionProcessor<TakeFromItemContainerAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.TAKE_FROM_ITEM_CONTAINER;
    }

    @Override
    public ActionResponse handle(TakeFromItemContainerAction action, Player player, ItemStackRequestContext context) {
        return doTransfer(action, player, context);
    }
}
