package cn.nukkit.inventory.transaction;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.BundleInventory;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.transaction.action.CraftingTakeResultAction;
import cn.nukkit.inventory.transaction.action.CraftingTransferMaterialAction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.GrindstoneItemAction;
import cn.nukkit.inventory.transaction.action.RepairItemAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/** Checked native recipe writes: callbacks precede the commit, notifications follow it. */
final class NativeRecipeTransaction {
    private NativeRecipeTransaction() { }

    /** Reject extension actions before their isValid hook can run outside this native contract. */
    static boolean supports(InventoryTransaction transaction) {
        Player actor = transaction.source;
        if (actor == null || !actor.getServer().isPrimaryThread() || !live(actor)) return false;
        boolean crafting = transaction.getClass() == CraftingTransaction.class;
        boolean repair = transaction.getClass() == RepairItemTransaction.class;
        boolean grinding = transaction.getClass() == GrindstoneTransaction.class;
        if (!crafting && !repair && !grinding) return false;
        for (InventoryAction action : transaction.actions) {
            Class<?> type = action.getClass();
            if (type != SlotChangeAction.class && !(crafting
                    && (type == CraftingTakeResultAction.class || type == CraftingTransferMaterialAction.class)
                    || repair && type == RepairItemAction.class
                    || grinding && type == GrindstoneItemAction.class)) return false;
        }
        return true;
    }

