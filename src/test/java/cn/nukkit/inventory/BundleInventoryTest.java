package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BundleInventoryTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @BeforeEach
    void resetServer() {
        MockServer.reset();
    }

    @Test
    void saveNbtKeepsBundleContentsAndCloneDoesNotShareInventoryInstance() {
        ItemBundle bundle = new ItemBundle();
        int bundleId = bundle.getBundleId();
        BundleInventory inventory = bundle.getInventory();

        assertTrue(inventory.setItem(0, Item.get(Item.STONE, 0, 16), false));

        ListTag<CompoundTag> storedItems = bundle.getNamedTag()
                .getList(ItemBundle.TAG_STORAGE_ITEM_COMPONENT_CONTENT, CompoundTag.class);
        assertEquals(1, storedItems.size());

        ItemBundle clone = bundle.clone();
        assertEquals(bundleId, clone.getBundleId());
        assertNotSame(inventory, clone.getInventory());
        assertEquals(Item.STONE, clone.getInventory().getItem(0).getId());
        assertEquals(16, clone.getInventory().getItem(0).getCount());
    }

    @Test
    void rejectsItemsThatWouldOverfillTheBundle() {
        ItemBundle bundle = new ItemBundle();
        BundleInventory inventory = bundle.getInventory();

        assertFalse(inventory.setItem(0, Item.get(Item.DIRT, 0, 65), false));
        assertTrue(inventory.isEmpty());
    }
}
