package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import org.jetbrains.annotations.ApiStatus;
import java.util.*;

/** Return authored UI inputs once; world overflow remains hidden until every source removal is accepted. */
@ApiStatus.Internal
public final class NativeUIItemReturn {
    private NativeUIItemReturn() { }

    private static final ClassValue<Boolean> NATIVE_PLAYER_HOOKS = new ClassValue<>() {
        @Override protected Boolean computeValue(Class<?> type) {
            try {
                return type.getMethod("resetCraftingGridType").getDeclaringClass() == Player.class
                        && type.getMethod("returnUiItems", Item[].class).getDeclaringClass() == Player.class
                        && type.getMethod("dropItem", Item.class).getDeclaringClass() == Player.class;
            } catch (NoSuchMethodException | SecurityException unsupported) {
                return false;
            }
        }
    };

    public static boolean isSupported(Player actor) {
        return NATIVE_PLAYER_HOOKS.get(actor.getClass()) && actor.getInventory() != null && actor.getInventory().getClass() == PlayerInventory.class
                && actor.getUIInventory() != null && actor.getUIInventory().getClass() == PlayerUIInventory.class
                && (actor.getLevel() == null || actor.getLevel().getClass() == Level.class);
    }

    public static boolean returnInputs(Player actor) {
        if (!isSupported(actor)) return false;
        if (actor.getUIInventory() != null && actor.getUIInventory().slots.isEmpty()) return true;
        return returnInputs(actor, () -> { });
    }

    /** Close notifications retain their mapped window until the same native lease is released. */
    static boolean returnInputs(Player actor, Runnable closeNotification) {
        return returnInputs(actor, closeNotification, null);
    }

