package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftNonImplementedAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;

public class CraftNonImplementedActionProcessor implements ItemStackRequestActionProcessor<CraftNonImplementedAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_NON_IMPLEMENTED_DEPRECATED;
    }

    @Override
    public ActionResponse handle(CraftNonImplementedAction action, Player player, ItemStackRequestContext context) {
        return context.error();
    }
}
