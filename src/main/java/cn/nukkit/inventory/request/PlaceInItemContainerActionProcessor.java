package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.PlaceInItemContainerAction;

public class PlaceInItemContainerActionProcessor extends TransferItemActionProcessor<PlaceInItemContainerAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.PLACE_IN_ITEM_CONTAINER;
    }

    @Override
    public ActionResponse handle(PlaceInItemContainerAction action, Player player, ItemStackRequestContext context) {
        return doTransfer(action, player, context);
    }
}
