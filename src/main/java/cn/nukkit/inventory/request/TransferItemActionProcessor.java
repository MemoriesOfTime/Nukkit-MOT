package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.inventory.InventoryClickEvent;
import cn.nukkit.event.inventory.InventoryTransactionEvent;
import cn.nukkit.event.player.PlayerTransferItemEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.inventory.transaction.InventoryTransaction;
import cn.nukkit.inventory.transaction.action.InventoryAction;
import cn.nukkit.inventory.transaction.action.SlotChangeAction;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.ItemStackRequestActionType;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.TransferItemStackRequestAction;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import lombok.extern.log4j.Log4j2;

import java.util.*;

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

        if (srcInv == null || dstInv == null) {
            return context.error();
        }

        if (count <= 0) {
            return context.error();
        }

        Item sourceItem = srcInv.getUnclonedItem(srcSlot);
        if (sourceItem.isNull() || sourceItem.getCount() < count) {
            return context.error();
        }
        if (validateStackNetworkId(sourceItem.getStackNetId(), src.getStackNetworkId())) {
            return context.error();
        }

        Item destItem = dstInv.getUnclonedItem(dstSlot);
        if (validateStackNetworkId(destItem.getStackNetId(), dst.getStackNetworkId())) {
            return context.error();
        }

        boolean fullTransfer = sourceItem.getCount() == count;
        boolean srcIsCreatedOutput = isCreatedOutput(srcInv, srcSlot);
        boolean creativeCreatedOutputTransfer = player.isCreative()
                && srcIsCreatedOutput
                && Boolean.TRUE.equals(context.get(CraftCreativeActionProcessor.CRAFT_CREATIVE_KEY));
        if (creativeCreatedOutputTransfer) {
            return transferCreativeCreatedOutput(action, player, context, srcInv, srcSlot, dstInv, dstSlot,
                    sourceItem, destItem, fullTransfer);
        }

        if (!destItem.isNull() && !destItem.equals(sourceItem, true, true)) {
            return context.error();
        }

        int destCount = destItem.isNull() ? 0 : destItem.getCount();
        if (destCount + count > sourceItem.getMaxStackSize()) {
            return context.error();
        }

        if (!isSlotCompatible(dstInv, dstSlot, sourceItem)) {
            return context.error();
        }

        // Equipment containers (OFFHAND/ARMOR) must emit network packets so other
        // players see MobEquipment/MobArmor updates; other containers can stay
        // quiet to avoid double-send with the ItemStackResponse echo.
        boolean sendSource = isEquipmentSlot(src.getContainer());
        boolean sendDest = isEquipmentSlot(dst.getContainer());

        // stackNetId allocation strategy aligned with Allay/PNX:
        // - full transfer + empty dst   : keep source stackId (whole stack moves)
        // - any transfer + non-empty dst: keep dest stackId (merge into existing)
        // - partial transfer + empty dst: assign new stackId (split creates new stack)
        // - CREATED_OUTPUT full transfer: assign new stackId so every creative take
        //   is an independent stack (prevents id reuse across creative sessions).
        Item newDest;
        if (destItem.isNull()) {
            newDest = sourceItem.clone();
            newDest.setCount(count);
            if (!fullTransfer || srcIsCreatedOutput) {
                newDest.autoAssignStackNetworkId();
            }
        } else {
            newDest = destItem.clone();
            newDest.setCount(destCount + count);
        }

        Item newSrc;
        if (fullTransfer) {
            newSrc = Item.get(Item.AIR);
        } else {
            newSrc = sourceItem.clone();
            newSrc.setCount(sourceItem.getCount() - count);
        }

        if (!fireTransferEvent(player, srcInv, srcSlot, dstInv, dstSlot, sourceItem, destItem, count)) {
            return context.error();
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

        // 向后兼容：旧路径中每次库存交互都会触发 InventoryTransactionEvent，
        // 但 SAI 路径默认不触发。为保持与旧插件的兼容性，在此处补发事件。
        List<InventoryAction> transactionActions = new ArrayList<>();
        transactionActions.add(new SlotChangeAction(srcInv, srcSlot, sourceItem, newSrc));
        if (srcInv != dstInv || srcSlot != dstSlot) {
            transactionActions.add(new SlotChangeAction(dstInv, dstSlot, destItem, newDest));
        }
        InventoryTransaction transaction = new EventOnlyInventoryTransaction(player, transactionActions, context);
        if (!transaction.execute()) {
            return context.error();
        }

        // 原子性写入：先 dst，再 src；任一失败则回滚已写入的另一端。
        // PlayerInventory.setItem 可能在 EntityInventoryChangeEvent/EntityArmorChangeEvent
        // 被插件取消时返回 false —— 需要把 dst 恢复到原状态避免出现悬挂写入。
        Item originalDestItem = destItem.clone();
        Item originalSourceItem = sourceItem.clone();

        if (!dstInv.setItem(dstSlot, newDest, sendDest)) {
            return context.error();
        }

        boolean srcOk = fullTransfer
                ? srcInv.clear(srcSlot, sendSource)
                : srcInv.setItem(srcSlot, newSrc, sendSource);
        if (!srcOk) {
            if (originalDestItem.isNull()) {
                dstInv.clear(dstSlot, sendDest);
            } else {
                dstInv.setItem(dstSlot, originalDestItem, sendDest);
            }
            return context.error();
        }

        // 防御：如果 src 的底层 setItem 不知为何把原物品写没了（如插件篡改为 AIR），
        // 也要把 src 恢复，避免客户端回滚看到不一致状态。
        if (srcInv.getItem(srcSlot).isNull() && !fullTransfer) {
            srcInv.setItem(srcSlot, originalSourceItem, sendSource);
        }

        List<ItemStackResponseContainer> containers = new ArrayList<>();
        containers.add(buildContainer(srcInv, srcSlot, src));
        if (!sameNetworkSlot(src, dst)) {
            containers.add(buildContainer(dstInv, dstSlot, dst));
        }
        return context.success(containers);
    }

    private ActionResponse transferCreativeCreatedOutput(T action, Player player, ItemStackRequestContext context,
                                                         Inventory srcInv, int srcSlot,
                                                         Inventory dstInv, int dstSlot,
                                                         Item sourceItem, Item destItem,
                                                         boolean fullTransfer) {
        ItemStackRequestSlotData dst = action.getDestination();
        int count = action.getCount();
        if (srcInv == dstInv && srcSlot == dstSlot) {
            return context.error();
        }
        if (!isSlotCompatible(dstInv, dstSlot, sourceItem)) {
            return context.error();
        }

        boolean sendSource = isEquipmentSlot(action.getSource().getContainer());
        boolean sendDest = isEquipmentSlot(dst.getContainer());

        Item newDest = sourceItem.clone();
        newDest.setCount(count);
        newDest.autoAssignStackNetworkId();

        Item newSrc;
        if (fullTransfer) {
            newSrc = Item.get(Item.AIR);
        } else {
            newSrc = sourceItem.clone();
            newSrc.setCount(sourceItem.getCount() - count);
        }

        if (!fireTransferEvent(player, srcInv, srcSlot, dstInv, dstSlot, sourceItem, destItem, count)) {
            return context.error();
        }
        if (!fireClickEvent(player, srcInv, srcSlot, sourceItem, newSrc)) {
            return context.error();
        }
        if (!fireClickEvent(player, dstInv, dstSlot, destItem, newDest)) {
            return context.error();
        }

        List<InventoryAction> transactionActions = new ArrayList<>();
        transactionActions.add(new SlotChangeAction(srcInv, srcSlot, sourceItem, newSrc));
        transactionActions.add(new SlotChangeAction(dstInv, dstSlot, destItem, newDest));
        InventoryTransaction transaction = new EventOnlyInventoryTransaction(player, transactionActions, context);
        if (!transaction.execute()) {
            return context.error();
        }

        Item originalDestItem = destItem.clone();
        if (!dstInv.setItem(dstSlot, newDest, sendDest)) {
            return context.error();
        }

        boolean srcOk = fullTransfer
                ? srcInv.clear(srcSlot, sendSource)
                : srcInv.setItem(srcSlot, newSrc, sendSource);
        if (!srcOk) {
            if (originalDestItem.isNull()) {
                dstInv.clear(dstSlot, sendDest);
            } else {
                dstInv.setItem(dstSlot, originalDestItem, sendDest);
            }
            return context.error();
        }

        return context.success(List.of(buildContainer(dstInv, dstSlot, dst)));
    }

    private static boolean isEquipmentSlot(ContainerSlotType type) {
        return type == ContainerSlotType.OFFHAND || type == ContainerSlotType.ARMOR;
    }

    /**
     * Check whether {@code item} is allowed in the given {@code slot} of
     * {@code inventory}. ARMOR slots reject non-matching equipment; all other
     * inventories accept any item.
     */
    static boolean isSlotCompatible(Inventory inventory, int slot, Item item) {
        if (inventory instanceof PlayerInventory playerInv) {
            int size = playerInv.getSize();
            if (slot == size) {
                return item.canBePutInHelmetSlot();
            } else if (slot == size + 1) {
                return item.isChestplate();
            } else if (slot == size + 2) {
                return item.isLeggings();
            } else if (slot == size + 3) {
                return item.isBoots();
            }
        }
        return true;
    }

    private static boolean isCreatedOutput(Inventory inventory, int slot) {
        return inventory instanceof PlayerUIInventory && slot == PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT;
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

    static boolean fireTransferEvent(Player actor, Inventory sourceInventory, int sourceSlot,
                                     Inventory destinationInventory, int destinationSlot,
                                     Item sourceItem, Item destinationItem, int count) {
        PlayerTransferItemEvent event = new PlayerTransferItemEvent(
                actor,
                sourceInventory,
                sourceSlot,
                destinationInventory,
                destinationSlot,
                sourceItem.clone(),
                destinationItem.clone(),
                count
        );
        Server.getInstance().getPluginManager().callEvent(event);
        return !event.isCancelled();
    }

    static boolean sameNetworkSlot(ItemStackRequestSlotData first, ItemStackRequestSlotData second) {
        return first.getContainer() == second.getContainer()
                && first.getSlot() == second.getSlot()
                && Objects.equals(first.getDynamicId(), second.getDynamicId());
    }

    /**
     * InventoryTransaction subclass used solely to emit
     * {@link InventoryTransactionEvent} for server-authoritative item stack
     * requests that involve block-entity containers (e.g. chests).  It does
     * <b>not</b> execute any actions – the real mutation is already performed
     * by {@link #doTransfer} – so calling {@link #execute} only fires the
     * event and returns whether a plugin cancelled it.
     */
    static class EventOnlyInventoryTransaction extends InventoryTransaction {
        private final ItemStackRequestContext context;

        public EventOnlyInventoryTransaction(Player source, List<InventoryAction> actions, ItemStackRequestContext context) {
            super(source, actions, false);
            this.context = context;
            init(source, actions);
        }

        @Override
        protected void init(Player source, List<InventoryAction> actions) {
            this.source = source;
            for (InventoryAction action : actions) {
                this.addAction(action);
            }
        }

        @Override
        protected boolean callExecuteEvent() {
            InventoryTransactionEvent ev = new InventoryTransactionEvent(this);
            this.source.getServer().getPluginManager().callEvent(ev);
            return !ev.isCancelled();
        }

        @Override
        public boolean execute() {
            if (this.invalid || this.actions.isEmpty()) {
                return false;
            }

            // Snapshot affected slots before firing the event so we can tell
            // whether a plugin actually mutated the inventory when cancelling.
            Map<Inventory, Map<Integer, Item>> preStates = new HashMap<>();
            for (InventoryAction action : this.actions) {
                if (action instanceof SlotChangeAction slotChange) {
                    Inventory inv = slotChange.getInventory();
                    int slot = slotChange.getSlot();
                    preStates.computeIfAbsent(inv, k -> new HashMap<>()).put(slot, inv.getItem(slot).clone());
                }
            }

            if (!callExecuteEvent()) {
                if (context != null) {
                    for (InventoryAction action : this.actions) {
                        if (action instanceof SlotChangeAction slotChange) {
                            Inventory inv = slotChange.getInventory();
                            int slot = slotChange.getSlot();
                            Item before = preStates.getOrDefault(inv, Collections.emptyMap()).get(slot);
                            Item after = inv.getItem(slot);
                            if (before == null || after == null || !before.equals(after, true, true)) {
                                context.addPluginModifiedInventory(inv);
                            }
                        }
                    }
                }
                return false;
            }
            this.hasExecuted = true;
            return true;
        }
    }

    static ItemStackResponseContainer buildContainer(Inventory inv, int internalSlot, ItemStackRequestSlotData slotData) {
        Item current = inv.getUnclonedItem(internalSlot);
        int networkSlot = slotData.getSlot();
        // hotbarSlot 与 slot 保持一致，与 Allay/PNX 对齐。部分客户端对"非 HOTBAR 容器
        // 填 hotbarSlot=0"会误判需要刷新热栏槽 0 造成视觉错位。
        ItemStackResponseSlot slot = new ItemStackResponseSlot(
                networkSlot,
                networkSlot,
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
