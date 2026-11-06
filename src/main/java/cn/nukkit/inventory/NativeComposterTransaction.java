package cn.nukkit.inventory;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.block.ComposterEmptyEvent;
import cn.nukkit.event.block.ComposterFillEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemDye;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.utils.DyeColor;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.BooleanSupplier;

/** Native hopper/composter conversion: events precede exact inventory and world acceptance. */
public final class NativeComposterTransaction {
    private static final ThreadLocal<Set<Cell>> COMMITTING = new ThreadLocal<>();
    private NativeComposterTransaction() { }

    public static boolean fill(InventoryHolder hopper, Inventory inventory, int slot,
                               BlockComposter composter, BooleanSupplier routeIntact) {
        if (slot < 0 || slot >= inventory.getSize() || composter.isFull()) return false;
        Item original = inventory.getItem(slot);
        if (original.isNull() || original.getCount() <= 0) return false;
        Item offered = original.clone(); offered.setCount(1);
        int chance = BlockComposter.getChance(offered);
        if (chance <= 0 || original.getCount() - 1 > Math.min(original.getMaxStackSize(), inventory.getMaxStackSize())) return false;
        Context context = capture(hopper, inventory, composter, routeIntact);
        if (context == null) return false;
        ComposterFillEvent event = new ComposterFillEvent(composter, null, offered, chance,
                ThreadLocalRandom.current().nextInt(100) < chance);
        composter.level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || !context.intact()) return false;
        Item debit = original.clone(); debit.setCount(original.getCount() - 1);
        int nextLevel = context.cell().damage() + (event.isSuccess() ? 1 : 0);
        Sound sound = !event.isSuccess() ? Sound.BLOCK_COMPOSTER_FILL
                : nextLevel == 8 ? Sound.BLOCK_COMPOSTER_READY : Sound.BLOCK_COMPOSTER_FILL_SUCCESS;
        return commit(context, slot, debit, nextLevel, sound);
    }

    public static boolean pull(InventoryHolder hopper, Inventory inventory,
                               BlockComposter composter, BooleanSupplier routeIntact) {
        if (!composter.isFull()) return false;
        Context context = capture(hopper, inventory, composter, routeIntact);
        if (context == null) return false;
        ComposterEmptyEvent event = new ComposterEmptyEvent(composter, null, null,
                new ItemDye(DyeColor.WHITE), 0);
        composter.level.getServer().getPluginManager().callEvent(event);
        if (event.isCancelled() || !context.intact()) return false;
        Item offered = event.getDrop();
        if (offered == null || offered.isNull()) return false;
        Item unit = offered.clone(); unit.setCount(1);
        int slot = insertion(context.inventory(), unit);
        if (slot < 0) return false;
        InventoryMoveItemEvent move = new InventoryMoveItemEvent(null, inventory, hopper, offered,
                InventoryMoveItemEvent.Action.PICKUP);
        move.call();
        // This legacy pickup event is a cancellation gate; the empty event authors the output.
        if (move.isCancelled() || !context.intact()) return false;
        slot = insertion(context.inventory(), unit);
        if (slot < 0) return false;
        Item existing = inventory.getItem(slot);
        Item credit = existing.isNull() ? unit.clone() : existing.clone();
        if (!existing.isNull()) credit.setCount(existing.getCount() + 1);
        return commit(context, slot, credit, event.getNewLevel(), Sound.BLOCK_COMPOSTER_EMPTY);
    }

    private static Context capture(InventoryHolder hopper, Inventory inventory,
                                   BlockComposter composter, BooleanSupplier routeIntact) {
        if (composter.getDamage() < 0 || composter.getDamage() > 8) return null;
        if (!(inventory instanceof BaseInventory nativeInventory) || !(hopper instanceof Position actor)
                || inventory.getHolder() != hopper || actor.level != composter.level
                || composter.layer != 0 || composter.level.getBlockEntity(composter) != null
                || NativeContainerTransaction.isActive(inventory)
                || !InventoryMutationGuard.canCaptureProfile(List.of(inventory))) return null;
        cn.nukkit.level.format.generic.BaseFullChunk chunk = composter.level.getChunkIfLoaded(
                composter.getFloorX() >> 4, composter.getFloorZ() >> 4);
        if (chunk == null || chunk.getProvider() == null || chunk.getProvider().getLevel() != composter.level) return null;
        Cell cell = new Cell(composter.level, composter.getFloorX(), composter.getFloorY(),
                composter.getFloorZ(), composter.layer, composter.getDamage(), chunk);
        Set<Cell> active = COMMITTING.get();
        if (active != null && active.contains(cell)) return null;
        NativeContainerTransaction.Snapshot snapshot = NativeContainerTransaction.snapshot(inventory);
        if (snapshot == null) return null;
        FullChunk actorChunk = actor instanceof BlockEntity tile ? tile.chunk
                : actor instanceof Entity entity ? entity.chunk : null;
        if (actorChunk == null) return null;
        Context context = new Context(nativeInventory, snapshot, cell, actor, actorChunk, actor.level,
                actor.x, actor.y, actor.z, routeIntact);
        return context.intact() ? context : null;
    }

    @SuppressWarnings("try")
    private static boolean commit(Context context, int slot, Item item, int nextLevel, Sound sound) {
        if (!context.intact()) return false;
        NativeContainerTransaction.Preparation preparation = NativeContainerTransaction.tryPrepare(context.inventory());
        if (preparation == null) return false;
        Set<Cell> previous = COMMITTING.get();
        Set<Cell> active = new HashSet<>();
        if (previous != null) active.addAll(previous);
        active.add(context.cell()); COMMITTING.set(active);
        try (preparation; InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(context.inventory()))) {
            if (claim == null || !context.intact()) return false;
            Map<Integer, Item> expected = context.snapshot().rawSlots().get(context.inventory());
            if (item.isNull()) expected.remove(slot); else expected.put(slot, item.clone());
            boolean[] accepted = {false};
            try (InventoryMutationGuard.WriteScope blocked = claim.blockWrites()) {
                try {
                    try (InventoryMutationGuard.WriteScope write = claim.expectWrite(context.inventory(), slot, item)) {
                        if (!context.inventory().setItem(slot, item, false)) {
                            context.snapshot().restore(claim); return false;
                        }
                    }
                    if (!matches(context.inventory(), expected) || !context.snapshot().matchesContext()
                            || !context.routeMatches() || !context.cell().matches()
                            || context.cell().level().getBlockEntity(context.cell().block(context.cell().damage())) != null) {
                        context.snapshot().restore(claim); return false;
                    }
                    if (nextLevel == context.cell().damage()) {
                        accepted[0] = true;
                    } else {
                        BlockComposter changed = context.cell().block(nextLevel);
                        context.cell().level().setBlock(changed, changed, true, true, () -> accepted[0] = true);
                    }
                    if (!accepted[0]) { context.snapshot().restore(claim); return false; }
                } catch (RuntimeException | Error failure) {
                    // Native setBlock publishes the chunk state before world callbacks. Never
                    // resurrect inventory funding after an observable accepted world transition.
                    if (!accepted[0]) context.snapshot().restore(claim);
                    throw failure;
                }
            }
            // Sound/viewer failures follow acceptance and cannot refund the conversion.
            Throwable failure = null;
            try { context.cell().level().addSound(context.cell().block(nextLevel).add(0.5, 0.5, 0.5), sound); }
            catch (RuntimeException | Error next) { failure = next; }
            try { context.inventory().sendSlot(slot, context.inventory().getViewers()); }
            catch (RuntimeException | Error next) {
                if (failure == null) failure = next;
                else if (failure != next) {
                    if (next instanceof Error && !(failure instanceof Error)) { next.addSuppressed(failure); failure = next; }
                    else failure.addSuppressed(next);
                }
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
            return true;
        } finally {
            if (previous == null) COMMITTING.remove(); else COMMITTING.set(previous);
        }
    }

    private static int insertion(BaseInventory inventory, Item unit) {
        int limit = Math.min(unit.getMaxStackSize(), inventory.getMaxStackSize());
        if (limit < 1) return -1;
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            Item current = inventory.getItem(slot);
            if (!current.isNull() && current.equals(unit, true, true) && current.getCount() < limit) return slot;
        }
        for (int slot = 0; slot < inventory.getSize(); slot++) if (inventory.getItem(slot).isNull()) return slot;
        return -1;
    }
    private static boolean matches(BaseInventory inventory, Map<Integer, Item> expected) {
        if (inventory.slots.size() != expected.size()) return false;
        for (var entry : expected.entrySet()) {
            Item actual = inventory.slots.get(entry.getKey());
            if (actual == null || !actual.equalsExact(entry.getValue())) return false;
        }
        return true;
    }
    private record Context(BaseInventory inventory, NativeContainerTransaction.Snapshot snapshot, Cell cell,
                           Position actor, FullChunk actorChunk, Level actorLevel, double x, double y, double z, BooleanSupplier route) {
        boolean routeMatches() {
            return actor.level == actorLevel && actor.x == x && actor.y == y && actor.z == z
                    && worldOwnsActor() && route.getAsBoolean();
        }
        private boolean worldOwnsActor() {
            if (actorLevel.getChunkIfLoaded(actor.getFloorX() >> 4, actor.getFloorZ() >> 4) != actorChunk
                    || actorChunk.getProvider() == null || actorChunk.getProvider().getLevel() != actorLevel) return false;
            if (actor instanceof BlockEntity tile) {
                return !tile.closed && tile.chunk == actorChunk
                        && actorChunk.getBlockEntities().get(tile.getId()) == tile
                        && actorLevel.getBlockEntityIfLoaded(tile) == tile;
            }
            if (actor instanceof Entity entity) {
                return !entity.closed && entity.isAlive() && entity.chunk == actorChunk
                        && actorChunk.getEntities().get(entity.getId()) == entity
                        && actorLevel.getEntity(entity.getId()) == entity;
            }
            return false;
        }
        boolean intact() { return snapshot.matches() && routeMatches() && cell.matches()
                && cell.level().getBlockEntity(cell.block(cell.damage())) == null; }
    }
    private record Cell(Level level, int x, int y, int z, int layer, int damage,
                        cn.nukkit.level.format.generic.BaseFullChunk chunk) {
        BlockComposter block(int nextLevel) {
            BlockComposter block = new BlockComposter(nextLevel);
            block.level = level; block.x = x; block.y = y; block.z = z; block.layer = layer;
            return block;
        }
        boolean matches() {
            if (level.getChunkIfLoaded(x >> 4, z >> 4) != chunk || chunk.getProvider() == null
                    || chunk.getProvider().getLevel() != level) return false;
            Block wanted = block(damage);
            Block actual = layer == 0 ? level.getBlock(wanted) : level.getBlock(wanted, layer);
            return actual != null && actual.getId() == Block.COMPOSTER && actual.getDamage() == damage;
        }
        // Cell ownership spans every hopper at these coordinates, independent of its current level.
        @Override public boolean equals(Object other) {
            return other instanceof Cell cell && cell.level == level && cell.x == x && cell.y == y
                    && cell.z == z && cell.layer == layer;
        }
        @Override public int hashCode() { return System.identityHashCode(level) * 31 + x * 17 + y * 13 + z * 7 + layer; }
    }
}
