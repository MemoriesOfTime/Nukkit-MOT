package cn.nukkit.inventory;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.level.Level;
import cn.nukkit.level.Sound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Publishes bundle contents only after the canonical carrier accepts their removal. */
public final class NativeBundleOutput {
    private static final Set<Player> ACTIVE = Collections.newSetFromMap(new IdentityHashMap<>());
    private NativeBundleOutput() { }

    @SuppressWarnings("try")
    public static boolean output(ItemBundle caller, Player actor) {
        if (!actor.getServer().isPrimaryThread() || !actor.spawned || !actor.isAlive()
                || actor.closed || actor.getLevel() == null || actor.getDirectionVector() == null) return false;
        if (!ACTIVE.add(actor)) return false;
        EntityItem.PreparedDrop ticket = null;
        boolean committed = false;
        try {
            PlayerInventory main = actor.getInventory();
            Context context = new Context(actor);
            int held = main.getHeldItemIndex();
            Item canonical = main.getItemInHandFast();
            if (!(canonical instanceof ItemBundle carrier) || carrier.getCount() != 1
                    || !caller.matchesBundleIdentity(carrier.getBundleId())
                    || !InventoryMutationGuard.matchesTarget(caller, carrier)) return false;
            Item before = carrier.clone();
            ItemBundle after = carrier.clone();
            var entry = after.getInventory().getContents().entrySet().stream()
                    .filter(e -> e.getValue() != null && !e.getValue().isNull())
                    .min(Map.Entry.comparingByKey()).orElse(null);
            if (entry == null || !after.getInventory().clear(entry.getKey(), false)) return false;
            Item drop = entry.getValue().clone();

            // Keep constructor callbacks independent. A legitimate carrier move wins;
            // the outer output then discards its still-private ticket without refunding that move.
            ticket = context.level().prepareItemDrop(actor.add(0, 1.3, 0), drop,
                    actor.getDirectionVector().multiply(0.4), 40);
            if (ticket == null || !ticket.isValid() || !context.matches(actor)
                    || !InventoryMutationGuard.matchesTarget(main.getItem(held), before)) return false;

            List<Inventory> inventories = new ArrayList<>();
            Set<Inventory> seen = Collections.newSetFromMap(new IdentityHashMap<>());
            collect(main, inventories, seen);
            collect(actor.getOffhandInventory(), inventories, seen);
            collect(actor.getEnderChestInventory(), inventories, seen);
            collect(actor.getUIInventory(), inventories, seen);
            try (InventoryMutationGuard.Lease lease = InventoryMutationGuard.tryClaim(inventories)) {
                if (lease == null) return false;
                Map<BaseInventory, Map<Integer, Item>> snapshots = new IdentityHashMap<>();
                for (Inventory inventory : inventories) {
                    if (inventory instanceof PlayerUIComponent component) inventory = component.getBackingInventory();
                    if (!(inventory instanceof BaseInventory backing)) return false;
                    snapshots.computeIfAbsent(backing, key -> copy(key.slots));
                }
                var metadata = lease.captureNativeMetadata();
                try (InventoryMutationGuard.WriteScope blocked = lease.blockWrites()) {
                    try {
                        try (InventoryMutationGuard.WriteScope write = lease.expectWrite(main, held, after)) {
                            if (!main.setItem(held, after.clone(), false)) {
                                restore(snapshots, metadata); return false;
                            }
                        }
                        Map<BaseInventory, Map<Integer, Item>> expected = new IdentityHashMap<>();
                        snapshots.forEach((inventory, slots) -> expected.put(inventory, copy(slots)));
                        expected.get(main).put(held, after);
                        if (!context.matches(actor) || !metadata.matches() || !matches(expected) || !ticket.isValid()) {
                            restore(snapshots, metadata); return false;
                        }
                        caller.setNamedTag(main.getItem(held).getNamedTag().clone());
                        committed = true;
                        ticket.release();
                    } catch (RuntimeException | Error failure) {
                        if (!committed) restore(snapshots, metadata);
                        throw failure;
                    }
                }
            }
            ticket.notifyPublished();
            if (context.matches(actor)) {
                actor.setUsingItem(false);
                main.sendSlot(held, actor);
                context.level().addSound(actor, Sound.BUNDLE_DROP_CONTENTS);
            }
            return context.matches(actor) && InventoryMutationGuard.matchesTarget(main.getItem(held), after);
        } finally {
            try { if (ticket != null && !committed) ticket.discard(); }
            finally { ACTIVE.remove(actor); }
        }
    }

    private static void collect(Inventory inventory, List<Inventory> inventories, Set<Inventory> seen) {
        if (inventory == null || !seen.add(inventory)) return;
        inventories.add(inventory);
        for (Item item : inventory.getContents().values()) {
            if (item instanceof ItemBundle bundle) collect(bundle.getInventory(), inventories, seen);
        }
    }
    private static Map<Integer, Item> copy(Map<Integer, Item> slots) {
        Map<Integer, Item> copy = new HashMap<>();
        slots.forEach((slot, item) -> copy.put(slot, item.clone())); return copy;
    }
    private static boolean matches(Map<BaseInventory, Map<Integer, Item>> expected) {
        for (var inventory : expected.entrySet()) {
            if (inventory.getKey().slots.size() != inventory.getValue().size()) return false;
            for (var entry : inventory.getValue().entrySet()) {
                Item actual = inventory.getKey().slots.get(entry.getKey());
                if (actual == null || !InventoryMutationGuard.matchesTarget(actual, entry.getValue())) return false;
            }
        }
        return true;
    }
    private static void restore(Map<BaseInventory, Map<Integer, Item>> snapshots,
                                InventoryMutationGuard.NativeMetadataSnapshot metadata) {
        metadata.restore();
        snapshots.forEach((inventory, slots) -> { inventory.slots.clear(); inventory.slots.putAll(copy(slots)); });
        snapshots.keySet().forEach(inventory -> {
            if (inventory instanceof BundleInventory bundle) bundle.getHolder().saveNBT();
        });
    }
    private record Context(Level level, GameVersion version, int protocol, int mode, PlayerInventory main,
                           Inventory offhand, Inventory ender, Inventory ui, int held) {
        Context(Player actor) { this(actor.getLevel(), actor.getGameVersion(), actor.protocol, actor.getGamemode(),
                actor.getInventory(), actor.getOffhandInventory(), actor.getEnderChestInventory(), actor.getUIInventory(),
                actor.getInventory().getHeldItemIndex()); }
        boolean matches(Player actor) { return !actor.closed && actor.spawned && actor.isAlive()
                && actor.getServer().isPrimaryThread() && actor.getLevel() == level && actor.getGameVersion() == version
                && actor.protocol == protocol && actor.getGamemode() == mode && actor.getInventory() == main
                && actor.getOffhandInventory() == offhand && actor.getEnderChestInventory() == ender
                && actor.getUIInventory() == ui && main.getHeldItemIndex() == held; }
    }
}
