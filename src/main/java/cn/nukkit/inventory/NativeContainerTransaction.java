package cn.nukkit.inventory;

import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;

/** One native hopper debit and grant; event effects precede the checked pair commit. */
public final class NativeContainerTransaction {
    private static final ThreadLocal<Set<BaseInventory>> ACTIVE = new ThreadLocal<>();
    private NativeContainerTransaction() { }

    /** targetSlot is -1 for ordinary insertion, otherwise the furnace input/fuel slot. */
    public static boolean move(InventoryHolder hopper, Inventory source, int sourceSlot,
                               Inventory target, int targetSlot, BooleanSupplier routeIntact) {
        return move(hopper, source, sourceSlot, target, targetSlot, routeIntact, () -> { });
    }

    /** Retire accepted native cooldown/route state before viewer callbacks may fail or author a new route. */
    @SuppressWarnings("try")
    public static boolean move(InventoryHolder hopper, Inventory source, int sourceSlot,
                               Inventory target, int targetSlot, BooleanSupplier routeIntact, Runnable committedEffects) {
        if (sourceSlot < 0 || sourceSlot >= source.getSize() || source.getItem(sourceSlot).isNull()) return false;
        Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
        if (!capture(source, before) || !capture(target, before)) return false;
        PhysicalSlot debitSlot = physical(source, sourceSlot);
        if (debitSlot == null || sourceSlot < 0 || sourceSlot >= source.getSize()) return false;
        Set<BaseInventory> previous = ACTIVE.get();
        for (BaseInventory inventory : before.keySet()) if (previous != null && previous.contains(inventory)) return false;
        Set<BaseInventory> active = Collections.newSetFromMap(new IdentityHashMap<>());
        if (previous != null) active.addAll(previous);
        active.addAll(before.keySet());
        ACTIVE.set(active);
        try {
            List<Context> contexts = new ArrayList<>();
            for (BaseInventory inventory : before.keySet()) contexts.add(new Context(inventory));
            Position actor = hopper instanceof Position position ? position : null;
            ContextPosition actorContext = actor == null ? null : new ContextPosition(actor);
            Item original = source.getItem(sourceSlot);
            if (original.isNull() || !intact(contexts, actorContext, routeIntact)) return false;
            if (actorContext != null) {
                for (Context context : contexts) if (context.position() == null
                        || context.position().level() != actorContext.level()) return false;
            }
            Item offered = original.clone();
            offered.setCount(1);
            if (insertion(target, targetSlot, offered) == null) return false;
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(source, target, hopper, offered,
                    InventoryMoveItemEvent.Action.SLOT_CHANGE);
            event.call();
            // A callback may perform an independent inventory operation. Never replay a stale
            // source or refund that accepted effect; only this transaction's writes are rolled back.
            if (event.isCancelled() || !matches(before) || !intact(contexts, actorContext, routeIntact)) return false;
            Item approved = event.getItem();
            if (approved == null || approved.isNull() || approved.getCount() <= 0) return false;
            approved = approved.clone();
            PhysicalSlot creditSlot = insertion(target, targetSlot, approved);
            if (creditSlot == null || debitSlot.equals(creditSlot)) return false;
            Item debit = original.clone();
            debit.setCount(original.getCount() - 1);
            Item credit = targetItem(creditSlot, approved);
            Map<BaseInventory, Map<Integer, Item>> expected = copyBackings(before);
            put(expected.get(debitSlot.inventory()), debitSlot.index(), debit);
            put(expected.get(creditSlot.inventory()), creditSlot.index(), credit);
            Throwable failure = null;
            try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(source, target))) {
                if (claim == null || !matches(before) || !intact(contexts, actorContext, routeIntact)) return false;
                InventoryMutationGuard.NativeMetadataSnapshot metadata = claim.captureNativeMetadata();
                try (InventoryMutationGuard.WriteScope blocked = claim.blockWrites()) {
                    try {
                        try (InventoryMutationGuard.WriteScope write = claim.expectWrite(debitSlot.inventory(), debitSlot.index(), debit)) {
                            if (!debitSlot.inventory().setItem(debitSlot.index(), debit, false)) return rollback(before, metadata);
                        }
                        if (!intact(contexts, actorContext, routeIntact) || !metadata.matches()) return rollback(before, metadata);
                        try (InventoryMutationGuard.WriteScope write = claim.expectWrite(creditSlot.inventory(), creditSlot.index(), credit)) {
                            if (!creditSlot.inventory().setItem(creditSlot.index(), credit, false)) return rollback(before, metadata);
                        }
                        if (!matches(expected) || !metadata.matches() || !intact(contexts, actorContext, routeIntact)) {
                            return rollback(before, metadata);
                        }
                    } catch (RuntimeException | Error writeFailure) {
                        rollback(before, metadata);
                        throw writeFailure;
                    }
                    // The complete pair is accepted. Effects cannot convert a later notification error
                    // into a refund, and a new route authored by a viewer callback must remain intact.
                    try { committedEffects.run(); } catch (RuntimeException | Error notification) { failure = notification; }
                }
            }
            // Packet/viewer errors occur after acceptance and cannot refund the pair.
            for (PhysicalSlot slot : List.of(debitSlot, creditSlot)) {
                try { slot.inventory().sendSlot(slot.index(), slot.inventory().getViewers()); }
                catch (RuntimeException | Error notification) {
                    if (failure == null) failure = notification;
                    else if (failure != notification) failure.addSuppressed(notification);
                }
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
            return true;
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    public static boolean isActive(Inventory inventory) {
        if (ACTIVE.get() == null) return false;
        Map<BaseInventory, Map<Integer, Item>> backings = new IdentityHashMap<>();
        if (!capture(inventory, backings)) return true;
        Set<BaseInventory> active = ACTIVE.get();
        if (active == null) return false;
        for (BaseInventory backing : backings.keySet()) if (active.contains(backing)) return true;
        return false;
    }

    /** Furnace conversion callbacks may author their output; inventory edits invalidate the old input. */
    @SuppressWarnings("try")
    public static boolean convert(Inventory inventory, BooleanSupplier event,
            java.util.function.Supplier<Map<Integer, Item>> changes, BooleanSupplier stateIntact, Runnable rollbackState, Runnable effects) {
        Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
        if (!capture(inventory, before) || isActive(inventory)) return false;
        Set<BaseInventory> previous = ACTIVE.get();
        Set<BaseInventory> active = Collections.newSetFromMap(new IdentityHashMap<>());
        if (previous != null) active.addAll(previous);
        active.addAll(before.keySet());
        ACTIVE.set(active);
        try {
            List<Context> contexts = new ArrayList<>();
            for (BaseInventory backing : before.keySet()) contexts.add(new Context(backing));
            if (!intact(contexts, null, stateIntact) || !event.getAsBoolean()
                    || !matches(before) || !intact(contexts, null, stateIntact)) return false;
            Map<Integer, Item> writes = new java.util.LinkedHashMap<>();
            Map<BaseInventory, Map<Integer, Item>> expected = copyBackings(before);
            for (var entry : changes.get().entrySet()) {
                int slot = entry.getKey();
                Item item = entry.getValue();
                if (slot < 0 || slot >= inventory.getSize() || item == null || !item.isNull()
                        && (item.getCount() <= 0 || item.getCount() > Math.min(item.getMaxStackSize(), inventory.getMaxStackSize()))) return false;
                writes.put(slot, item.clone());
                PhysicalSlot backing = physical(inventory, slot);
                put(expected.get(backing.inventory()), backing.index(), item);
            }
            Throwable failure = null;
            try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(inventory))) {
                if (claim == null || !matches(before) || !intact(contexts, null, stateIntact)) return false;
                InventoryMutationGuard.NativeMetadataSnapshot metadata = claim.captureNativeMetadata();
                try (InventoryMutationGuard.WriteScope blocked = claim.blockWrites()) {
                    try {
                        for (var entry : writes.entrySet()) {
                            try (InventoryMutationGuard.WriteScope write = claim.expectWrite(inventory, entry.getKey(), entry.getValue())) {
                                if (!inventory.setItem(entry.getKey(), entry.getValue(), false)) { rollbackState.run(); return rollback(before, metadata); }
                            }
                            if (!metadata.matches() || !intact(contexts, null, stateIntact)) { rollbackState.run(); return rollback(before, metadata); }
                        }
                        if (!matches(expected)) { rollbackState.run(); return rollback(before, metadata); }
                    } catch (RuntimeException | Error writeFailure) {
                        rollback(before, metadata);
                        rollbackState.run();
                        throw writeFailure;
                    }
                    // All physical writes are accepted. Commit timers/XP before the lease drains
                    // native close callbacks; an effect or viewer error must not restore those writes.
                    try { effects.run(); } catch (RuntimeException | Error notification) { failure = notification; }
                }
            }
            for (int slot : writes.keySet()) {
                try { inventory.sendSlot(slot, inventory.getViewers()); }
                catch (RuntimeException | Error notification) {
                    if (failure == null) failure = notification;
                    else if (failure != notification) failure.addSuppressed(notification);
                }
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
            return true;
        } finally {
            if (previous == null) ACTIVE.remove(); else ACTIVE.set(previous);
        }
    }

