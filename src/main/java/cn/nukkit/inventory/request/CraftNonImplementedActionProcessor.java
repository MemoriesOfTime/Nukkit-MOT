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
        // 协议占位/no-op action（老协议下还会承接映射歧义的若干真实 action）。
        // 返回 null 表示静默跳过：不产出响应、不判定为错误，避免中断整条 request，
        return null;
    }
}
