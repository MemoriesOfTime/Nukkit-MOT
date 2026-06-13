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
        // 实验台（教育版）合成 action，服务端未实现。返回 null 表示静默跳过：
        // 不产出响应、不判定为错误，避免中断整条 request，
        return null;
    }
}
