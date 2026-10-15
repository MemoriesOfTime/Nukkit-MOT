package cn.nukkit.inventory;

import cn.nukkit.MockServer;
import cn.nukkit.item.Item;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class InventoryBulkErrorRollbackTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @Test void failedAdditionObserverRestoresItsSlotAndKeepsEarlierAcceptedCredit() {
        BaseInventory inventory = nativeInventory();
        AssertionError failure = failOnceAfterWritingSecondSlot(inventory);
        Item request = Item.get(Item.DIAMOND, 0, 100);

        assertSame(failure, assertThrows(AssertionError.class, () -> inventory.addItem(request)));
        assertEquals(64, inventory.getItem(0).getCount(), "earlier accepted credit remains committed");
        assertTrue(inventory.getItem(1).isNull(), "the failing setter's credit must be restored");
        assertEquals(64, count(inventory));
        assertEquals(100, request.getCount(), "the caller's requested stack is never borrowed");

        assertEquals(0, inventory.addItem(Item.get(Item.DIAMOND, 0, 36)).length,
                "write ownership must be released after Error cleanup");
        assertEquals(64, inventory.getItem(0).getCount());
        assertEquals(36, inventory.getItem(1).getCount());
        assertEquals(100, count(inventory));
    }

    @Test void failedRemovalObserverRestoresItsSlotAndKeepsEarlierAcceptedDebit() {
        BaseInventory inventory = nativeInventory();
        inventory.setItemForce(0, Item.get(Item.DIAMOND, 0, 64));
        inventory.setItemForce(1, Item.get(Item.DIAMOND, 0, 36));
        AssertionError failure = failOnceAfterWritingSecondSlot(inventory);
        Item request = Item.get(Item.DIAMOND, 0, 100);

        assertSame(failure, assertThrows(AssertionError.class, () -> inventory.removeItem(request)));
        assertTrue(inventory.getItem(0).isNull(), "earlier accepted debit remains committed");
        assertEquals(36, inventory.getItem(1).getCount(), "the failing setter's debit must be restored");
        assertEquals(36, count(inventory));
        assertEquals(100, request.getCount());

        assertEquals(0, inventory.removeItem(Item.get(Item.DIAMOND, 0, 36)).length,
                "write ownership must be released after Error cleanup");
        assertTrue(inventory.getContents().isEmpty());
    }

    private static BaseInventory nativeInventory() {
        // Only construction differs; every getter/setter/bulk operation is the native implementation.
        return spy(new BaseInventory(null, InventoryType.CHEST, new HashMap<>(), 2) { });
    }

    private static AssertionError failOnceAfterWritingSecondSlot(BaseInventory inventory) {
        AssertionError failure = new AssertionError("native slot observer failed");
        AtomicBoolean thrown = new AtomicBoolean();
        doAnswer(call -> {
            if (thrown.compareAndSet(false, true)) throw failure;
            return call.callRealMethod();
        }).when(inventory).onSlotChange(eq(1), any(Item.class), anyBoolean());
        return failure;
    }

    private static int count(BaseInventory inventory) {
        return inventory.getContents().values().stream().mapToInt(Item::getCount).sum();
    }
}
