package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityShelf;
import cn.nukkit.item.Item;

import java.util.HashMap;

public class ShelfInventory extends BaseInventory {

    public ShelfInventory(BlockEntityShelf holder) {
        super(holder, InventoryType.CHEST, new HashMap<>(), 3, "Shelf");
    }

    @Override
    public boolean open(Player who) {
        return false;
    }

    @Override
    public BlockEntityShelf getHolder() {
        return (BlockEntityShelf) super.getHolder();
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (index < 0 || index >= this.size) {
            return false;
        }
        Item old = this.getItem(index);
        if (item == null || item.isNull()) {
            this.slots.remove(index);
        } else {
            Item stored = item.clone();
            stored.setCount(1);
            this.slots.put(index, stored);
        }
        this.onSlotChange(index, old, send);
        return true;
    }

    @Override
    public boolean clear(int index, boolean send) {
        if (index < 0 || index >= this.size) {
            return false;
        }
        Item old = this.slots.remove(index);
        if (old != null) {
            this.onSlotChange(index, old, send);
        }
        return true;
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        super.onSlotChange(index, before, send);
        this.getHolder().onInventorySlotChanged(index);
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }
}
