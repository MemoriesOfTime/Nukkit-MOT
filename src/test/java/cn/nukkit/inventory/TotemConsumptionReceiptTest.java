package cn.nukkit.inventory;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

class TotemConsumptionReceiptTest {
    @Test void consumesOneAndRetainsSupportedRemainderInEitherHand() {
        for (boolean offhand : List.of(false, true)) {
            for (int count : List.of(1, 2)) {
                try (Fixture f = new Fixture(offhand)) {
                    Item selected = f.seed(count);
                    Item consumed = decrement(f.inventory, 0, 0, selected);
                    assertNotNull(consumed);
                    assertEquals(1, consumed.getCount());
                    assertEquals(Item.TOTEM, consumed.getId());
                    assertEquals(count - 1, f.inventory.getItem(0).getCount());
                    assertNotSame(selected, consumed);
                }
            }
        }
    }

    @Test void emptyWrongSlotAndReplacedSelectionRetainLegacyMutationWithoutReceipt() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                assertNull(decrement(f.inventory, 0, 0, null));
                Item selected = f.seed(1);
                assertNull(decrement(f.inventory, 0, 1, selected));
                assertEquals(0, f.inventory.getItem(0).getCount());
                selected = f.seed(1);
                f.inventory.slots.put(0, selected.clone());
                assertNull(decrement(f.inventory, 0, 0, selected));
                assertEquals(0, f.inventory.getItem(0).getCount());
            }
        }
    }

    @Test void nestedPublicWriteCannotConfirmOrImpersonateOuterDecrement() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                Item selected = f.seed(1);
                AtomicBoolean nested = new AtomicBoolean();
                f.callback = e -> {
                    if (e instanceof EntityInventoryChangeEvent && nested.compareAndSet(false, true)) {
                        f.inventory.clear(0);
                        f.inventory.setItem(0, selected.clone());
                    }
                };
                assertNull(decrement(f.inventory, 0, 0, selected));
                assertTrue(nested.get());
                assertEquals(0, f.inventory.getItem(0).getCount());
            }
        }
    }

    @Test void nestedObservedDecrementHasItsOwnReceiptAndOuterDoesNotDuplicateIt() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                Item selected = f.seed(1);
                AtomicBoolean entered = new AtomicBoolean();
                Item[] inner = new Item[1];
                f.callback = e -> {
                    if (e instanceof EntityInventoryChangeEvent && entered.compareAndSet(false, true)) {
                        inner[0] = decrement(f.inventory, 0, 0, selected);
                    }
                };
                assertNull(decrement(f.inventory, 0, 0, selected));
                assertNotNull(inner[0]);
                assertEquals(1, inner[0].getCount());
                assertEquals(0, f.inventory.getItem(0).getCount());
            }
        }
    }

    @Test void committedReceiptSurvivesLaterSlotCallbackReplacement() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                BaseInventory observed = spy(f.inventory);
                assertEquals(f.inventory.getClass(), observed.getClass());
                Item selected = f.seed(1);
                doAnswer(i -> {
                    observed.slots.put(0, new Item(Item.DIAMOND, 0, 1));
                    return null;
                }).when(observed).onSlotChange(eq(0), any(Item.class), eq(true));
                Item consumed = decrement(observed, 0, 0, selected);
                assertNotNull(consumed);
                assertEquals(Item.TOTEM, consumed.getId());
                assertEquals(Item.DIAMOND, observed.getItem(0).getId());
            }
        }
    }

    @Test void exceptionBeforeOrAfterCommitIsNotConvertedToCompletedConsumption() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                Item selected = f.seed(1);
                f.callback = e -> { throw new IllegalStateException("before commit"); };
                assertThrows(IllegalStateException.class, () -> decrement(f.inventory, 0, 0, selected));
                assertSame(selected, f.inventory.slots.get(0));
                f.callback = e -> {};
                BaseInventory observed = spy(f.inventory);
                doThrow(new IllegalStateException("after commit")).when(observed)
                        .onSlotChange(eq(0), any(Item.class), eq(true));
                assertThrows(IllegalStateException.class, () -> decrement(observed, 0, 0, selected));
                assertFalse(observed.slots.containsKey(0));
            }
        }
    }

    @Test void overflowRemainsLegacyBehaviorWithoutUnprovenReceipt() {
        try (Fixture f = new Fixture(false)) {
            Item selected = f.seed(3);
            assertNull(decrement(f.inventory, 0, 0, selected));
            assertEquals(2, f.inventory.slots.values().stream()
                    .filter(i -> i.getId() == Item.TOTEM).mapToInt(Item::getCount).sum());
        }
    }

    @Test void immutableSnapshotPreservesRetainedRootAndSeesCachedOnlyMutation() {
        Item item = new Item(Item.TOTEM, 0, 1);
        item.setNamedTag(new CompoundTag().putString("name", "kept"));
        CompoundTag retained = item.getNamedTag();
        retained.setName("tag");
        retained.putBoolean("temporary", true);
        CompoundTag snapshot = (CompoundTag) invoke(Item.class, item, "getNamedTagSnapshot", new Class<?>[0]);
        assertEquals("tag", retained.getName());
        assertTrue(snapshot.getBoolean("temporary"));
        snapshot.putBoolean("temporary", false);
        assertTrue(retained.getBoolean("temporary"));
    }

    @Test void subclassesKeepAllLegacyVirtualCallsAndNeverInventReceipts() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                int[] calls = new int[5];
                BaseInventory custom = offhand ? new PlayerOffhandInventory(f.player) {
                    @Override public void decreaseCount(int slot) { calls[0]++; super.decreaseCount(slot); }
                    @Override public Item getItem(int slot) { calls[1]++; return super.getItem(slot); }
                    @Override public boolean setItem(int slot, Item item) { calls[2]++; return super.setItem(slot, item); }
                    @Override public boolean setItem(int slot, Item item, boolean send) { calls[3]++; return super.setItem(slot, item, send); }
                    @Override public boolean clear(int slot, boolean send) { calls[4]++; return super.clear(slot, send); }
                } : new PlayerInventory(f.player) {
                    @Override public void decreaseCount(int slot) { calls[0]++; super.decreaseCount(slot); }
                    @Override public Item getItem(int slot) { calls[1]++; return super.getItem(slot); }
                    @Override public boolean setItem(int slot, Item item) { calls[2]++; return super.setItem(slot, item); }
                    @Override public boolean setItem(int slot, Item item, boolean send) { calls[3]++; return super.setItem(slot, item, send); }
                    @Override public boolean clear(int slot, boolean send) { calls[4]++; return super.clear(slot, send); }
                };
                Item selected = new Item(Item.TOTEM, 0, 1);
                custom.slots.put(0, selected);
                assertNull(decrement(custom, 0, 0, selected));
                for (int count : calls) assertTrue(count > 0);
                int clears = calls[4];
                custom.setItem(0, new Item(Item.AIR, 0, 0), true);
                assertEquals(clears + 1, calls[4]);
            }
        }
    }

    @Test void existingAndCallbackCreatedClaimsCannotPublishProvisionalSpend() {
        for (boolean offhand : List.of(false, true)) {
            try (Fixture f = new Fixture(offhand)) {
                Item selected = f.seed(1);
                try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(f.inventory))) {
                    assertNotNull(claim);
                    assertNull(decrement(f.inventory, 0, 0, selected));
                }
                selected = f.seed(1);
                InventoryMutationGuard.Lease[] nested = new InventoryMutationGuard.Lease[1];
                f.callback = e -> {
                    if (e instanceof EntityInventoryChangeEvent && nested[0] == null) {
                        nested[0] = InventoryMutationGuard.tryClaim(List.of(f.inventory));
                    }
                };
                try {
                    assertNull(decrement(f.inventory, 0, 0, selected));
                    assertNotNull(nested[0]);
                } finally {
                    if (nested[0] != null) nested[0].close();
                }
                f.callback = e -> {};
                selected = f.seed(1);
                assertNotNull(decrement(f.inventory, 0, 0, selected));
            }
        }
    }

    private static Item decrement(BaseInventory inventory, int slot, int selectedSlot, Item selected) {
        return (Item) invoke(BaseInventory.class, inventory, "decreaseTotemCount",
                new Class<?>[] {int.class, int.class, Item.class}, slot, selectedSlot, selected);
    }
    private static Object invoke(Class<?> owner, Object target, String name, Class<?>[] signature, Object... args) {
        try { return owner.getMethod(name, signature).invoke(target, args); }
        catch (java.lang.reflect.InvocationTargetException wrapped) {
            Throwable cause = wrapped.getCause();
            if (cause instanceof RuntimeException failure) throw failure;
            if (cause instanceof Error failure) throw failure;
            throw new AssertionError(cause);
        } catch (ReflectiveOperationException missing) { throw new AssertionError("GREEN-only receipt API qualification", missing); }
    }

    private static final class Fixture implements AutoCloseable {
        final Server server = mock(Server.class, RETURNS_DEEP_STUBS);
        final MockedStatic<Server> singleton = mockStatic(Server.class);
        final Player player = mock(Player.class);
        final BaseInventory inventory;
        Consumer<Event> callback = e -> {};

        Fixture(boolean offhand) {
            singleton.when(Server::getInstance).thenReturn(server);
            player.protocol = 2169;
            when(player.getLevel()).thenReturn(mock(Level.class));
            inventory = offhand ? new PlayerOffhandInventory(player) : new PlayerInventory(player);
            var events = server.getPluginManager();
            doAnswer(i -> { callback.accept(i.getArgument(0)); return null; }).when(events).callEvent(any(Event.class));
        }
        Item seed(int count) {
            Item item = new cn.nukkit.item.ItemTotem(0);
            item.setCount(count);
            inventory.slots.put(0, item);
            return inventory.getItemFast(0);
        }
        public void close() { singleton.close(); }
    }
}
