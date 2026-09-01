package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.BeaconInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.DestroyAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.ArrayList;
import java.util.List;

public class DestroyActionProcessor implements ItemStackRequestActionProcessor<DestroyAction> {

    public static final String NO_RESPONSE_DESTROY_KEY = "noResponseForDestroyAction";

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.DESTROY;
    }

    @Override
    public ActionResponse handle(DestroyAction action, Player player, ItemStackRequestContext context) {
        boolean suppressResponse = Boolean.TRUE.equals(context.get(NO_RESPONSE_DESTROY_KEY));

        ItemStackRequestSlotData src = action.getSource();
        Inventory inventory = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        if (inventory == null) {
            return context.error();
        }
        // suppressResponse 表示当前 destroy 紧随 CraftResultsDeprecated —— 这是
        // 协议要求的合成尾声清理，必须严格限定在 CREATED_OUTPUT 槽，否则恶意
        // 客户端可借此绕过生存模式权限销毁背包物品。
        // BeaconInventory 始终豁免（信标 payment 槽的标准消耗路径）。
        boolean isCreatedOutputCleanup = suppressResponse
                && src.getContainer() == ContainerSlotType.CREATED_OUTPUT;
        if (!isCreatedOutputCleanup && !player.isCreative() && !(inventory instanceof BeaconInventory)) {
            return context.error();
        }

        int slot = NetworkMapping.toInternalSlot(src.getContainer(), src.getSlot());
        int count = action.getCount();
        if (count <= 0) {
            return context.error();
        }

        Item item = inventory.getUnclonedItem(slot);
        if (item.isNull() || item.getCount() < count) {
            return context.error();
        }
        if (validateStackNetworkId(item.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }

        Item targetItem;
        if (item.getCount() == count) {
            targetItem = Item.get(Item.AIR);
        } else {
            targetItem = item.clone();
            targetItem.setCount(item.getCount() - count);
        }

        // 向后兼容：旧路径中每次库存交互都会触发 InventoryTransactionEvent
        List<InventoryAction> transactionActions = new ArrayList<>();
        transactionActions.add(new SlotChangeAction(inventory, slot, item, targetItem));
        var transaction = new TransferItemActionProcessor.EventOnlyInventoryTransaction(player, transactionActions, context);
        if (!transaction.execute()) {
            return context.error();
        }

        if (item.getCount() == count) {
            if (!inventory.clear(slot, false)) {
                return context.error();
            }
        } else {
            if (!inventory.setItem(slot, targetItem, false)) {
                return context.error();
            }
        }

        ItemStackResponseContainer container = TransferItemActionProcessor.buildContainer(inventory, slot, src);
        if (suppressResponse) {
            return null;
        }
        return context.success(List.of(container));
    }
}
