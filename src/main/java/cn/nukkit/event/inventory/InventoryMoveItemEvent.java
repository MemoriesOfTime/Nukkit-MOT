package cn.nukkit.event.inventory;

import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;

/**
 * @author CreeperFace
 * <p>
 * Called when inventory transaction is not caused by a player
 */
public class InventoryMoveItemEvent extends InventoryEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    public static HandlerList getHandlers() {
        return handlers;
    }

    private final Inventory targetInventory;
    private final InventoryHolder source;

    private Item item;

    private final Action action;

    private final EntityItem pickedEntity;

    public InventoryMoveItemEvent(Inventory from, Inventory targetInventory, InventoryHolder source, Item item, Action action) {
        this(from, targetInventory, source, item, action, null);
    }

    /**
     * @param pickedEntity the item entity a {@link Action#PICKUP} takes from the ground, or null
     *                     when the move does not come from an entity
     */
    public InventoryMoveItemEvent(Inventory from, Inventory targetInventory, InventoryHolder source, Item item, Action action, EntityItem pickedEntity) {
        super(from);
        this.targetInventory = targetInventory;
        this.source = source;
        this.item = item;
        this.action = action;
        this.pickedEntity = pickedEntity;
    }

    public Inventory getTargetInventory() {
        return targetInventory;
    }

    public InventoryHolder getSource() {
        return source;
    }

    public Item getItem() {
        return item;
    }

    public void setItem(Item item) {
        this.item = item;
    }

    public Action getAction() {
        return action;
    }

    /**
     * The item entity a hopper or a hopper minecart is about to pick up from the ground.
     *
     * <p>A pickup has no source inventory, and {@link #getItem()} is only the entity's item, so
     * the tags saved on the entity itself were unreachable before. Null for every other move.
     */
    public EntityItem getPickedEntity() {
        return pickedEntity;
    }

    public enum Action {
        SLOT_CHANGE, //transaction between 2 inventories
        PICKUP,
        DROP,
        DISPENSE
    }
}