    static boolean returnInputs(Player actor, Runnable closeNotification, PlayerUIComponent playerDropSources) {
        PlayerInventory main = actor.getInventory(); PlayerUIInventory ui = actor.getUIInventory();
        Thread owner = actor.getServer().getPrimaryThread();
        if (!isSupported(actor) || actor.closed || main == null || ui == null || !actor.getServer().isPrimaryThread()
                && (owner == null || owner.getState() != Thread.State.TERMINATED || !InventoryMutationGuard.isIdle())) return false;
        try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaimProfile(
                Arrays.asList(main, ui, actor.getOffhandInventory(), actor.getEnderChestInventory()))) {
            if (claim == null || !returnInputs(actor, claim, playerDropSources)) return false;
            closeNotification.run();
            return true;
        }
    }

    /** Reuse a native workstation close lease so its mapped close packet precedes deferred callbacks. */
    @SuppressWarnings("try")
    static boolean returnInputs(Player actor, InventoryMutationGuard.Lease claim) {
        return returnInputs(actor, claim, null);
    }

    @SuppressWarnings("try")
    static boolean returnInputs(Player actor, InventoryMutationGuard.Lease claim, PlayerUIComponent playerDropSources) {
        PlayerInventory main = actor.getInventory(); PlayerUIInventory ui = actor.getUIInventory();
        if (actor.closed || main == null || ui == null) return false;
        if (ui.slots.isEmpty()) return true;
        List<Inventory> captured = Arrays.asList(main, ui, actor.getOffhandInventory(), actor.getEnderChestInventory());
        for (Inventory inventory : captured) if (inventory != null && !claim.contains(inventory)) return false;
        Context context = new Context(actor);
        List<EntityItem.PreparedDrop> tickets = new ArrayList<>();
        Map<BaseInventory, Map<Integer, Item>> before = new IdentityHashMap<>();
        boolean committed = false;
        boolean endsItemUse = false;
        {
            for (Inventory inventory : captured) {
                if (inventory == null) continue;
                if (!(inventory instanceof BaseInventory backing)) return false;
                before.put(backing, copy(backing.slots));
            }
            var metadata = claim.captureNativeMetadata();
            Map<BaseInventory, Map<Integer, Item>> expected = new IdentityHashMap<>();
            before.forEach((inventory, slots) -> expected.put(inventory, copy(slots)));
            List<Write> writes = new ArrayList<>(); List<Item> overflow = new ArrayList<>();
            Map<Integer, Item> expectedMain = expected.get(main);
            for (int source : new TreeSet<>(before.get(ui).keySet())) {
                Item item = before.get(ui).get(source);
                expected.get(ui).remove(source);
                writes.add(new Write(ui, source, Item.AIR_ITEM.clone()));
                // Native output previews and creative UI items have no paid input custody.
                if (!PlayerUIInventory.isPersistentSlot(source) || actor.isCreative() || item.isNull()) continue;
                int remaining = item.getCount();
                int limit = Math.min(item.getMaxStackSize(), main.getMaxStackSize());
                if (remaining <= 0 || remaining > item.getMaxStackSize() || limit <= 0) return false;
                for (int pass = 0; pass < 2; pass++) {
                    for (int slot = 0; slot < main.getSize() && remaining > 0; slot++) {
                        Item current = expectedMain.get(slot);
                        boolean empty = current == null || current.isNull();
                        if (pass == 0 && empty || pass == 1 && !empty) continue;
                        if (!empty && !current.equals(item, true, true)) continue;
                        int count = empty ? 0 : current.getCount();
                        int accepted = Math.min(remaining, Math.max(0, limit - count));
                        if (accepted == 0) continue;
                        Item target = empty ? item.clone() : current.clone();
                        if (empty) target.autoAssignStackNetworkId();
                        target.setCount(count + accepted);
                        expectedMain.put(slot, target); remaining -= accepted;
                    }
                }
                if (remaining > 0) {
                    Item drop = item.clone(); drop.setCount(remaining); overflow.add(drop);
                    if (playerDropSources != null && source >= playerDropSources.getBackingSlot(0)
                            && source < playerDropSources.getBackingSlot(playerDropSources.getSize())) endsItemUse = true;
                }
            }
            for (int slot = 0; slot < main.getSize(); slot++) {
                Item from = before.get(main).get(slot), target = expectedMain.get(slot);
                if (target != null && (from == null || !InventoryMutationGuard.matchesTarget(from, target)))
                    writes.add(new Write(main, slot, target.clone()));
            }
            try (var blocked = claim.blockWrites()) {
                try {
                    for (Item drop : overflow) {
                        if (context.level() == null) return refuse(tickets, before, metadata);
                        Vector3 direction = actor.getDirectionVector();
                        EntityItem.PreparedDrop ticket = context.level().prepareItemDrop(actor.add(0, 1.3, 0),
                                drop.clone(), direction == null ? new Vector3(0, 0.2, 0) : direction.multiply(0.4), 40);
                        if (ticket == null) return refuse(tickets, before, metadata);
                        tickets.add(ticket);
                    }
                    if (!context.matches(actor) || !metadata.matches() || !matches(before)
                            || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) return refuse(tickets, before, metadata);
                    for (Write write : writes) {
                        try (var scope = claim.expectWrite(write.inventory(), write.slot(), write.target())) {
                            if (!write.inventory().setItem(write.slot(), write.target().clone(), false)
                                    || !InventoryMutationGuard.matchesTarget(write.inventory().getItem(write.slot()), write.target()))
                                return refuse(tickets, before, metadata);
                        }
                    }
                    if (!context.matches(actor) || !metadata.matches() || !matches(expected)
                            || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) return refuse(tickets, before, metadata);
                    committed = true;
                    for (EntityItem.PreparedDrop ticket : tickets) ticket.release();
                } catch (RuntimeException | Error failure) {
                    if (!committed) {
                        try { refuse(tickets, before, metadata); } catch (RuntimeException | Error cleanup) {
                            if (failure != cleanup) failure.addSuppressed(cleanup);
                        }
                    }
                    throw failure;
                }
            }
        }
        Throwable failure = null;
        if (endsItemUse && actor.spawned && actor.isAlive()) {
            try { actor.setUsingItem(false); } catch (RuntimeException | Error notification) { failure = accumulate(failure, notification); }
        }
        for (EntityItem.PreparedDrop ticket : tickets) {
            try { ticket.notifyPublished(); } catch (RuntimeException | Error notification) { failure = accumulate(failure, notification); }
        }
        // Notify before the caller's lease can complete a deferred close and retire this actor.
        if (!actor.closed) {
            try { main.sendContents(actor); } catch (RuntimeException | Error notification) { failure = accumulate(failure, notification); }
            for (int slot : before.get(ui).keySet()) {
                try { ui.sendSlot(slot, actor); } catch (RuntimeException | Error notification) { failure = accumulate(failure, notification); }
            }
        }
        rethrow(failure);
        return true;
    }

    private static boolean refuse(List<EntityItem.PreparedDrop> tickets, Map<BaseInventory, Map<Integer, Item>> before,
                                  InventoryMutationGuard.NativeMetadataSnapshot metadata) {
        Throwable failure = null;
        for (EntityItem.PreparedDrop ticket : tickets) {
            try { ticket.discard(); } catch (RuntimeException | Error cleanup) { failure = accumulate(failure, cleanup); }
        }
        metadata.restore();
        Map<BaseInventory, Map<Integer, Item>> changed = new IdentityHashMap<>();
        before.forEach((inventory, snapshot) -> {
            Set<Integer> slots = new HashSet<>(snapshot.keySet()); slots.addAll(inventory.slots.keySet());
            Map<Integer, Item> previous = new HashMap<>();
            for (int slot : slots) {
                Item current = inventory.slots.getOrDefault(slot, Item.AIR_ITEM);
                if (!current.equalsExact(snapshot.getOrDefault(slot, Item.AIR_ITEM))) previous.put(slot, current.clone());
            }
            changed.put(inventory, previous);
            inventory.slots.clear(); inventory.slots.putAll(copy(snapshot));
        });
        for (var inventory : changed.entrySet()) {
            for (var slot : inventory.getValue().entrySet()) {
                try { inventory.getKey().onSlotChange(slot.getKey(), slot.getValue(), true); }
                catch (RuntimeException | Error notification) { failure = accumulate(failure, notification); }
            }
        }
        rethrow(failure);
        return false;
    }
    private static Map<Integer, Item> copy(Map<Integer, Item> slots) {
        Map<Integer, Item> copy = new HashMap<>(); slots.forEach((slot, item) -> copy.put(slot, item.clone())); return copy;
    }
    private static boolean matches(Map<BaseInventory, Map<Integer, Item>> expected) {
        for (var entry : expected.entrySet()) {
            if (entry.getKey().slots.size() != entry.getValue().size()) return false;
            for (var slot : entry.getValue().entrySet()) {
                Item current = entry.getKey().slots.get(slot.getKey());
                if (current == null || !InventoryMutationGuard.matchesTarget(current, slot.getValue())) return false;
            }
        }
        return true;
    }
    private static Throwable accumulate(Throwable original, Throwable next) {
        if (original == null) return next; if (original != next) original.addSuppressed(next); return original;
    }
    private static void rethrow(Throwable failure) {
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }
    private record Write(BaseInventory inventory, int slot, Item target) { }
    private record Context(Level level, GameVersion version, int protocol, int mode, Inventory main, Inventory ui,
                           Inventory offhand, Inventory ender, double x, double y, double z) {
        private Context(Player actor) { this(actor.getLevel(), actor.getGameVersion(), actor.protocol, actor.getGamemode(),
                actor.getInventory(), actor.getUIInventory(), actor.getOffhandInventory(), actor.getEnderChestInventory(), actor.x, actor.y, actor.z); }
        private boolean matches(Player actor) { return !actor.closed && actor.getLevel() == level
                && actor.getGameVersion() == version && actor.protocol == protocol && actor.getGamemode() == mode
                && actor.getInventory() == main && actor.getUIInventory() == ui && actor.getOffhandInventory() == offhand
                && actor.getEnderChestInventory() == ender && actor.x == x && actor.y == y && actor.z == z; }
    }
}
