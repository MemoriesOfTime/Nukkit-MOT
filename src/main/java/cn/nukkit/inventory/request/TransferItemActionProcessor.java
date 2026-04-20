package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TransferItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import lombok.extern.log4j.Log4j2;

import java.util.ArrayList;
import java.util.List;

/**
 * Base handler for TAKE and PLACE actions — both structurally a partial/full
 * transfer between two slots. Centralises the count math, stack merging rules
 * and response container construction.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public abstract class TransferItemActionProcessor<T extends TransferItemStackRequestAction>
        implements ItemStackRequestActionProcessor<T> {

    protected ActionResponse doTransfer(T action, Player player, ItemStackRequestContext context) {
        ItemStackRequestSlotData src = action.getSource();
        ItemStackRequestSlotData dst = action.getDestination();

        Inventory srcInv = NetworkMapping.getInventory(player, src.getContainer(), src.getDynamicId());
        Inventory dstInv = NetworkMapping.getInventory(player, dst.getContainer(), dst.getDynamicId());

        int srcSlot = srcInv == null ? -1 : NetworkMapping.toInternalSlot(src.getContainer(), src.getSlot());
        int dstSlot = dstInv == null ? -1 : NetworkMapping.toInternalSlot(dst.getContainer(), dst.getSlot());
        int count = action.getCount();

        log.info("{}: {} src={}[net={}->int={},netId={}] dst={}[net={}->int={},netId={}] count={} srcItem={} dstItem={}",
                player.getName(), getType(),
                src.getContainer(), src.getSlot(), srcSlot, src.getStackNetworkId(),
                dst.getContainer(), dst.getSlot(), dstSlot, dst.getStackNetworkId(),
                count,
                srcInv == null ? "null-inv" : srcInv.getItem(srcSlot),
                dstInv == null ? "null-inv" : dstInv.getItem(dstSlot));

        if (srcInv == null || dstInv == null) {
            log.info("{}: transfer rejected - inventory missing src={}({}) dst={}({})",
                    player.getName(), src.getContainer(), srcInv, dst.getContainer(), dstInv);
            return context.error();
        }

        if (count <= 0) {
            log.info("{}: transfer rejected - non-positive count {}", player.getName(), count);
            return context.error();
        }

        Item sourceItem = srcInv.getItem(srcSlot);
        if (sourceItem.isNull() || sourceItem.getCount() < count) {
            log.info("{}: transfer rejected - src invalid (slot {} item={} count needed {})",
                    player.getName(), srcSlot, sourceItem, count);
            return context.error();
        }
        if (validateStackNetworkId(sourceItem.getStackNetId(), src.getStackNetworkId())) {
            log.info("{}: transfer rejected - src stackNetId mismatch server={} client={}",
                    player.getName(), sourceItem.getStackNetId(), src.getStackNetworkId());
            return context.error();
        }

        Item destItem = dstInv.getItem(dstSlot);
        if (!destItem.isNull() && !destItem.equals(sourceItem, true, true)) {
            log.info("{}: transfer rejected - dst item differs (dst {} vs src {})",
                    player.getName(), destItem, sourceItem);
            return context.error();
        }
        if (validateStackNetworkId(destItem.getStackNetId(), dst.getStackNetworkId())) {
            log.info("{}: transfer rejected - dst stackNetId mismatch server={} client={}",
                    player.getName(), destItem.getStackNetId(), dst.getStackNetworkId());
            return context.error();
        }

        int destCount = destItem.isNull() ? 0 : destItem.getCount();
        if (destCount + count > sourceItem.getMaxStackSize()) {
            log.info("{}: transfer rejected - would overflow max stack (destCount={} + count={} > max={})",
                    player.getName(), destCount, count, sourceItem.getMaxStackSize());
            return context.error();
        }

        // Equipment containers (OFFHAND/ARMOR) must emit network packets so other
        // players see MobEquipment/MobArmor updates; other containers can stay
        // quiet to avoid double-send with the ItemStackResponse echo.
        boolean sendSource = isEquipmentSlot(src.getContainer());
        boolean sendDest = isEquipmentSlot(dst.getContainer());

        Item newDest;
        if (destItem.isNull()) {
            newDest = sourceItem.clone();
            newDest.setCount(count);
            newDest.autoAssignStackNetworkId();
        } else {
            newDest = destItem.clone();
            newDest.setCount(destCount + count);
        }

        Item newSrc;
        if (sourceItem.getCount() == count) {
            newSrc = Item.get(Item.AIR);
        } else {
            newSrc = sourceItem.clone();
            newSrc.setCount(sourceItem.getCount() - count);
        }

        // Fire InventoryClickEvent for each affected slot (matches legacy
        // InventoryTransaction.java:260). Only holder-is-Player inventories
        // trigger the event, as in the legacy path.
        if (!fireClickEvent(player, srcInv, srcSlot, sourceItem, newSrc)) {
            return context.error();
        }
        if (!fireClickEvent(player, dstInv, dstSlot, destItem, newDest)) {
            return context.error();
        }

        dstInv.setItem(dstSlot, newDest, sendDest);

        if (sourceItem.getCount() == count) {
            srcInv.clear(srcSlot, sendSource);
        } else {
            srcInv.setItem(srcSlot, newSrc, sendSource);
        }

        List<ItemStackResponseContainer> containers = new ArrayList<>();
        containers.add(buildContainer(srcInv, srcSlot, src));
        if (src.getContainer() != dst.getContainer() || src.getSlot() != dst.getSlot()) {
            containers.add(buildContainer(dstInv, dstSlot, dst));
        }
        return context.success(containers);
    }

    private static boolean isEquipmentSlot(ContainerSlotType type) {
        return type == ContainerSlotType.OFFHAND || type == ContainerSlotType.ARMOR;
    }

    /**
     * Fire {@link InventoryClickEvent} for a slot that is about to change. Only
     * inventories whose holder is a {@link Player} emit the event, mirroring the
     * legacy {@code InventoryTransaction.callExecuteEvent} check. Returns
     * {@code false} when a plugin cancelled the event — callers should abort
     * and return an error response.
     */
    static boolean fireClickEvent(Player actor, Inventory inventory, int slot, Item sourceItem, Item heldItem) {
        if (!(inventory.getHolder() instanceof Player)) {
            return true;
        }
        InventoryClickEvent event = new InventoryClickEvent(
                actor, inventory, slot, sourceItem.clone(), heldItem.clone());
        Server.getInstance().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    static ItemStackResponseContainer buildContainer(Inventory inv, int internalSlot, ItemStackRequestSlotData slotData) {
        Item current = inv.getItem(internalSlot);
        int networkSlot = slotData.getSlot();
        int hotbarSlot = (slotData.getContainer() == ContainerSlotType.HOTBAR
                || slotData.getContainer() == ContainerSlotType.HOTBAR_AND_INVENTORY) ? networkSlot : 0;
        ItemStackResponseSlot slot = new ItemStackResponseSlot(
                networkSlot,
                hotbarSlot,
                current.isNull() ? 0 : current.getCount(),
                current.getStackNetId(),
                current.hasCustomName() ? current.getCustomName() : "",
                current.getDamage(),
                ""
        );
        FullContainerName name = new FullContainerName(slotData.getContainer(), slotData.getDynamicId());
        return new ItemStackResponseContainer(slotData.getContainer(), List.of(slot), name);
    }

    @Override
    public abstract ItemStackRequestActionType getType();
}
