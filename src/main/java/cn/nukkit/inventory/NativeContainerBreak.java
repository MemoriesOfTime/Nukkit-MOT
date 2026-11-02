package cn.nukkit.inventory;

import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

/** One container lifecycle settles exact physical contents before publishing native world goods. */
public final class NativeContainerBreak {
    private final Supplier<? extends Inventory> inventories;
    private final Runnable breakAction, closeAction;
    private final Object deferredLifecycle = new Object();
    private boolean pendingBreak, pendingClose, preparingContainerBreak, acceptedBreak;

    public NativeContainerBreak(Supplier<? extends Inventory> inventories, Runnable breakAction, Runnable closeAction) {
        this.inventories = inventories;
        this.breakAction = breakAction;
        this.closeAction = closeAction;
    }

    /** Lifecycle callbacks must observe the owner's final source, never a provisional debit. */
    public boolean deferLifecycle(boolean breaking) {
        Inventory inventory = inventories.get();
        if (inventory == null) return false;
        // A recursive break is part of this same decision, rather than another copy of its goods.
        if (breaking && preparingContainerBreak) return true;
        boolean deferred = InventoryMutationGuard.deferUntilReleased(List.of(inventory), deferredLifecycle, this::finishDeferredLifecycle)
                || NativeContainerTransaction.deferIfPreparing(inventory, deferredLifecycle, this::finishDeferredLifecycle);
        if (deferred) {
            pendingBreak |= breaking;
            pendingClose |= !breaking;
        }
        return deferred;
    }

    private void finishDeferredLifecycle() {
        boolean breaking = pendingBreak, closing = pendingClose;
        pendingBreak = false;
        pendingClose = false;
        // Native lease deferrals use an identity map, so distinct break/close entries would have
        // no ordering guarantee. One entry always settles goods before detaching their holder.
        Throwable failure = null;
        if (breaking) {
            try { breakAction.run(); } catch (RuntimeException | Error error) { failure = error; }
        }
        // An accepted break may fail while notifying viewers. Its terminal close still runs.
        // A refused/restored source stays reachable; neither a queued close nor Level's caller
        // may destroy the holder after that unsuccessful decision.
        if (closing && (acceptedBreak || !hasGoods(inventories.get())
                || failure == null && !preparingContainerBreak)) {
            try { closeAction.run(); } catch (RuntimeException | Error error) { failure = retainFailure(failure, error); }
        }
        if (failure instanceof Error error) throw error;
        if (failure instanceof RuntimeException exception) throw exception;
    }

    @SuppressWarnings("try")
    public void breakContents(Position holder, Runnable acceptedEffect) {
        Inventory inventory = inventories.get();
        if (deferLifecycle(true)) return;
        acceptedBreak = false;
        NativeContainerTransaction.Preparation preparation = NativeContainerTransaction.tryPrepare(inventory);
        if (preparation == null) { refuseRetainedSource(inventory); return; }
        List<EntityItem.PreparedDrop> tickets = new ArrayList<>();
        boolean accepted = false;
        Throwable failureInFlight = null;
        preparingContainerBreak = true;
        try (preparation) {
            NativeContainerTransaction.Snapshot source = NativeContainerTransaction.snapshot(inventory);
            if (source == null || holder.getLevel() == null || inventory.getHolder() != holder) { refuseRetainedSource(inventory); return; }
            for (var backing : source.rawSlots().values()) {
                for (Item content : backing.values()) {
                    if (content.isNull()) continue;
                    EntityItem.PreparedDrop ticket = prepareContainerDrop(holder, content);
                    if (ticket == null) { refuseRetainedSource(inventory); return; }
                    tickets.add(ticket);
                    // ItemSpawn callbacks may independently relocate the source. Preserve that
                    // accepted effect and refuse this stale plan; it has not written any source.
                    if (!source.matches() || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) {
                        refuseRetainedSource(inventory);
                        return;
                    }
                }
            }
            try (InventoryMutationGuard.Lease claim = InventoryMutationGuard.tryClaim(List.of(inventory))) {
                if (claim == null || !source.matches()
                        || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) {
                    refuseRetainedSource(inventory);
                    return;
                }
                if (!source.clear(claim) || !tickets.stream().allMatch(EntityItem.PreparedDrop::isValid)) {
                    source.restore(claim);
                    refuseRetainedSource(inventory);
                    return;
                }
                // Release is assignment-only. All source writes were accepted before any unit
                // becomes collectible; deferred lifecycle work runs only after this decision.
                for (EntityItem.PreparedDrop ticket : tickets) ticket.release();
                accepted = true;
                acceptedBreak = true;
            }
            Throwable failure = null;
            try { acceptedEffect.run(); } catch (RuntimeException | Error effect) { failure = effect; }
            for (EntityItem.PreparedDrop ticket : tickets) {
                try { ticket.notifyPublished(); }
                catch (RuntimeException | Error notification) {
                    failure = retainFailure(failure, notification);
                }
            }
            try { inventory.sendContents(inventory.getViewers()); }
            catch (RuntimeException | Error notification) {
                failure = retainFailure(failure, notification);
            }
            if (failure instanceof RuntimeException exception) throw exception;
            if (failure instanceof Error error) throw error;
        } catch (RuntimeException | Error failure) {
            failureInFlight = failure;
            throw failure;
        } finally {
            preparingContainerBreak = false;
            Throwable cleanupFailure = null;
            if (!accepted) for (EntityItem.PreparedDrop ticket : tickets) {
                try { ticket.discard(); }
                catch (RuntimeException | Error cleanup) { cleanupFailure = retainFailure(cleanupFailure, cleanup); }
            }
            if (cleanupFailure != null) {
                if (failureInFlight != null) {
                    if (cleanupFailure != failureInFlight) failureInFlight.addSuppressed(cleanupFailure);
                } else if (cleanupFailure instanceof Error error) throw error;
                else if (cleanupFailure instanceof RuntimeException exception) throw exception;
            }
        }
    }

    private static boolean hasGoods(Inventory inventory) {
        return inventory != null && inventory.getContents().values().stream().anyMatch(item -> !item.isNull());
    }

    private static void refuseRetainedSource(Inventory inventory) {
        if (hasGoods(inventory)) throw new IllegalStateException("Container break refused before source removal");
    }

    private static Throwable retainFailure(Throwable original, Throwable additional) {
        if (original == null) return additional;
        if (additional instanceof Error && !(original instanceof Error)) {
            additional.addSuppressed(original);
            return additional;
        }
        if (original != additional) original.addSuppressed(additional);
        return original;
    }

    private EntityItem.PreparedDrop prepareContainerDrop(Position holder, Item content) {
        // Prepare the complete native payload before accepting source removal.
        ThreadLocalRandom random = ThreadLocalRandom.current();
        CompoundTag item = NBTIO.putItemHelper(content);
        item.setName("Item");
        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", holder.x))
                        .add(new DoubleTag("", holder.y)).add(new DoubleTag("", holder.z)))
                .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", random.nextDouble() * 0.2 - 0.1))
                        .add(new DoubleTag("", 0.2)).add(new DoubleTag("", random.nextDouble() * 0.2 - 0.1)))
                .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", random.nextFloat() * 360))
                        .add(new FloatTag("", 0)))
                .putShort("Health", 5).putCompound("Item", item).putShort("PickupDelay", 10);
        return EntityItem.prepareDrop(holder.getLevel().getChunk((int) holder.x >> 4, (int) holder.z >> 4, true), nbt, content);
    }

}
