package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntityChiseledBookshelf;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class ChiseledBookshelfInventory extends BaseInventory {

    public ChiseledBookshelfInventory(BlockEntityChiseledBookshelf holder) {
        super(holder, InventoryType.CHEST, new HashMap<>(), BlockEntityChiseledBookshelf.SIZE, "Chiseled Bookshelf");
        this.maxStackSize = 1;
    }

    @Override
    public BlockEntityChiseledBookshelf getHolder() {
        return (BlockEntityChiseledBookshelf) super.getHolder();
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (index < 0 || index >= this.size || !this.allowedToAdd(item)) {
            return false;
        }

        Item old = this.getItem(index);
        Item stored = normalizeBook(item);
        if (stored.isNull()) {
            this.slots.remove(index);
        } else {
            this.slots.put(index, stored);
        }

        this.onSlotChange(index, old, send);
        return true;
    }

    public void loadItem(int index, Item item) {
        if (index < 0 || index >= this.size || !this.allowedToAdd(item)) {
            return;
        }

        Item stored = normalizeBook(item);
        if (stored.isNull()) {
            this.slots.remove(index);
        } else {
            this.slots.put(index, stored);
        }
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
    public boolean allowedToAdd(Item item) {
        return item == null || item.isNull() || isBookshelfBook(item);
    }

    @Override
    public boolean canAddItem(Item item) {
        if (item == null || item.isNull()) {
            return true;
        }
        if (!this.allowedToAdd(item)) {
            return false;
        }

        int count = item.getCount();
        for (int i = 0; i < this.getSize(); i++) {
            if (this.getItemFast(i).isNull()) {
                count--;
                if (count <= 0) {
                    return true;
                }
            }
        }
        return false;
    }

    @Override
    public Item[] addItem(Item... slots) {
        List<Item> remaining = new ArrayList<>();
        for (Item source : slots) {
            if (source == null || source.isNull()) {
                continue;
            }
            if (!this.allowedToAdd(source)) {
                remaining.add(source.clone());
                continue;
            }

            Item item = source.clone();
            for (int i = 0; i < this.getSize() && item.getCount() > 0; i++) {
                if (!this.getItemFast(i).isNull()) {
                    continue;
                }

                Item stored = item.clone();
                stored.setCount(1);
                this.setItem(i, stored);
                item.setCount(item.getCount() - 1);
            }

            if (item.getCount() > 0) {
                remaining.add(item);
            }
        }

        return remaining.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public void setMaxStackSize(int maxStackSize) {
        this.maxStackSize = 1;
    }

    @Override
    public boolean isFull() {
        return this.slots.size() >= this.getSize();
    }

    @Override
    public boolean open(Player who) {
        return false;
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        super.onSlotChange(index, before, send);
        this.getHolder().onInventorySlotChanged(index);
    }

    private static Item normalizeBook(Item item) {
        if (item == null || item.isNull()) {
            return new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        }

        Item stored = item.clone();
        stored.setCount(1);
        return stored;
    }

    private static boolean isBookshelfBook(Item item) {
        return switch (item.getId()) {
            case Item.BOOK, Item.BOOK_AND_QUILL, Item.WRITTEN_BOOK, Item.ENCHANTED_BOOK -> true;
            default -> false;
        };
    }
}
