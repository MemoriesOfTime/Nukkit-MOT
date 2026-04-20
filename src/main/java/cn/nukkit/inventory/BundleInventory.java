package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Dynamic container backing inventory for bundle items.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public class BundleInventory extends BaseInventory {

    public static final int MAX_FILL = 64;
    public static final int DYNAMIC_REGISTRY_WINDOW_ID = 125;

    public BundleInventory(ItemBundle holder) {
        super(holder, InventoryType.CHEST, Map.of(), MAX_FILL, "Bundle");
        loadFromHolder(holder);
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        int newWeight = getWeight() - getWeight(this.getItemFast(index)) + getWeight(item);
        if (newWeight > MAX_FILL) {
            return false;
        }

        boolean changed = super.setItem(index, item, send);
        if (changed) {
            getHolder().saveNBT();
        }
        return changed;
    }

    @Override
    public boolean clear(int index, boolean send) {
        boolean changed = super.clear(index, send);
        if (changed) {
            getHolder().saveNBT();
        }
        return changed;
    }

    @Override
    public void sendContents(Player... players) {
        InventoryContentPacket pk = new InventoryContentPacket();
        pk.slots = new Item[this.getSize()];
        for (int i = 0; i < this.getSize(); ++i) {
            pk.slots[i] = this.getItem(i);
        }
        pk.containerNameData = new FullContainerName(ContainerSlotType.DYNAMIC_CONTAINER, getHolder().getBundleId());
        pk.dynamicContainerSize = this.getSize();
        pk.storageItem = getHolder().clone();

        for (Player player : players) {
            if (!player.spawned || player.protocol < ProtocolInfo.v1_21_20) {
                continue;
            }
            pk.inventoryId = DYNAMIC_REGISTRY_WINDOW_ID;
            player.dataPacket(pk);
        }
    }

    @Override
    public void sendSlot(int index, Player... players) {
        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = this.getItem(index);
        pk.containerNameData = new FullContainerName(ContainerSlotType.DYNAMIC_CONTAINER, getHolder().getBundleId());
        pk.dynamicContainerSize = this.getSize();
        pk.storageItem = getHolder().clone();

        for (Player player : players) {
            if (!player.spawned || player.protocol < ProtocolInfo.v1_21_20) {
                continue;
            }
            pk.inventoryId = DYNAMIC_REGISTRY_WINDOW_ID;
            player.dataPacket(pk);
        }
    }

    public int getWeight() {
        return getWeight(new HashSet<>());
    }

    public ItemBundle getHolder() {
        return (ItemBundle) holder;
    }

    private void loadFromHolder(ItemBundle holder) {
        holder.getBundleId();
        CompoundTag tag = holder.getNamedTag();
        if (tag == null || !tag.containsList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT)) {
            return;
        }

        ListTag<CompoundTag> items = tag.getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class);
        for (CompoundTag itemTag : items.getAll()) {
            int slot = itemTag.getByte("Slot") & 0xFF;
            if (slot < 0 || slot >= this.getSize()) {
                continue;
            }

            Item item = NBTIO.getItemHelper(itemTag);
            if (!item.isNull()) {
                this.slots.put(slot, item);
            }
        }
    }

    private int getWeight(Set<Integer> visitedBundleIds) {
        int weight = 0;
        for (Item item : this.slots.values()) {
            weight += getWeight(item, visitedBundleIds);
        }
        return weight;
    }

    private int getWeight(Item item) {
        return getWeight(item, new HashSet<>());
    }

    private int getWeight(Item item, Set<Integer> visitedBundleIds) {
        if (item == null || item.isNull() || item.getCount() <= 0) {
            return 0;
        }

        if (item instanceof ItemBundle bundle) {
            int bundleId = bundle.getBundleId();
            if (!visitedBundleIds.add(bundleId)) {
                return 0;
            }
            return ((BundleInventory) bundle.getInventory()).getWeight(visitedBundleIds) + 4;
        }

        return Math.max(1, MAX_FILL / Math.max(1, item.getMaxStackSize())) * item.getCount();
    }
}
