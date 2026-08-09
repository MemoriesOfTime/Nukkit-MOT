package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.event.player.PlayerTransferItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.DropAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

import java.util.ArrayList;
import java.util.List;

public class DropActionProcessor implements ItemStackRequestActionProcessor<DropAction> {

    @Override
    public ItemStackRequestActionType getType() {
        return ItemStackRequestActionType.DROP;
    }

    @Override
    public ActionResponse handle(DropAction action, Player player, ItemStackRequestContext context) {
        ItemStackRequestSlotData src = action.getSource();
        Inventory inventory = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        if (inventory == null) {
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

        Item dropItem = item.clone();
        dropItem.setCount(count);

        if (!TransferItemActionProcessor.fireTransferEvent(player, PlayerTransferItemEvent.Type.DROP,
                inventory, slot, null, -1, item, null, count)) {
            return context.error();
        }

        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropItem);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        // 向后兼容：旧路径中每次库存交互都会触发 InventoryTransactionEvent，
        // 但 SAI 路径默认不触发。
        List<InventoryAction> transactionActions = new ArrayList<>();
        Item targetItem;
        if (item.getCount() == count) {
            targetItem = Item.get(Item.AIR);
        } else {
            targetItem = item.clone();
            targetItem.setCount(item.getCount() - count);
        }
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
            Item remaining = item.clone();
            remaining.setCount(item.getCount() - count);
            if (!inventory.setItem(slot, remaining, false)) {
                return context.error();
            }
        }

        Item committedDrop = event.getItem().clone();
        context.onCommit(() -> player.dropItem(committedDrop));

        ItemStackResponseContainer container = TransferItemActionProcessor.buildContainer(inventory, slot, src);
        return context.success(List.of(container));
    }
}