    @SuppressWarnings("try") // Scopes enforce native setters through the existing guard.
    static boolean execute(InventoryTransaction transaction, Item originalOutput, Supplier<Item> output,
                           BooleanSupplier executeEvent, BooleanSupplier recipeIntact, Runnable committedEffects) {
        Player actor = transaction.source;
        if (!actor.getServer().isPrimaryThread() || transaction.hasExecuted || !live(actor)) return false;
        List<InventoryAction> actions = List.copyOf(transaction.actions);
        Set<Inventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        inventories.addAll(transaction.inventories);
        boolean crafting = transaction.getClass() == CraftingTransaction.class;
        boolean repair = transaction.getClass() == RepairItemTransaction.class;
        boolean grinding = transaction.getClass() == GrindstoneTransaction.class;
        if (!crafting && !repair && !grinding) return false;
        List<SlotChangeAction> writes = new ArrayList<>();
        Map<InventoryAction, Payload> initial = new IdentityHashMap<>();
        for (InventoryAction action : actions) {
            Class<?> type = action.getClass();
            if (type == SlotChangeAction.class) writes.add((SlotChangeAction) action);
            else if (!(crafting && (type == CraftingTakeResultAction.class || type == CraftingTransferMaterialAction.class)
                    || repair && type == RepairItemAction.class
                    || grinding && type == GrindstoneItemAction.class)) return false;
            initial.put(action, new Payload(action.getSourceItem(), action.getTargetItem()));
        }
        if (writes.isEmpty()) return false;
        List<Inventory> captured = new ArrayList<>(inventories);
        for (Inventory inventory : Arrays.asList(actor.getInventory(), actor.getOffhandInventory(),
                actor.getEnderChestInventory(), actor.getUIInventory())) {
            if (inventory != null) captured.add(inventory);
        }
        ActorContext context = new ActorContext(actor, repair ? Player.ANVIL_WINDOW_ID
                : grinding ? Player.GRINDSTONE_WINDOW_ID : -1);
        if (context.window() != null) captured.add(context.window());
        Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
        boolean committed = false;
        Throwable failure = null;
        try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(captured)) {
            if (claim == null) return false;
            for (Inventory inventory : captured) {
                if (!capture(inventory, before)) return false;
            }
            InventoryMutationGuard.NativeMetadataSnapshot metadata = claim.captureNativeMetadata();
            try (InventoryMutationGuard.WriteScope blocked = claim.blockWrites()) {
                try {
                    if (!membership(transaction, actions, inventories) || !context.matches(transaction)
                            || !recipeIntact.getAsBoolean() || !payloads(actions, initial)
                            || !matches(before) || !metadata.matches()) return rollback(before, metadata);
                    boolean acceptedEvent = executeEvent.getAsBoolean();
                    Item approved = output.get();
                    if (!acceptedEvent || approved == null || !sameVariantAndCount(originalOutput, approved)
                            || !recipeIntact.getAsBoolean() || !membership(transaction, actions, inventories)
                            || !context.matches(transaction) || !matches(before) || !metadata.matches()) {
                        return rollback(before, metadata);
                    }
                    Map<InventoryAction, Payload> approvedPayloads = approvePayloads(actions, initial, originalOutput, approved);
                    if (approvedPayloads == null || !transaction.matchItems(false, false) || transaction.invalid) {
                        return rollback(before, metadata);
                    }
                    Map<BaseInventory, Map<Integer, Item>> expected = copyBackings(before);
                    Set<PhysicalSlot> occupied = new java.util.HashSet<>();
                    for (SlotChangeAction write : writes) {
                        PhysicalSlot slot = physicalSlot(write.getInventory(), write.getSlot());
                        Item target = approvedPayloads.get(write).target();
                        if (slot == null || !expected.containsKey(slot.inventory()) || !occupied.add(slot)
                                || !claim.contains(write.getInventory()) || !fits(write.getInventory(), target)
                                || !InventoryMutationGuard.matchesTarget(write.getInventory().getItem(write.getSlot()),
                                approvedPayloads.get(write).source())) return rollback(before, metadata);
                        if (target.isNull()) expected.get(slot.inventory()).remove(slot.index());
                        else expected.get(slot.inventory()).put(slot.index(), target.clone());
                    }
                    if (grinding && !grindingPlan(before, expected, context.window(), approved)) return rollback(before, metadata);
                    for (InventoryAction action : actions) {
                        if (!action.onPreExecute(actor) || !context.matches(transaction) || !metadata.matches()
                                || !matches(before) || !recipeIntact.getAsBoolean()
                                || !membership(transaction, actions, inventories) || !payloads(actions, approvedPayloads)) {
                            return rollback(before, metadata);
                        }
                    }
                    // No virtual action can mutate physical inventory or publish an independent effect.
                    for (SlotChangeAction write : writes) {
                        Item target = approvedPayloads.get(write).target();
                        try (InventoryMutationGuard.WriteScope scope = claim.expectWrite(write.getInventory(), write.getSlot(), target)) {
                            if (!write.executeInventoryWrite(actor)
                                    || !InventoryMutationGuard.matchesTarget(write.getInventory().getItem(write.getSlot()), target)) {
                                return rollback(before, metadata);
                            }
                        }
                    }
                    // The last setter can restore an earlier slot, create a hidden raw slot, or resize a backing.
                    if (!matches(expected) || !metadata.matches() || !context.matches(transaction)
                            || !recipeIntact.getAsBoolean() || !membership(transaction, actions, inventories)
                            || !payloads(actions, approvedPayloads)) return rollback(before, metadata);
                    transaction.hasExecuted = true;
                    committed = true;
                } catch (RuntimeException | Error writeFailure) {
                    if (!committed) {
                        try { rollback(before, metadata); } catch (RuntimeException | Error cleanup) {
                            if (writeFailure != cleanup) writeFailure.addSuppressed(cleanup);
                        }
                    }
                    throw writeFailure;
                }
                // Charge the accepted recipe before releasing its lease can drain a native close callback.
                // hasExecuted already marks the inventory commit: effect errors cannot refund it.
                try { committedEffects.run(); } catch (RuntimeException | Error notification) { failure = notification; }
            }
        }
        // Commit is irreversible here. Later notification failures never refund accepted inventory.
        for (SlotChangeAction write : writes) {
            try { write.onInventoryWriteCommitted(actor); } catch (RuntimeException | Error notification) {
                failure = accumulate(failure, notification);
            }
        }
        for (InventoryAction action : actions) {
            try { action.onExecuteSuccess(actor); } catch (RuntimeException | Error notification) {
                failure = accumulate(failure, notification);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
        return true;
    }

    private static Throwable accumulate(Throwable original, Throwable next) {
        if (original == null) return next;
        if (original != next) original.addSuppressed(next);
        return original;
    }

    private static boolean live(Player actor) {
        return !actor.closed && actor.spawned && actor.isOnline() && actor.isAlive() && !actor.isSpectator();
    }

    private static boolean fits(Inventory inventory, Item target) {
        return target.isNull() || target.getCount() > 0
                && target.getCount() <= Math.min(inventory.getMaxStackSize(), target.getMaxStackSize());
    }

    private static boolean sameVariantAndCount(Item original, Item approved) {
        return original != null && !approved.isNull() && original.equals(approved, true, false)
                && original.getCount() == approved.getCount();
    }

    /** Virtual recipe rows cannot substitute for consuming the two physical grindstone inputs. */
    private static boolean grindingPlan(Map<BaseInventory, Map<Integer, Item>> before,
            Map<BaseInventory, Map<Integer, Item>> expected, Inventory station, Item output) {
        if (station == null) return false;
        PhysicalSlot equipment = physicalSlot(station, 0), ingredient = physicalSlot(station, 1);
        if (equipment == null || ingredient == null) return false;
        long produced = 0;
        for (var backing : before.entrySet()) {
            Map<Integer, Item> after = expected.get(backing.getKey());
            Set<Integer> slots = new java.util.HashSet<>(backing.getValue().keySet()); slots.addAll(after.keySet());
            for (int index : slots) {
                Item from = backing.getValue().get(index), to = after.get(index);
                PhysicalSlot slot = new PhysicalSlot(backing.getKey(), index);
                if (slot.equals(equipment) || slot.equals(ingredient)) {
                    if (to != null && !to.isNull()) return false;
                    continue;
                }
                if (from != null && to != null && InventoryMutationGuard.matchesTarget(from, to)) continue;
                int oldCount = from == null || from.isNull() ? 0 : from.getCount();
                int newCount = to == null || to.isNull() ? 0 : to.getCount();
                if (newCount < oldCount || from != null && !from.isNull() && !from.equals(output, true, true)
                        || to != null && !to.isNull() && !to.equals(output, true, true)) return false;
                produced += (long) newCount - oldCount;
            }
        }
        return produced == output.getCount();
    }

    private static Map<InventoryAction, Payload> approvePayloads(List<InventoryAction> actions,
            Map<InventoryAction, Payload> initial, Item original, Item approved) {
        Map<InventoryAction, Payload> result = new IdentityHashMap<>();
        for (InventoryAction action : actions) {
            Payload payload = initial.get(action);
            Item from = payload.source().equalsExact(original) ? approved : payload.source();
            Item to = payload.target().equalsExact(original) ? approved : payload.target();
            if (!action.getSourceItem().equalsExact(from) || !action.getTargetItem().equalsExact(to)) return null;
            result.put(action, new Payload(from.clone(), to.clone()));
        }
        return result;
    }

    private static boolean membership(InventoryTransaction transaction, List<InventoryAction> actions, Set<Inventory> inventories) {
        if (transaction.invalid || transaction.actions.size() != actions.size()
                || transaction.inventories.size() != inventories.size() || !inventories.containsAll(transaction.inventories)) return false;
        for (int i = 0; i < actions.size(); i++) if (transaction.actions.get(i) != actions.get(i)) return false;
        return true;
    }

    private static boolean payloads(List<InventoryAction> actions, Map<InventoryAction, Payload> payloads) {
        for (InventoryAction action : actions) {
            Payload payload = payloads.get(action);
            if (!action.getSourceItem().equalsExact(payload.source()) || !action.getTargetItem().equalsExact(payload.target())) return false;
        }
        return true;
    }

    private static boolean capture(Inventory inventory, Map<BaseInventory, Map<Integer, Item>> before) {
        if (inventory instanceof DoubleChestInventory chest) return capture(chest.getLeftSide(), before) && capture(chest.getRightSide(), before);
        if (inventory instanceof PlayerUIComponent component) return capture(component.getBackingInventory(), before);
        if (!(inventory instanceof BaseInventory nativeInventory) || inventory instanceof BundleInventory) return false;
        before.computeIfAbsent(nativeInventory, key -> copySlots(key.slots));
        return true;
    }

    private static PhysicalSlot physicalSlot(Inventory inventory, int slot) {
        if (inventory instanceof DoubleChestInventory chest) {
            int leftSize = chest.getLeftSide().getSize();
            return physicalSlot(slot < leftSize ? chest.getLeftSide() : chest.getRightSide(), slot < leftSize ? slot : slot - leftSize);
        }
        if (inventory instanceof PlayerUIComponent component) return physicalSlot(component.getBackingInventory(), component.getBackingSlot(slot));
        return inventory instanceof BaseInventory nativeInventory ? new PhysicalSlot(nativeInventory, slot) : null;
    }

    private static Map<Integer, Item> copySlots(Map<Integer, Item> slots) {
        Map<Integer, Item> copy = new HashMap<>();
        slots.forEach((slot, item) -> copy.put(slot, item.clone()));
        return copy;
    }

    private static Map<BaseInventory, Map<Integer, Item>> copyBackings(Map<BaseInventory, Map<Integer, Item>> before) {
        Map<BaseInventory, Map<Integer, Item>> copy = new IdentityHashMap<>();
        before.forEach((inventory, slots) -> copy.put(inventory, copySlots(slots)));
        return copy;
    }

    private static boolean matches(Map<BaseInventory, Map<Integer, Item>> expected) {
        for (var entry : expected.entrySet()) {
            if (entry.getKey().slots.size() != entry.getValue().size()) return false;
            for (var slot : entry.getValue().entrySet()) {
                Item item = entry.getKey().slots.get(slot.getKey());
                if (item == null || !InventoryMutationGuard.matchesTarget(item, slot.getValue())) return false;
            }
        }
        return true;
    }

    private static boolean rollback(Map<BaseInventory, Map<Integer, Item>> before, InventoryMutationGuard.NativeMetadataSnapshot metadata) {
        metadata.restore();
        before.forEach((inventory, slots) -> {
            inventory.slots.clear();
            inventory.slots.putAll(copySlots(slots));
        });
        return false;
    }

    private record Payload(Item source, Item target) { }
    private record PhysicalSlot(BaseInventory inventory, int index) { }

    private record ActorContext(Player actor, Level level, GameVersion version, int protocol, int mode,
                                Inventory main, Inventory offhand, Inventory ender, Inventory ui, Inventory crafting,
                                int windowId, Inventory window, int craftingType, int experience, int experienceLevel) {
        private ActorContext(Player actor, int windowId) {
            this(actor, actor.getLevel(), actor.getGameVersion(), actor.protocol, actor.getGamemode(),
                    actor.getInventory(), actor.getOffhandInventory(), actor.getEnderChestInventory(), actor.getUIInventory(),
                    actor.getCraftingGrid(), windowId, windowId < 0 ? null : actor.getWindowById(windowId),
                    actor.craftingType, actor.getExperience(), actor.getExperienceLevel());
        }
        private boolean matches(InventoryTransaction transaction) {
            return transaction.source == actor && live(actor) && actor.getServer().isPrimaryThread()
                    && actor.getLevel() == level && actor.getGameVersion() == version && actor.protocol == protocol
                    && actor.getGamemode() == mode && actor.craftingType == craftingType
                    && actor.getInventory() == main && actor.getOffhandInventory() == offhand
                    && actor.getEnderChestInventory() == ender && actor.getUIInventory() == ui && actor.getCraftingGrid() == crafting
                    && (window == null || actor.getWindowById(windowId) == window)
                    && actor.getExperience() == experience && actor.getExperienceLevel() == experienceLevel;
        }
    }
}
