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
    private static final java.util.Map<Object, DeferredClose> DEFERRED_CLOSES = new IdentityHashMap<>();

    private InventoryMutationGuard() { }

    /** Native close copies cannot observe a provisional owner snapshot. Deduplicate by the native window identity. */
    public static boolean deferUntilReleased(Collection<? extends Inventory> inventories, Object identity, Runnable close) {
        Set<Inventory> backings = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Inventory inventory : inventories) collectBackings(inventory, backings);
        synchronized (CLAIMED) {
            if (!isOwned(backings)) return false;
            DEFERRED_CLOSES.putIfAbsent(identity, new DeferredClose(backings, close));
            return true;
        }
    }

    private static boolean isOwned(Set<Inventory> backings) {
        if (overlapsWrite(backings)) return true;
        for (Inventory inventory : backings) if (CLAIMED.contains(inventory)) return true;
        return false;
    }

    /** A close error must never turn an accepted caller commit into a source refund. */
    private static void drainDeferredCloses() {
        java.util.List<Runnable> ready = new java.util.ArrayList<>();
        synchronized (CLAIMED) {
            var entries = DEFERRED_CLOSES.entrySet().iterator();
            while (entries.hasNext()) {
                DeferredClose deferred = entries.next().getValue();
                if (!isOwned(deferred.backings())) {
                    ready.add(deferred.close());
                    entries.remove();
                }
            }
        }
        for (Runnable close : ready) {
            try { close.run(); } catch (RuntimeException | Error failure) {
                try {
                    cn.nukkit.Server server = cn.nukkit.Server.getInstance();
                    if (server != null && server.getLogger() != null) server.getLogger().logException(failure);
                } catch (RuntimeException | Error ignored) {
                    // Logging failures also cannot manufacture a rollback after the caller's commit.
                }
            }
        }
    }

    private record DeferredClose(Set<Inventory> backings, Runnable close) { }


    public static Lease tryClaim(Collection<? extends Inventory> inventories) {
        Set<Inventory> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Inventory inventory : inventories) {
            collectBackings(inventory, owned);
        }
        if (overlapsWrite(owned)) return null;
        synchronized (CLAIMED) {
            for (Inventory inventory : owned) {
                if (CLAIMED.contains(inventory)) return null;
            }
            CLAIMED.addAll(owned);
        }
        return new Lease(owned);
    }

    /** Profiles cannot capture provisional slots owned by an atomic claim or an exact bulk write. */
    public static boolean canCaptureProfile(Collection<? extends Inventory> inventories) {
        synchronized (CLAIMED) {
            // Ordinary autosave must not initialize lazy bundle containers or allocate a backing traversal.
            if (CLAIMED.isEmpty() && EXPECTED_WRITE.get() == null) return true;
        }
        Set<Inventory> backings = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Inventory inventory : inventories) collectProfileBackings(inventory, backings);
        if (overlapsWrite(backings)) return false;
        synchronized (CLAIMED) {
            for (Inventory inventory : backings) {
                if (CLAIMED.contains(inventory)) return false;
            }
        }
        return true;
    }

    private static void collectProfileBackings(Inventory inventory, Set<Inventory> backings) {
        if (inventory == null || !backings.add(inventory)) return;
        if (inventory instanceof DoubleChestInventory chest) {
            collectProfileBackings(chest.getLeftSide(), backings);
            collectProfileBackings(chest.getRightSide(), backings);
        } else if (inventory instanceof PlayerUIComponent component) {
            collectProfileBackings(component.getBackingInventory(), backings);
        }
        // Contents hold the actual item instances; a bundle's live container mutates its carrier NBT.
        // Identity traversal rejects only containers reachable from this profile, not another player's bundle.
        for (Item item : inventory.getContents().values()) {
            if (item instanceof ItemBundle bundle) collectProfileBackings(bundle.getInventory(), backings);
        }
    }

    private static boolean overlapsWrite(Set<Inventory> inventories) {
        for (WriteScope scope = EXPECTED_WRITE.get(); scope != null; scope = scope.previous) {
            for (Inventory inventory : inventories) {
                if (scope.owned.contains(inventory)) return true;
            }
        }
        return false;
    }

    /** Exact bulk writes can run inside an outer pickup lease, but cannot nest into a callback. */
    public static WriteScope tryExactBulkWrite(Inventory inventory, int slot, Item target) {
        Set<Inventory> owned = Collections.newSetFromMap(new IdentityHashMap<>());
        collectBackings(inventory, owned);
        if (overlapsWrite(owned)) return null;
        return expectedWrite(owned, inventory, slot, target, true);
    }

    private static WriteScope expectedWrite(Set<Inventory> owned, Inventory inventory, int slot, Item target, boolean exactBulk) {
        Inventory requestedInventory = inventory;
        if (inventory instanceof DoubleChestInventory chest) {
            int leftSize = chest.getLeftSide().getSize();
            inventory = slot < leftSize ? chest.getLeftSide() : chest.getRightSide();
            if (slot >= leftSize) slot -= leftSize;
        } else if (inventory instanceof PlayerUIComponent component) {
            slot = component.getBackingSlot(slot);
            inventory = component.getBackingInventory();
        }
        return new WriteScope(owned, inventory, requestedInventory, slot, target, exactBulk);
    }

    private static void collectBackings(Inventory inventory, Set<Inventory> owned) {
        if (inventory == null || !owned.add(inventory)) return;
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
                return expected == active && expected.inventory != null && expected.inventory == inventory
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
        private final Item slotBefore;
        private final boolean exactBulk;

        private WriteScope(Set<Inventory> owned, Inventory inventory, Inventory requestedInventory, int slot, Item target, boolean exactBulk) {
            this.owned = owned;
            this.inventory = inventory;
            this.requestedInventory = requestedInventory;
            this.slot = slot;
            this.target = target == null ? null : target.clone();
            this.exactBulk = exactBulk;
            Item stored = exactBulk && inventory instanceof BaseInventory nativeInventory
                    ? nativeInventory.slots.get(slot) : null;
            this.slotBefore = stored == null ? null : stored.clone();
            this.previous = EXPECTED_WRITE.get();
            EXPECTED_WRITE.set(this);
        }

        /** Restore only the failed native slot; earlier accepted bulk slots remain accepted. */
        public void restorePreviousSlot() {
            if (!exactBulk || !(inventory instanceof BaseInventory nativeInventory)) {
                throw new IllegalStateException("Exact bulk writes require a native inventory");
            }
            if (slotBefore == null) nativeInventory.slots.remove(slot);
            else nativeInventory.slots.put(slot, slotBefore.clone());
            if (nativeInventory.getHolder() instanceof cn.nukkit.blockentity.BlockEntity holder) holder.setDirty();
            if (nativeInventory instanceof BundleInventory bundle) bundle.getHolder().saveNBT();
        }

        @Override public void close() {
            if (previous == null) EXPECTED_WRITE.remove();
            else EXPECTED_WRITE.set(previous);
            drainDeferredCloses();
        }
    }

    /** An inventory rollback restores native dimensions without invoking resizing callbacks. */
    public static final class NativeMetadataSnapshot {
        private final java.util.Map<BaseInventory, int[]> metadata = new IdentityHashMap<>();

        private NativeMetadataSnapshot(Set<Inventory> owned) {
            for (Inventory inventory : owned) {
                if (inventory instanceof BaseInventory nativeInventory) {
                    metadata.put(nativeInventory, new int[] {nativeInventory.size, nativeInventory.maxStackSize});
                }
            }
        }

        public boolean matches() {
            for (var entry : metadata.entrySet()) {
                if (entry.getKey().size != entry.getValue()[0]
                        || entry.getKey().maxStackSize != entry.getValue()[1]) return false;
            }
            return true;
        }

        public void restore() {
            metadata.forEach((inventory, values) -> {
                inventory.size = values[0];
                inventory.maxStackSize = values[1];
            });
        }
    }

    public static final class Lease implements AutoCloseable {
        private final Set<Inventory> owned;
        private boolean closed;

        private Lease(Set<Inventory> owned) { this.owned = owned; }

        public boolean contains(Inventory inventory) { return owned.contains(inventory); }

        public NativeMetadataSnapshot captureNativeMetadata() {
            if (closed) throw new IllegalStateException("Inventory claim is closed");
            return new NativeMetadataSnapshot(owned);
        }

        /** Native transaction callbacks cannot alter a backing owned by this transaction. */
        public WriteScope blockWrites() {
            if (closed) throw new IllegalStateException("Inventory claim is closed");
            return new WriteScope(owned, null, null, -1, null, false);
        }

        public WriteScope expectWrite(Inventory inventory, int slot, Item target) {
            if (closed || !contains(inventory)) throw new IllegalStateException("Inventory is not claimed");
            return expectedWrite(owned, inventory, slot, target, false);
        }

        @Override public void close() {
            synchronized (CLAIMED) {
                if (!closed) {
                    CLAIMED.removeAll(owned);
                    closed = true;
                }
            }
            drainDeferredCloses();
        }
    }
}
