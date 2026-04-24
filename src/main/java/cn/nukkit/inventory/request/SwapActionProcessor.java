package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.SwapAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.ArrayList;
import java.util.List;

public class SwapActionProcessor implements ItemStackRequestActionProcessor<SwapAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.SWAP;
    }

    @Override
    public ActionResponse handle(SwapAction action, Player player, ItemStackRequestContext context) {
        ItemStackRequestSlotData src = action.getSource();
        ItemStackRequestSlotData dst = action.getDestination();

        Inventory srcInv = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        Inventory dstInv = NetworkMapping.getInventory(player, dst.getContainer(), dst.getDynamicId());
        if (srcInv == null || dstInv == null) {
            return context.error();
        }

        int srcSlot = NetworkMapping.toInternalSlot(src.getContainer(), src.getSlot());
        int dstSlot = NetworkMapping.toInternalSlot(dst.getContainer(), dst.getSlot());

        Item sourceItem = srcInv.getUnclonedItem(srcSlot);
        Item destItem = dstInv.getUnclonedItem(dstSlot);

        if (validateStackNetworkId(sourceItem.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }
        if (validateStackNetworkId(destItem.getStackNetId(), dst.getStackNetworkId())) {
            return context.error();
        }

        // Fire InventoryClickEvent for both slots before mutation, matching the
        // legacy InventoryTransaction path (one event per swapped slot).
        if (!TransferItemActionProcessor.fireClickEvent(player, srcInv, srcSlot, sourceItem, destItem)) {
            return context.error();
        }
        if (!TransferItemActionProcessor.fireClickEvent(player, dstInv, dstSlot, destItem, sourceItem)) {
            return context.error();
        }

        // 向后兼容：旧路径中每次库存交互都会触发 InventoryTransactionEvent，
        // 但 SAI 路径默认不触发。
        List<InventoryAction> transactionActions = new ArrayList<>();
        transactionActions.add(new SlotChangeAction(srcInv, srcSlot, sourceItem, destItem));
        transactionActions.add(new SlotChangeAction(dstInv, dstSlot, destItem, sourceItem));
        var transaction = new TransferItemActionProcessor.EventOnlyInventoryTransaction(player, transactionActions, context);
        if (!transaction.execute()) {
            return context.error();
        }

        Item originalSource = sourceItem.clone();
        Item originalDest = destItem.clone();

        if (!srcInv.setItem(srcSlot, destItem.clone(), false)) {
            return context.error();
        }
        if (!dstInv.setItem(dstSlot, sourceItem.clone(), false)) {
            srcInv.setItem(srcSlot, originalSource, false);
            return context.error();
        }

        ItemStackResponseContainer srcResp = TransferItemActionProcessor.buildContainer(srcInv, srcSlot, src);
        ItemStackResponseContainer dstResp = TransferItemActionProcessor.buildContainer(dstInv, dstSlot, dst);
        return context.success(List.of(srcResp, dstResp));
    }
}
