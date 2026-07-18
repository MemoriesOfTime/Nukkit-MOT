package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceAction;

public class PlaceActionProcessor extends TransferItemActionProcessor<PlaceAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.PLACE;
    }

    @Override
    public ActionResponse handle(PlaceAction action, Player player, ItemStackRequestContext context) {
        return doTransfer(action, player, context);
    }
}
