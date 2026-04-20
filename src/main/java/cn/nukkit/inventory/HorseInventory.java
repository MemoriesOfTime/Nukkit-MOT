package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.passive.EntityHorseBase;
import cn.nukkit.entity.passive.EntityLlama;
import cn.nukkit.item.Item;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;

import java.util.Collection;
import java.util.Map;

/**
 * Inventory backing the horse family's equipment and optional chest storage.
 * <p>
 * Slot layout: 0 = saddle, 1 = armor (normal horses), 2..2+chestSize-1 = chest storage.
 * Llama bodies reject armor in slot 1 (carpet slot wiring is left as follow-up).
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class HorseInventory extends BaseInventory {

    public static final int SLOT_SADDLE = 0;
    public static final int SLOT_ARMOR = 1;
    public static final int SLOT_CHEST_BASE = 2;

    private final int chestSize;
    private boolean suppressSaddleSync;

    public HorseInventory(EntityHorseBase holder, int chestSize) {
        super(holder, InventoryType.HORSE, Map.of(), SLOT_CHEST_BASE + Math.max(0, chestSize), "Horse");
        this.chestSize = Math.max(0, chestSize);
    }

    @Override
    public EntityHorseBase getHolder() {
        return (EntityHorseBase) this.holder;
    }

    public int getChestSize() {
        return chestSize;
    }

    public boolean isSaddleSlot(int slot) {
        return slot == SLOT_SADDLE;
    }

    public boolean isArmorSlot(int slot) {
        return slot == SLOT_ARMOR;
    }

    public boolean isChestSlot(int slot) {
        return slot >= SLOT_CHEST_BASE && slot < SLOT_CHEST_BASE + chestSize;
    }

    @Override
    public boolean allowedToAdd(Item item) {
        return true;
    }

    public boolean isValidForSlot(int slot, Item item) {
        if (item == null || item.isNull()) {
            return true;
        }
        if (slot == SLOT_SADDLE) {
            return item.getId() == Item.SADDLE;
        }
        if (slot == SLOT_ARMOR) {
            if (getHolder() instanceof EntityLlama) {
                return false;
            }
            int id = item.getId();
            return id == Item.LEATHER_HORSE_ARMOR
                    || id == Item.IRON_HORSE_ARMOR
                    || id == Item.GOLD_HORSE_ARMOR
                    || id == Item.DIAMOND_HORSE_ARMOR;
        }
        return isChestSlot(slot);
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (!isValidForSlot(index, item)) {
            return false;
        }
        return super.setItem(index, item, send);
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        super.onSlotChange(index, before, send);

        Item now = this.getItem(index);
        if (index == SLOT_SADDLE) {
            syncSaddle(!now.isNull());
        } else if (index == SLOT_ARMOR) {
            broadcastArmorVisual(now);
        }
    }

    private void syncSaddle(boolean saddled) {
        if (suppressSaddleSync) {
            return;
        }
        EntityHorseBase holder = getHolder();
        if (holder == null || holder.closed) {
            return;
        }
        if (holder.isSaddled() != saddled) {
            holder.setSaddled(saddled);
        }
    }

    private void broadcastArmorVisual(Item armor) {
        EntityHorseBase holder = getHolder();
        if (holder == null || holder.closed) {
            return;
        }
        Item body = armor == null || armor.isNull() ? Item.get(Item.AIR) : armor;
        MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
        pk.eid = holder.getId();
        Item air = Item.get(Item.AIR);
        pk.slots = new Item[]{air, body, air, air};

        Collection<Player> viewers = holder.getViewers().values();
        for (Player viewer : viewers) {
            viewer.dataPacket(pk);
        }
    }

    /**
     * Sync the saddle slot from the holder's {@link EntityHorseBase#isSaddled()}
     * without triggering {@link #syncSaddle(boolean)} reentrance. Used when loading
     * NBT state or handling interact-driven saddle changes.
     */
    public void applySaddleWithoutSync(Item saddleItem) {
        boolean previous = suppressSaddleSync;
        suppressSaddleSync = true;
        try {
            super.setItem(SLOT_SADDLE, saddleItem == null ? Item.get(Item.AIR) : saddleItem, false);
        } finally {
            suppressSaddleSync = previous;
        }
    }

    public ListTag<CompoundTag> saveToNBT() {
        ListTag<CompoundTag> list = new ListTag<>();
        for (int slot = 0; slot < this.getSize(); slot++) {
            Item item = this.getItem(slot);
            if (item == null || item.isNull()) {
                continue;
            }
            list.add(NBTIO.putItemHelper(item, slot));
        }
        return list;
    }

    public void loadFromNBT(ListTag<CompoundTag> list) {
        if (list == null) {
            return;
        }
        boolean previous = suppressSaddleSync;
        suppressSaddleSync = true;
        try {
            for (CompoundTag tag : list.getAll()) {
                int slot = tag.contains("Slot") ? (tag.getByte("Slot") & 0xFF) : -1;
                if (slot < 0 || slot >= this.getSize()) {
                    continue;
                }
                Item item = NBTIO.getItemHelper(tag);
                if (item == null || item.isNull()) {
                    continue;
                }
                if (!isValidForSlot(slot, item)) {
                    continue;
                }
                super.setItem(slot, item, false);
            }
        } finally {
            suppressSaddleSync = previous;
        }
    }
}
