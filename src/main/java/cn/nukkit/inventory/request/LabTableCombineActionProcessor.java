package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.LabTableCombineAction;

public class LabTableCombineActionProcessor implements ItemStackRequestActionProcessor<LabTableCombineAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.LAB_TABLE_COMBINE;
    }

    @Override
    public ActionResponse handle(LabTableCombineAction action, Player player, ItemStackRequestContext context) {
        return context.error();
    }
}
