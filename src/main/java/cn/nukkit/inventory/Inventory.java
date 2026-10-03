package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.ApiStatus;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public interface Inventory {

    int MAX_STACK = 64;

    int getSize();

    int getMaxStackSize();

    void setMaxStackSize(int size);

    String getName();

    String getTitle();

    Item getItem(int index);

    @ApiStatus.Internal
    default Item getUnclonedItem(int index) {
        return getItem(index);
    }

    default Item getItemFast(int index) {
        return getItem(index);
    }

    default boolean setItem(int index, Item item) {
        return setItem(index, item, true);
    }

    boolean setItem(int index, Item item, boolean send);

    /**
     * Unconditionally write an item to the given slot, bypassing all inventory
     * change events (EntityInventoryChangeEvent, EntityArmorChangeEvent, etc.).
     * <p>
     * This is intended solely for server-authoritative rollback/restore
     * operations where the server's snapshot must be restored regardless of
     * plugin event handlers that might veto a normal {@link #setItem} call.
     * Using this method in any other context will silently break plugin
     * integrations that rely on change events.
     * <p>
     * No network packet is sent (equivalent to {@code send = false}). Callers
     * are responsible for ensuring visual synchronisation afterwards, e.g. via
     * {@code sendContents}/{@code sendSlot} or the {@code resyncActor} path in
     * {@link cn.nukkit.inventory.request.ItemStackRequestHandler}.
     *
     * @param index the slot index
     * @param item  the item to write (null or empty item clears the slot)
     */
    @ApiStatus.Internal
    default void setItemForce(int index, Item item) {
        setItem(index, item, false);
    }

    Item[] addItem(Item... slots);

    boolean canAddItem(Item item);

    boolean allowedToAdd(Item item);

    Item[] removeItem(Item... slots);

    Map<Integer, Item> getContents();

    void setContents(Map<Integer, Item> items);

    void sendContents(Player player);

    void sendContents(Player... players);

    void sendContents(Collection<Player> players);

    void sendSlot(int index, Player player);

    void sendSlot(int index, Player... players);

    void sendSlot(int index, Collection<Player> players);

    boolean contains(Item item);

    Map<Integer, Item> all(Item item);

    default int first(Item item) {
        return first(item, false);
    }

    int first(Item item, boolean exact);

    int firstEmpty(Item item);

    void decreaseCount(int slot);
    
    void remove(Item item);

    default boolean clear(int index) {
        return clear(index, true);
    }

    boolean clear(int index, boolean send);

    void clearAll();

    boolean isFull();

    boolean isEmpty();

    Set<Player> getViewers();

    InventoryType getType();

    InventoryHolder getHolder();

    void onOpen(Player who);

    boolean open(Player who);

    void close(Player who);

    void onClose(Player who);

    void onSlotChange(int index, Item before, boolean send);
}
