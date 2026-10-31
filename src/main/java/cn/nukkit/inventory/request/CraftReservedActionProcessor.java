package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.CraftReservedAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;

/**
 * v1_26_60 唱片压制占位动作：MOT 未实现 disc press，返回 null 静默跳过。
 * <p>
 * v1_26_60 disc-press placeholder action: MOT does not implement disc press, return null to skip silently.
 */
public class CraftReservedActionProcessor implements ItemStackRequestActionProcessor<CraftReservedAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.CRAFT_RESERVED;
    }

    @Override
    public ActionResponse handle(CraftReservedAction action, Player player, ItemStackRequestContext context) {
        return null;
    }
}
