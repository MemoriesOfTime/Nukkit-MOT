package cn.nukkit.inventory;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;

import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/** Prevent nested inventory operations from committing effects into another operation's rollback. */
public final class InventoryMutationGuard {
    private static final Set<Inventory> CLAIMED = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final ThreadLocal<WriteScope> EXPECTED_WRITE = new ThreadLocal<>();

    private InventoryMutationGuard() { }

    public static Lease tryClaim(Collection<? extends Inventory> inventories) {
        Set<Inventory> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Inventory inventory : inventories) {
            collectBackings(inventory, owned);
        }
        synchronized (CLAIMED) {
            for (Inventory inventory : owned) {
                if (CLAIMED.contains(inventory)) return null;
            }
            CLAIMED.addAll(owned);
        }
        return new Lease(owned);
    }

    private static void collectBackings(Inventory inventory, Set<Inventory> owned) {
        if (!owned.add(inventory)) return;
        if (inventory instanceof DoubleChestInventory chest) {
            collectBackings(chest.getLeftSide(), owned);
            collectBackings(chest.getRightSide(), owned);
        } else if (inventory instanceof PlayerUIComponent component) {
            collectBackings(component.getBackingInventory(), owned);
        }
    }

    /** Reject callback-authored replacements before a strict transfer can route overflow into the world. */
    public static boolean acceptsWrite(Inventory inventory, int slot, Item target) {
        WriteScope active = EXPECTED_WRITE.get();
        for (WriteScope expected = active; expected != null; expected = expected.previous) {
            if (expected.owned.contains(inventory)) {
                return expected == active && expected.inventory == inventory
                        && expected.slot == slot && matchesTarget(target, expected.target)
                        && fitsCurrentLimit(inventory, expected.requestedInventory, target);
            }
        }
        return true;
    }

    private static boolean fitsCurrentLimit(Inventory inventory, Inventory requested, Item target) {
        if (target.isNull()) return true;
        int limit = Math.min(target.getMaxStackSize(),
                Math.min(inventory.getMaxStackSize(), requested.getMaxStackSize()));
        return limit > 0 && target.getCount() > 0 && target.getCount() <= limit;
    }

    public static boolean matchesTarget(Item actual, Item expected) {
        if (actual.isNull() && expected.isNull()) return true;
        if (actual instanceof ItemBundle && expected instanceof ItemBundle) {
            // Only this documented bundle runtime ID changes at ingress; contents/serials/other NBT remain authored.
            actual = actual.clone();
            expected = expected.clone();
            var actualTag = actual.getNamedTag().clone();
            var expectedTag = expected.getNamedTag().clone();
            actualTag.remove(ItemBundle.TAG_BUNDLE_ID);
            expectedTag.remove(ItemBundle.TAG_BUNDLE_ID);
            actual.setNamedTag(actualTag);
            expected.setNamedTag(expectedTag);
        }
        return actual.equalsExact(expected);
    }

    public static final class WriteScope implements AutoCloseable {
        private final Set<Inventory> owned;
        private final Inventory inventory;
        private final Inventory requestedInventory;
        private final int slot;
        private final Item target;
        private final WriteScope previous;

        private WriteScope(Set<Inventory> owned, Inventory inventory, Inventory requestedInventory, int slot, Item target) {
            this.owned = owned;
            this.inventory = inventory;
            this.requestedInventory = requestedInventory;
            this.slot = slot;
            this.target = target.clone();
            this.previous = EXPECTED_WRITE.get();
            EXPECTED_WRITE.set(this);
        }

        @Override public void close() {
            if (previous == null) EXPECTED_WRITE.remove();
            else EXPECTED_WRITE.set(previous);
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Set<Inventory> owned;
        private boolean closed;

        private Lease(Set<Inventory> owned) { this.owned = owned; }

        public boolean contains(Inventory inventory) { return owned.contains(inventory); }

        public WriteScope expectWrite(Inventory inventory, int slot, Item target) {
            if (closed || !contains(inventory)) throw new IllegalStateException("Inventory is not claimed");
            Inventory requestedInventory = inventory;
            if (inventory instanceof DoubleChestInventory chest) {
                int leftSize = chest.getLeftSide().getSize();
                inventory = slot < leftSize ? chest.getLeftSide() : chest.getRightSide();
                if (slot >= leftSize) slot -= leftSize;
            } else if (inventory instanceof PlayerUIComponent component) {
                slot = component.getBackingSlot(slot);
                inventory = component.getBackingInventory();
            }
            return new WriteScope(owned, inventory, requestedInventory, slot, target);
        }

        @Override public void close() {
            synchronized (CLAIMED) {
                if (!closed) {
                    CLAIMED.removeAll(owned);
                    closed = true;
                }
            }
        }
    }
}