    private static boolean nativeContainer(BaseInventory inventory) {
        Class<?> type = inventory.getClass();
        return type == ChestInventory.class || type == HopperInventory.class || type == FurnaceInventory.class
                || type == BlastFurnaceInventory.class || type == SmokerInventory.class || type == BarrelInventory.class
                || type == ShulkerBoxInventory.class || type == DispenserInventory.class || type == DropperInventory.class
                || type == BrewingInventory.class || type == CampfireInventory.class || type == CrafterInventory.class || type == ChestBoatInventory.class
                || type == MinecartHopperInventory.class || type == MinecartChestInventory.class;
    }

    private static boolean capture(Inventory inventory, Map<BaseInventory, Map<Integer, Item>> before) {
        if (inventory instanceof DoubleChestInventory chest) {
            return capture(chest.getLeftSide(), before) && capture(chest.getRightSide(), before);
        }
        if (!(inventory instanceof BaseInventory backing) || !nativeContainer(backing)) return false;
        before.computeIfAbsent(backing, ignored -> copySlots(backing.slots));
        return true;
    }

    private static PhysicalSlot physical(Inventory inventory, int slot) {
        if (inventory instanceof DoubleChestInventory chest) {
            int size = chest.getLeftSide().getSize();
            return physical(slot < size ? chest.getLeftSide() : chest.getRightSide(), slot < size ? slot : slot - size);
        }
        return inventory instanceof BaseInventory backing ? new PhysicalSlot(backing, slot) : null;
    }

