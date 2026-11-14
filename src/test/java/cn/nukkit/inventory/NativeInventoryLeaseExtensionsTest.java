package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** GREEN-only qualification of the new helper contract. API absence is never behavioral RED. */
class NativeInventoryLeaseExtensionsTest {
    @BeforeAll static void init() { MockServer.init(); }

    @Test void blockedUiAliasCannotWriteButUnrelatedNativeInventoryCan() {
        Player player = mock(Player.class);
        PlayerUIInventory ui = new PlayerUIInventory(player);
        PlayerInventory unrelated = new PlayerInventory(mock(Player.class));
        try (var lease = InventoryMutationGuard.tryClaim(List.of(ui.getCraftingGrid()));
             var blocked = lease.blockWrites()) {
            assertFalse(ui.getCursorInventory().setItem(0, Item.get(Item.DIAMOND)));
            assertFalse(ui.getCraftingGrid().setItem(0, Item.get(Item.LOG)));
            assertTrue(unrelated.setItem(0, Item.get(Item.DIAMOND)));
            assertFalse(InventoryMutationGuard.canCaptureProfile(List.of(ui.getCursorInventory())));
            assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(unrelated)));
        }
        assertTrue(ui.slots.isEmpty());
        assertEquals(1, unrelated.getItem(0).getCount());
        assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(ui)));
    }

    @Test void metadataRestoreUsesNativeDimensionsWithoutResizeCallbacksOrHiddenSlotLoss() {
        PlayerInventory inventory = spy(new PlayerInventory(mock(Player.class)));
        Item hidden = Item.get(Item.DIAMOND, 0, 3);
        inventory.slots.put(101, hidden);
        try (var lease = InventoryMutationGuard.tryClaim(List.of(inventory))) {
            var metadata = lease.captureNativeMetadata();
            assertTrue(metadata.matches());
            inventory.size = 1;
            inventory.maxStackSize = 2;
            assertFalse(metadata.matches());
            metadata.restore();
            assertTrue(metadata.matches());
            assertEquals(36, inventory.getSize());
            assertEquals(64, inventory.getMaxStackSize());
            assertSame(hidden, inventory.slots.get(101));
            verify(inventory, never()).setSize(anyInt());
            verify(inventory, never()).setMaxStackSize(anyInt());
        }
    }

    @Test void releasedLeaseCannotCreateNewWriteOrMetadataScopes() {
        var inventory = new PlayerInventory(mock(Player.class));
        var lease = InventoryMutationGuard.tryClaim(List.of(inventory));
        lease.close();
        assertThrows(IllegalStateException.class, lease::blockWrites);
        assertThrows(IllegalStateException.class, lease::captureNativeMetadata);
    }

    @Test void nestedBundleClaimBlocksProfileCaptureOfItsContainingInventory() {
        var main = new PlayerInventory(mock(Player.class));
        var bundle = mock(BundleInventory.class);
        var item = mock(ItemBundle.class);
        when(item.getInventory()).thenReturn(bundle);
        main.slots.put(0, item);
        // Keep a native retained reference: getContents returns actual raw item objects.
        when(bundle.getContents()).thenReturn(Map.of());
        try (var lease = InventoryMutationGuard.tryClaim(List.of(bundle))) {
            assertNotNull(lease);
            assertFalse(InventoryMutationGuard.canCaptureProfile(List.of(main)));
        }
        assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(main)));
    }

    @Test void unclaimedQueryKeepsLazyBundleUninitialized() {
        var main = new PlayerInventory(mock(Player.class));
        var item = mock(ItemBundle.class);
        main.slots.put(0, item);
        assertTrue(InventoryMutationGuard.canCaptureProfile(List.of(main)));
        verify(item, never()).getInventory();
    }
}
