package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.BlockShelf;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.ShelfInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;

import java.util.HashSet;

public class BlockEntityShelf extends BlockEntitySpawnable implements InventoryHolder, BlockEntityContainer {

    public static final int SIZE = 3;

    private ShelfInventory inventory;

    public BlockEntityShelf(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        this.inventory = new ShelfInventory(this);

        if (this.namedTag.contains("Items") && this.namedTag.get("Items") instanceof ListTag) {
            ListTag<CompoundTag> list = this.namedTag.getList("Items", CompoundTag.class);
            for (CompoundTag compound : list.getAll()) {
                Item item = NBTIO.getItemHelper(compound);
                this.inventory.slots.put(compound.getByte("Slot"), item);
            }
        }

        super.initBlockEntity();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevelBlock() instanceof BlockShelf;
    }

    @Override
    public String getName() {
        return "Shelf";
    }

    @Override
    public ShelfInventory getInventory() {
        return this.inventory;
    }

    @Override
    public void close() {
        if (!closed) {
            for (Player player : new HashSet<>(this.inventory.getViewers())) {
                player.removeWindow(this.inventory);
            }
            super.close();
        }
    }

    @Override
    public void onBreak() {
        for (Item content : this.inventory.getContents().values()) {
            this.level.dropItem(this, content);
        }
        this.inventory.clearAll();
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
        this.setDirty();
        if (this.level != null) {
            this.level.updateComparatorOutputLevel(this);
        }
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = new CompoundTag()
                .putString("id", BlockEntity.SHELF)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putBoolean("isMovable", this.movable);

        this.writeItems(tag);
        return tag;
    }

    private void writeItems(CompoundTag tag) {
        ListTag<CompoundTag> items = new ListTag<>("Items");
        for (int i = 0; i < SIZE; i++) {
            items.add(NBTIO.putItemHelper(this.inventory.getItem(i), i));
        }
        tag.putList(items);
    }

    @Override
    public void saveNBT() {
        this.namedTag.putString("id", BlockEntity.SHELF)
                .putInt("x", (int) this.getX())
                .putInt("y", (int) this.getY())
                .putInt("z", (int) this.getZ())
                .putBoolean("isMovable", this.movable);
        this.writeItems(this.namedTag);
    }

    @Override
    public void setDirty() {
        this.spawnToAll();
        super.setDirty();
    }
}