    private static PhysicalSlot insertion(Inventory target, int requested, Item item) {
        if (requested >= 0) {
            if (requested >= target.getSize()) return null;
            PhysicalSlot slot = physical(target, requested);
            return fits(target, slot, item) ? slot : null;
        }
        // Keep native addItem's merge-before-empty preference, without publishing intermediate grants.
        for (int i = 0; i < target.getSize(); i++) {
            PhysicalSlot slot = physical(target, i);
            if (!slot.inventory().getItem(slot.index()).isNull() && fits(target, slot, item)) return slot;
        }
        for (int i = 0; i < target.getSize(); i++) {
            PhysicalSlot slot = physical(target, i);
            if (slot.inventory().getItem(slot.index()).isNull() && fits(target, slot, item)) return slot;
        }
        return null;
    }

    private static boolean fits(Inventory requested, PhysicalSlot slot, Item item) {
        Item existing = slot.inventory().getItem(slot.index());
        int limit = Math.min(item.getMaxStackSize(), Math.min(requested.getMaxStackSize(), slot.inventory().getMaxStackSize()));
        return item.getCount() > 0 && limit > 0 && (existing.isNull()
                ? item.getCount() <= limit : existing.equals(item, true, true)
                && (long) existing.getCount() + item.getCount() <= limit);
    }

    private static Item targetItem(PhysicalSlot slot, Item item) {
        Item existing = slot.inventory().getItem(slot.index());
        Item result = existing.isNull() ? item.clone() : existing.clone();
        if (!existing.isNull()) result.setCount(existing.getCount() + item.getCount());
        return result;
    }

