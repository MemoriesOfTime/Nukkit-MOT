package cn.nukkit.event.inventory;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.StonecutterInventory;
import cn.nukkit.item.Item;
import lombok.Getter;

public class StonecutterItemEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Getter
    private final Item inputItem;
    @Getter
    private final Item outputItem;
    @Getter
    private final Player player;

    public StonecutterItemEvent(StonecutterInventory inventory, Item inputItem, Item outputItem, Player player) {
        super(inventory);
        this.inputItem = inputItem;
        this.outputItem = outputItem;
        this.player = player;
    }
}
