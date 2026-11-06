package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.DoubleChestInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/** Keeps a dynamic carrier and every live descendant private until its entire request settles. */
final class NativeItemStackRequestOwnership implements AutoCloseable {
    private static final String CONTEXT_KEY = NativeItemStackRequestOwnership.class.getName();
    private final InventoryMutationGuard.Lease lease;
    private final InventoryMutationGuard.NativeMetadataSnapshot metadata;
    private final boolean required;

    private NativeItemStackRequestOwnership(boolean required, InventoryMutationGuard.Lease lease) {
        this.required = required;
        this.lease = lease;
        this.metadata = lease == null ? null : lease.captureNativeMetadata();
    }

    static NativeItemStackRequestOwnership acquire(Player actor, ItemStackRequestAction[] actions,
                                                  ItemStackRequestContext context) {
        boolean required = false;
        for (ItemStackRequestAction action : actions) {
            if (action instanceof TransferItemStackRequestAction transfer) {
                required |= dynamic(transfer.getSource()) || dynamic(transfer.getDestination());
            } else if (action instanceof SwapAction swap) {
                required |= dynamic(swap.getSource()) || dynamic(swap.getDestination());
            } else if (action instanceof DropAction drop) required |= dynamic(drop.getSource());
            else if (action instanceof DestroyAction destroy) required |= dynamic(destroy.getSource());
            else if (action instanceof ConsumeAction consume) required |= dynamic(consume.getSource());
        }
        List<Inventory> roots = new ArrayList<>();
        add(roots, actor.getInventory());
        add(roots, actor.getOffhandInventory());
        add(roots, actor.getEnderChestInventory());
        add(roots, actor.getUIInventory());
        add(roots, actor.getCursorInventory());
        add(roots, actor.getCraftingGrid());
        actor.getTopWindow().ifPresent(root -> add(roots, root));
        add(roots, NetworkMapping.getInventory(actor, ContainerSlotType.HORSE_EQUIP, null));
        if (!required) {
            // A nested ordinary request must not acknowledge another request's provisional carrier output.
            // The existing capture fence has an allocation-free fast path when no claim or write is active.
            boolean blocked = !InventoryMutationGuard.canCaptureProfile(roots);
            return new NativeItemStackRequestOwnership(blocked, null);
        }
        Set<Inventory> inventories = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Inventory root : roots) collect(root, inventories);
        InventoryMutationGuard.Lease lease = InventoryMutationGuard.tryClaim(inventories);
        NativeItemStackRequestOwnership ownership = new NativeItemStackRequestOwnership(true, lease);
        if (lease != null) {
            lease.followBundleDescendants(roots);
            context.put(CONTEXT_KEY, ownership);
        }
        return ownership;
    }

    private static boolean dynamic(ItemStackRequestSlotData slot) {
        return slot != null && slot.getContainer() == ContainerSlotType.DYNAMIC_CONTAINER;
    }

    private static void add(List<Inventory> roots, Inventory inventory) {
        if (inventory != null) roots.add(inventory);
    }

    private static void collect(Inventory inventory, Set<Inventory> inventories) {
        if (!inventories.add(inventory)) return;
        if (inventory instanceof DoubleChestInventory chest) {
            collect(chest.getLeftSide(), inventories);
            collect(chest.getRightSide(), inventories);
            return;
        }
        if (inventory instanceof PlayerUIComponent component) collect(component.getBackingInventory(), inventories);
        for (Item item : inventory.getContents().values()) {
            if (item instanceof ItemBundle bundle) collect(bundle.getInventory(), inventories);
        }
    }

    boolean ready() { return !required || lease != null; }
    boolean metadataMatches() { return metadata == null || metadata.matches(); }
    void restoreMetadata() { if (metadata != null) metadata.restore(); }

    @SuppressWarnings("try")
    static boolean write(ItemStackRequestContext context, Inventory inventory, int slot, Item target, boolean send) {
        NativeItemStackRequestOwnership ownership = context.get(CONTEXT_KEY);
        if (ownership == null) return inventory.setItem(slot, target, send);
        ownership.lease.refreshBundleDescendants();
        if (!ownership.lease.contains(inventory)) return false;
        try (InventoryMutationGuard.WriteScope write = ownership.lease.expectWrite(inventory, slot, target)) {
            if (!inventory.setItem(slot, target, send)) return false;
            return ownership.metadataMatches()
                    && InventoryMutationGuard.matchesTarget(inventory.getItem(slot), target);
        }
    }

    @Override public void close() { if (lease != null) lease.close(); }
}
