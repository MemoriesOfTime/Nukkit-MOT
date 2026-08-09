package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TakeAction;

public class TakeActionProcessor extends TransferItemActionProcessor<TakeAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.TAKE;
    }

    @Override
    public ActionResponse handle(TakeAction action, Player player, ItemStackRequestContext context) {
        return doTransfer(action, player, context);
    }
}
