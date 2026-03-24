package cn.nukkit.event.player;

import cn.nukkit.Player;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.item.Item;

public class PlayerOffhandInventoryChangeEvent extends PlayerEvent implements Cancellable {
    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final Item oldItem;
    private Item newItem;

    public PlayerOffhandInventoryChangeEvent(Player player, Item oldItem, Item newItem) {
        this.player = player;
        this.oldItem = oldItem;
        this.newItem = newItem;
    }

    public Item getOldItem() {
        return oldItem;
    }

    public Item getNewItem() {
        return newItem;
    }

    public void setNewItem(Item newItem) {
        this.newItem = newItem;
    }

}
