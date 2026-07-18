package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.level.Sound;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.ProtocolInfo;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ItemBundle extends StringItemBase implements InventoryHolder {

    public static final String TAG_BUNDLE_ID = "bundle_id";
    public static final String TAG_STORAGE_ITEM_COMPONENT_CONTENT = "storage_item_component_content";

    private static final AtomicInteger NEXT_BUNDLE_ID = new AtomicInteger(1);

    private int bundleId;
    private int sourceBundleId;
    private BundleInventory inventory;

    public ItemBundle() {
        super(BUNDLE, "Bundle");
    }

    public ItemBundle(@NotNull String namespaceId, @Nullable String name) {
        super(namespaceId, name);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_40;
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    public int getBundleId() {
        return ensureBundleTag().getInt(TAG_BUNDLE_ID);
    }

    public boolean matchesBundleIdentity(int bundleId) {
        if (bundleId <= 0) {
            return false;
        }
        if (this.bundleId == bundleId || this.sourceBundleId == bundleId) {
            return true;
        }
        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : null;
        return tag != null && tag.contains(TAG_BUNDLE_ID) && tag.getInt(TAG_BUNDLE_ID) == bundleId;
    }

    @Override
    public BundleInventory getInventory() {
        if (this.inventory == null) {
            ensureBundleTag();
            this.inventory = new BundleInventory(this);
        }
        return this.inventory;
    }

    public void saveNBT() {
        CompoundTag tag = ensureBundleTag();
        ListTag<CompoundTag> storedItems = new ListTag<>(TAG_STORAGE_ITEM_COMPONENT_CONTENT);
        for (Map.Entry<Integer, Item> entry : this.getInventory().getContents().entrySet()) {
            Item item = entry.getValue();
            if (item != null && !item.isNull()) {
                storedItems.add(NBTIO.putItemHelper(item, entry.getKey()));
            }
        }
        tag.putList(storedItems);
        this.setNamedTag(tag);
    }

    @Override
    public Item setNamedTag(CompoundTag tag) {
        super.setNamedTag(tag);
        if (tag != null && tag.contains(TAG_BUNDLE_ID)) {
            int tagBundleId = tag.getInt(TAG_BUNDLE_ID);
            NEXT_BUNDLE_ID.updateAndGet(current -> Math.max(current, tagBundleId + 1));
            if (this.bundleId <= 0) {
                this.sourceBundleId = tagBundleId;
                this.bundleId = NEXT_BUNDLE_ID.getAndIncrement();
            }
        }
        return this;
    }

    @Override
    public boolean onClickAir(Player player, Vector3 directionVector) {
        Map.Entry<Integer, Item> entry = this.getInventory().getContents().entrySet().stream()
                .filter(e -> e.getValue() != null && !e.getValue().isNull())
                .min(Map.Entry.comparingByKey())
                .orElse(null);
        if (entry == null) {
            return false;
        }

        Item item = entry.getValue().clone();
        if (!this.getInventory().clear(entry.getKey(), false)) {
            return false;
        }
        if (!player.dropItem(item)) {
            this.getInventory().setItem(entry.getKey(), entry.getValue(), false);
            return false;
        }
        if (player.getLevel() != null) {
            player.getLevel().addSound(player, Sound.BUNDLE_DROP_CONTENTS);
        }
        return true;
    }

    @Override
    public ItemBundle clone() {
        ItemBundle cloned = (ItemBundle) super.clone();
        cloned.inventory = null;
        return cloned;
    }

    public void assignNewBundleId() {
        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : new CompoundTag();
        int oldBundleId = this.bundleId > 0 ? this.bundleId
                : tag.contains(TAG_BUNDLE_ID) ? tag.getInt(TAG_BUNDLE_ID) : this.sourceBundleId;
        this.sourceBundleId = oldBundleId;
        this.bundleId = NEXT_BUNDLE_ID.getAndIncrement();
        tag.putInt(TAG_BUNDLE_ID, this.bundleId);
        if (!tag.containsList(TAG_STORAGE_ITEM_COMPONENT_CONTENT)) {
            tag.putList(new ListTag<>(TAG_STORAGE_ITEM_COMPONENT_CONTENT));
        }
        this.setNamedTag(tag);
    }

    private CompoundTag ensureBundleTag() {
        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : new CompoundTag();
        boolean dirty = !this.hasCompoundTag();

        if (this.bundleId <= 0) {
            if (this.sourceBundleId <= 0 && tag.contains(TAG_BUNDLE_ID)) {
                this.sourceBundleId = tag.getInt(TAG_BUNDLE_ID);
            }
            this.bundleId = NEXT_BUNDLE_ID.getAndIncrement();
            tag.putInt(TAG_BUNDLE_ID, this.bundleId);
            dirty = true;
        } else if (!tag.contains(TAG_BUNDLE_ID) || tag.getInt(TAG_BUNDLE_ID) != this.bundleId) {
            tag.putInt(TAG_BUNDLE_ID, this.bundleId);
            dirty = true;
        } else {
            NEXT_BUNDLE_ID.updateAndGet(current -> Math.max(current, tag.getInt(TAG_BUNDLE_ID) + 1));
        }
        if (!tag.containsList(TAG_STORAGE_ITEM_COMPONENT_CONTENT)) {
            tag.putList(new ListTag<>(TAG_STORAGE_ITEM_COMPONENT_CONTENT));
            dirty = true;
        }
        if (dirty) {
            this.setNamedTag(tag);
        }
        return tag;
    }
}
