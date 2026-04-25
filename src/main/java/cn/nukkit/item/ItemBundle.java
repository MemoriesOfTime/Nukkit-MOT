package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.InventoryHolder;
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
            NEXT_BUNDLE_ID.updateAndGet(current -> Math.max(current, tag.getInt(TAG_BUNDLE_ID) + 1));
        }
        return this;
    }

    @Override
    public ItemBundle clone() {
        ItemBundle cloned = (ItemBundle) super.clone();
        cloned.inventory = null;
        return cloned;
    }

    private CompoundTag ensureBundleTag() {
        CompoundTag tag = this.hasCompoundTag() ? this.getNamedTag() : new CompoundTag();
        boolean dirty = !this.hasCompoundTag();

        if (!tag.contains(TAG_BUNDLE_ID)) {
            tag.putInt(TAG_BUNDLE_ID, NEXT_BUNDLE_ID.getAndIncrement());
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
