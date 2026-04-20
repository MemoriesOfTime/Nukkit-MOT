package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.player.PlayerDropItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.DropAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;

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

        Item item = inventory.getItem(slot);
        if (item.isNull() || item.getCount() < count) {
            return context.error();
        }
        if (validateStackNetworkId(item.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }

        Item dropItem = item.clone();
        dropItem.setCount(count);

        PlayerDropItemEvent event = new PlayerDropItemEvent(player, dropItem);
        player.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return context.error();
        }

        if (item.getCount() == count) {
            inventory.clear(slot, false);
        } else {
            Item remaining = item.clone();
            remaining.setCount(item.getCount() - count);
            inventory.setItem(slot, remaining, false);
        }

        player.dropItem(dropItem);

        ItemStackResponseContainer container = TransferItemActionProcessor.buildContainer(inventory, slot, src);
        return context.success(List.of(container));
    }
}
