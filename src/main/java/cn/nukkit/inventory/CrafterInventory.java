package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntityCrafter;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;

import java.util.ArrayList;
import java.util.List;

public class CrafterInventory extends ContainerInventory {

    public CrafterInventory(BlockEntityCrafter crafter) {
        super(crafter, InventoryType.CRAFTER);
    }

    @Override
    public BlockEntityCrafter getHolder() {
        return (BlockEntityCrafter) super.getHolder();
    }

    public boolean isSlotDisabled(int slot) {
        return slot >= 0 && slot < this.getSize() && (this.getHolder().getDisabledSlots() & (1 << slot)) != 0;
    }

    public boolean setSlotDisabled(int slot, boolean disabled) {
        if (slot < 0 || slot >= this.getSize()) {
            return false;
        }
        if (disabled && !this.getItem(slot).isNull()) {
            return false;
        }

        int oldMask = this.getHolder().getDisabledSlots();
        int newMask = disabled ? oldMask | (1 << slot) : oldMask & ~(1 << slot);
        if (newMask == oldMask) {
            return true;
        }

        this.getHolder().setDisabledSlots(newMask);
        this.sendContents(this.getViewers());
        this.updateComparatorOutputLevel();
        return true;
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (this.isSlotDisabled(index) && item != null && !item.isNull()) {
            return false;
        }
        if (item != null && item.isNull()) {
            return this.clear(index, send);
        }
        int oldOutput = this.getComparatorOutput();
        boolean changed = super.setItem(index, item, send);
        if (changed) {
            this.updateComparatorOutputLevelIfChanged(oldOutput);
        }
        return changed;
    }

    @Override
    public boolean clear(int index, boolean send) {
        int oldOutput = this.getComparatorOutput();
        boolean changed = super.clear(index, send);
        if (changed) {
            this.updateComparatorOutputLevelIfChanged(oldOutput);
        }
        return changed;
    }

    @Override
    public boolean canAddItem(Item item) {
        Item remaining = item.clone();
        boolean checkDamage = remaining.hasMeta();
        boolean checkTag = remaining.getCompoundTag() != null;

        for (int i = 0; i < this.getSize(); i++) {
            if (this.isSlotDisabled(i)) {
                continue;
            }
            Item slot = this.getItem(i);
            if (remaining.equals(slot, checkDamage, checkTag)) {
                remaining.setCount(remaining.getCount() - Math.max(0, Math.min(slot.getMaxStackSize(), this.getMaxStackSize()) - slot.getCount()));
            } else if (slot.isNull()) {
                remaining.setCount(remaining.getCount() - Math.min(remaining.getMaxStackSize(), this.getMaxStackSize()));
            }

            if (remaining.getCount() <= 0) {
                return true;
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

            Item item = source.clone();
            boolean checkDamage = item.hasMeta();
            boolean checkTag = item.getCompoundTag() != null;

            for (int i = 0; i < this.getSize() && item.getCount() > 0; i++) {
                if (this.isSlotDisabled(i)) {
                    continue;
                }
                Item current = this.getItem(i);
                if (!item.equals(current, checkDamage, checkTag)) {
                    continue;
                }

                int maxStackSize = Math.min(current.getMaxStackSize(), this.getMaxStackSize());
                int amount = Math.min(maxStackSize - current.getCount(), item.getCount());
                if (amount <= 0) {
                    continue;
                }
                current.setCount(current.getCount() + amount);
                item.setCount(item.getCount() - amount);
                this.setItem(i, current);
            }

            for (int i = 0; i < this.getSize() && item.getCount() > 0; i++) {
                if (this.isSlotDisabled(i) || !this.getItem(i).isNull()) {
                    continue;
                }

                int amount = Math.min(Math.min(item.getMaxStackSize(), this.getMaxStackSize()), item.getCount());
                Item add = item.clone();
                add.setCount(amount);
                item.setCount(item.getCount() - amount);
                this.setItem(i, add);
            }

            if (item.getCount() > 0) {
                remaining.add(item);
            }
        }

        return remaining.toArray(Item.EMPTY_ARRAY);
    }

    public int getComparatorOutput() {
        int used = 0;
        for (int i = 0; i < this.getSize(); i++) {
            if (this.isSlotDisabled(i) || !this.getItem(i).isNull()) {
                used++;
            }
        }
        return used;
    }

    public void sendContents(Iterable<Player> players) {
        for (Player player : players) {
            this.sendContents(player);
        }
    }

    private void updateComparatorOutputLevelIfChanged(int oldOutput) {
        if (this.getComparatorOutput() != oldOutput) {
            this.updateComparatorOutputLevel();
        }
    }

    private void updateComparatorOutputLevel() {
        Level level = this.getHolder().getLevel();
        if (level != null) {
            level.updateComparatorOutputLevel(this.getHolder());
        }
    }
}