    private static boolean intact(List<Context> contexts, ContextPosition actor, BooleanSupplier route) {
        if (actor != null && !actor.matches()) return false;
        for (Context context : contexts) if (!context.matches()) return false;
        return route.getAsBoolean();
    }

    private static Map<Integer, Item> copySlots(Map<Integer, Item> slots) {
        Map<Integer, Item> copy = new HashMap<>();
        slots.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }
    private static Map<BaseInventory, Map<Integer, Item>> copyBackings(Map<BaseInventory, Map<Integer, Item>> original) {
        Map<BaseInventory, Map<Integer, Item>> result = new IdentityHashMap<>();
        original.forEach((inventory, slots) -> result.put(inventory, copySlots(slots)));
        return result;
    }
    private static void put(Map<Integer, Item> slots, int slot, Item target) {
        if (target.isNull()) slots.remove(slot); else slots.put(slot, target.clone());
    }
    private static boolean matches(Map<BaseInventory, Map<Integer, Item>> expected) {
        for (var entry : expected.entrySet()) {
            if (entry.getKey().slots.size() != entry.getValue().size()) return false;
            for (var slot : entry.getValue().entrySet()) {
                Item actual = entry.getKey().slots.get(slot.getKey());
                if (actual == null || !actual.equalsExact(slot.getValue())) return false;
            }
        }
        return true;
    }
    private static boolean rollback(Map<BaseInventory, Map<Integer, Item>> before,
                                    InventoryMutationGuard.NativeMetadataSnapshot metadata) {
        metadata.restore();
        before.forEach((inventory, slots) -> { inventory.slots.clear(); inventory.slots.putAll(copySlots(slots)); });
        return false;
    }
    private record PhysicalSlot(BaseInventory inventory, int index) { }
    private record ContextPosition(Position position, cn.nukkit.level.Level level, double x, double y, double z) {
        ContextPosition(Position position) {
            this(position, position.getLevel(), position.x, position.y, position.z);
        }
        boolean matches() {
            if (position.getLevel() != level || position.x != x || position.y != y || position.z != z) return false;
            if (position instanceof BlockEntity block && block.closed) return false;
            return !(position instanceof Entity entity) || !entity.closed && entity.isAlive();
        }
    }
    private record Context(BaseInventory inventory, InventoryHolder holder, int size, int limit,
                           ContextPosition position, Object chunk, DoubleChestInventory paired, int pairX, int pairZ,
                           boolean hasPair) {
        Context(BaseInventory inventory) {
            this(inventory, inventory.getHolder(), inventory.getSize(), inventory.getMaxStackSize(),
                    inventory.getHolder() instanceof Position pos ? new ContextPosition(pos) : null,
                    inventory.getHolder() instanceof BlockEntity block ? block.chunk
                            : inventory.getHolder() instanceof Entity entity ? entity.chunk : null,
                    inventory instanceof ChestInventory chest ? chest.getDoubleInventory() : null,
                    inventory.getHolder() instanceof BlockEntityChest chest ? chest.namedTag.getInt("pairx") : 0,
                    inventory.getHolder() instanceof BlockEntityChest chest ? chest.namedTag.getInt("pairz") : 0,
                    inventory.getHolder() instanceof BlockEntityChest chest && chest.isPaired());
        }
        boolean matches() {
            if (position == null || position.level() == null || chunk == null) return false;
            if (inventory.destroyed || inventory.getHolder() != holder || inventory.getSize() != size
                    || inventory.getMaxStackSize() != limit || !position.matches()) return false;
            if (holder instanceof BlockEntity block && block.chunk != chunk) return false;
            if (holder instanceof Entity entity && entity.chunk != chunk) return false;
            if (holder instanceof BlockEntityChest chest) return chest.getRealInventory() == inventory
                    && ((ChestInventory) inventory).getDoubleInventory() == paired
                    && chest.namedTag.getInt("pairx") == pairX && chest.namedTag.getInt("pairz") == pairZ
                    && chest.isPaired() == hasPair;
            return holder != null && holder.getInventory() == inventory;
        }
    }
}
