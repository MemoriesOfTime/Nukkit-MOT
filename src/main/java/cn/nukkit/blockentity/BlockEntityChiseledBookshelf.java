package cn.nukkit.blockentity;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockChiseledBookshelf;
import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.ChiseledBookshelfInventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

public class BlockEntityChiseledBookshelf extends BlockEntitySpawnable implements InventoryHolder, BlockEntityContainer {

    public static final int SIZE = 6;
    public static final String LAST_INTERACTED_SLOT = "LastInteractedSlot";

    private ChiseledBookshelfInventory inventory;
    private int lastInteractedSlot;

    public BlockEntityChiseledBookshelf(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        this.inventory = new ChiseledBookshelfInventory(this);
        this.lastInteractedSlot = -1;

        if (this.namedTag.containsInt(LAST_INTERACTED_SLOT)) {
            this.lastInteractedSlot = this.namedTag.getInt(LAST_INTERACTED_SLOT);
        }

        if (this.namedTag.containsList("Items")) {
            ListTag<CompoundTag> list = this.namedTag.getList("Items", CompoundTag.class);
            int index = 0;
            for (CompoundTag compound : list.getAll()) {
                int slot = compound.containsByte("Slot") ? compound.getByte("Slot") : index;
                index++;
                if (slot < 0 || slot >= SIZE) {
                    continue;
                }
                this.inventory.loadItem(slot, NBTIO.getItemHelper(compound));
            }
        }

        super.initBlockEntity();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevelBlock().getId() == BlockID.CHISELED_BOOKSHELF;
    }

    public boolean hasBook(int slot) {
        checkSlot(slot);
        return !this.inventory.getItem(slot).isNull();
    }

    public Item getBook(int slot) {
        checkSlot(slot);
        return this.inventory.getItem(slot);
    }

    public void setBook(int slot, Item item) {
        checkSlot(slot);
        this.inventory.setItem(slot, item);
    }

    public Item removeBook(int slot) {
        checkSlot(slot);
        Item removed = this.getBook(slot);
        this.inventory.clear(slot);
        return removed;
    }

    public int getBooksStoredBit() {
        int bits = 0;
        for (int i = 0; i < SIZE; i++) {
            if (this.hasBook(i)) {
                bits |= 1 << i;
            }
        }
        return bits;
    }

    public int getComparatorOutput() {
        return this.lastInteractedSlot >= 0 ? this.lastInteractedSlot + 1 : 0;
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = new CompoundTag()
                .putString("id", BlockEntity.CHISELED_BOOKSHELF)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putBoolean("isMovable", this.movable);
        this.writeBookshelfNbt(tag);
        return tag;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.writeBookshelfNbt(this.namedTag);
    }

    @Override
    public void setDirty() {
        this.spawnToAll();
        super.setDirty();
    }

    @Override
    public void onBreak() {
        for (Item content : this.inventory.getContents().values()) {
            this.level.dropItem(this.add(0.5, 0.5, 0.5), content);
        }
        this.inventory.clearAll();
    }

    @Override
    public ChiseledBookshelfInventory getInventory() {
        return this.inventory;
    }

    @Override
    public int getSize() {
        return SIZE;
    }

    @Override
    public Item getItem(int index) {
        if (index < 0 || index >= SIZE) {
            return Item.AIR_ITEM.clone();
        }
        return this.inventory.getItem(index);
    }

    @Override
    public void setItem(int index, Item item) {
        if (index < 0 || index >= SIZE) {
            return;
        }
        this.inventory.setItem(index, item);
    }

    public void onInventorySlotChanged(int slot) {
        checkSlot(slot);
        this.lastInteractedSlot = slot;
        this.refreshBooksStoredBlockState();
        this.setDirty();
        if (this.level != null) {
            this.level.updateComparatorOutputLevel(this);
        }
    }

    private void writeBookshelfNbt(CompoundTag tag) {
        if (this.lastInteractedSlot >= 0) {
            tag.putInt(LAST_INTERACTED_SLOT, this.lastInteractedSlot);
        } else {
            tag.remove(LAST_INTERACTED_SLOT);
        }

        ListTag<CompoundTag> itemList = new ListTag<>("Items");
        for (int i = 0; i < SIZE; i++) {
            if (this.hasBook(i)) {
                itemList.add(NBTIO.putItemHelper(this.inventory.getItem(i), i));
            }
        }
        tag.putList(itemList);
    }

    private void refreshBooksStoredBlockState() {
        if (this.level == null) {
            return;
        }

        Block block = this.getLevelBlock();
        if (block instanceof BlockChiseledBookshelf bookshelf) {
            int booksStored = this.getBooksStoredBit();
            if (bookshelf.getBooksStoredBit() != booksStored) {
                bookshelf.setBooksStoredBit(booksStored);
                this.level.setBlock(this, bookshelf, true, true);
            }
        }
    }

    private static void checkSlot(int slot) {
        if (slot < 0 || slot >= SIZE) {
            throw new IllegalArgumentException("Chiseled bookshelf slot out of range: " + slot);
        }
    }
}
