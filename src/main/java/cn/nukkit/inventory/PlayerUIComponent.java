package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;

import java.util.*;

public class PlayerUIComponent extends BaseInventory {

    public static final int CREATED_ITEM_OUTPUT_UI_SLOT = 50;

    private final PlayerUIInventory playerUI;
    private final int offset;
    private final int size;

    PlayerUIComponent(PlayerUIInventory playerUI, int offset, int size) {
        super(playerUI.holder, InventoryType.UI, Collections.emptyMap(), size);
        this.playerUI = playerUI;
        this.offset = offset;
        this.size = size;
    }

    @Override
    public int getSize() {
        return size;
    }

    @Override
    public int getMaxStackSize() {
        return 64;
    }

    @Override
    public void setMaxStackSize(int size) {
        throw new UnsupportedOperationException();
    }


    @Override
    public String getTitle() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Item getItem(int index) {
        return this.playerUI.getItem(index + this.offset);
    }

    @Override
    public Item getItemFast(int index) {
        return this.playerUI.getItemFast(index + this.offset);
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        //return this.playerUI.setItem(index + this.offset, item, send);
        Item before = this.playerUI.getItem(index + this.offset);
        if (this.playerUI.setItem(index + this.offset, item, send)) {
            onSlotChange(index, before, false);
            return true;
        }
        return false;
    }

    @Override
    public boolean clear(int index, boolean send) {
        Item before = playerUI.getItem(index + this.offset);
        if (this.playerUI.clear(index + this.offset, send)) {
            onSlotChange(index, before, false);
            return true;
        }
        return false;
    }

    @Override
    public Map<Integer, Item> getContents() {
        // Return contents with local indices (0 to size-1) instead of playerUI indices
        // This ensures consistency with getItem(), setItem(), clear(), etc.
        Map<Integer, Item> contents = new HashMap<>();
        for (int i = 0; i < this.size; i++) {
            Item item = this.getItemFast(i);
            if (item != null && !item.isNull()) {
                contents.put(i, item);
            }
        }
        return contents;
    }

    @Override
    public void sendContents(Player... players) {
        this.playerUI.sendContents(players);
    }

    @Override
    public void sendSlot(int index, Player player) {
        this.playerUI.sendSlot(index + this.offset, player);
    }

    @Override
    public void sendSlot(int index, Player... players) {
        this.playerUI.sendSlot(index + this.offset, players);
    }

    @Override
    public void sendSlot(int index, Collection<Player> players) {
        this.playerUI.sendSlot(index + this.offset, players);
    }

    @Override
    public Set<Player> getViewers() {
        return this.playerUI.getViewers();
    }

    @Override
    public InventoryType getType() {
        return this.playerUI.type;
    }

    @Override
    public void onOpen(Player who) {

    }

    @Override
    public boolean open(Player who) {
        return false;
    }

    @Override
    public void close(Player who) {

    }

    @Override
    public void onClose(Player who) {

    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        if (send) {
            this.playerUI.onSlotChange(index + this.offset, before, true);
        }
        super.onSlotChange(index, before, false);
    }
}
