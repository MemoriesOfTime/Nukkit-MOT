package cn.nukkit.blockentity;

import cn.nukkit.block.BlockID;
import cn.nukkit.inventory.CrafterInventory;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class BlockEntityCrafter extends BlockEntitySpawnableContainer implements BlockEntityNameable {

    public static final String DISABLED_SLOTS = "disabled_slots";
    private static final int SLOT_MASK = 0x1ff;

    public BlockEntityCrafter(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        this.inventory = new CrafterInventory(this);
        if (this.namedTag.containsShort("disabledSlots") && !this.namedTag.containsShort(DISABLED_SLOTS)) {
            this.namedTag.putShort(DISABLED_SLOTS, this.namedTag.getShort("disabledSlots"));
        }
        if (!this.namedTag.containsShort(DISABLED_SLOTS)) {
            this.namedTag.putShort(DISABLED_SLOTS, 0);
        }
        super.initBlockEntity();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.getLevelBlock().getId() == BlockID.CRAFTER;
    }

    @Override
    public int getSize() {
        return 9;
    }

    @Override
    public CrafterInventory getInventory() {
        return (CrafterInventory) this.inventory;
    }

    public int getDisabledSlots() {
        return this.namedTag.getShort(DISABLED_SLOTS) & SLOT_MASK;
    }

    public void setDisabledSlots(int disabledSlots) {
        this.namedTag.putShort(DISABLED_SLOTS, disabledSlots & SLOT_MASK);
        this.setDirty();
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag tag = new CompoundTag()
                .putString("id", BlockEntity.CRAFTER)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z)
                .putBoolean("isMovable", this.movable)
                .putInt("crafting_ticks_remaining", 0)
                .putShort(DISABLED_SLOTS, this.getDisabledSlots());

        if (this.hasName()) {
            tag.putString("CustomName", this.getName());
        }
        return tag;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putShort(DISABLED_SLOTS, this.getDisabledSlots());
    }

    @Override
    public void setDirty() {
        this.spawnToAll();
        super.setDirty();
    }

    @Override
    public String getName() {
        return this.hasName() ? this.namedTag.getString("CustomName") : "Crafter";
    }

    @Override
    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            this.namedTag.remove("CustomName");
        } else {
            this.namedTag.putString("CustomName", name);
        }
    }

    @Override
    public boolean hasName() {
        return this.namedTag.containsString("CustomName");
    }
}
