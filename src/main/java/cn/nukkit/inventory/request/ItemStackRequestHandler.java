package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.event.inventory.ItemStackRequestActionEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.inventory.PlayerUIComponent;
import cn.nukkit.inventory.PlayerUIInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.ItemStackResponsePacket;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequest;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.ItemStackRequestSlotData;
import cn.nukkit.network.protocol.types.inventory.itemstack.request.action.*;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponse;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseContainer;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseSlot;
import cn.nukkit.network.protocol.types.inventory.itemstack.response.ItemStackResponseStatus;
import lombok.extern.log4j.Log4j2;

import java.util.*;

/**
 * Central dispatcher for incoming ItemStackRequest payloads. Iterates the action
 * chain in each request, delegates each action to its registered processor, and
 * emits a single ItemStackResponsePacket summarising success/failure per request.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
@Log4j2
public final class ItemStackRequestHandler {

    private static final EnumMap<ItemStackRequestActionType, ItemStackRequestActionProcessor<?>> PROCESSORS =
            new EnumMap<>(ItemStackRequestActionType.class);

    static {
        register(new TakeActionProcessor());
        register(new PlaceActionProcessor());
        register(new TakeFromItemContainerActionProcessor());
        register(new PlaceInItemContainerActionProcessor());
        register(new SwapActionProcessor());
        register(new DropActionProcessor());
        register(new DestroyActionProcessor());
        register(new ConsumeActionProcessor());
        register(new CreateActionProcessor());
        register(new CraftRecipeActionProcessor());
        register(new CraftRecipeAutoProcessor());
        register(new CraftCreativeActionProcessor());
        register(new CraftRecipeOptionalProcessor());
        register(new CraftGrindstoneActionProcessor());
        register(new CraftLoomActionProcessor());
        register(new CraftResultDeprecatedActionProcessor());
        register(new CraftNonImplementedActionProcessor());
        register(new MineBlockActionProcessor());
        register(new LabTableCombineActionProcessor());
        register(new BeaconPaymentActionProcessor());
    }

    public static void register(ItemStackRequestActionProcessor<?> processor) {
        PROCESSORS.put(processor.getType(), processor);
    }

    private ItemStackRequestHandler() {
    }

    @SuppressWarnings("unchecked")
    public static void handleRequests(Player player, List<ItemStackRequest> requests) {
        List<ItemStackResponse> responses = new ArrayList<>();

        for (ItemStackRequest request : requests) {
            ItemStackRequestAction[] actions = request.getActions();
            ItemStackRequestContext context = new ItemStackRequestContext(request);
            List<ItemStackResponseContainer> responseContainers = new ArrayList<>();
            Set<Inventory> affectedInventories = new LinkedHashSet<>();
            Set<NetworkMapping.BundleHolderRef> affectedBundleOuters = new LinkedHashSet<>();
            LinkedHashMap<Inventory, Map<Integer, Item>> snapshots = new LinkedHashMap<>();
            boolean error = false;

            for (int i = 0; i < actions.length; i++) {
                ItemStackRequestAction action = actions[i];
                context.setCurrentActionIndex(i);
                affectedInventories.addAll(resolveAffectedInventories(player, action));
                affectedBundleOuters.addAll(resolveAffectedBundleOuters(player, action));
                captureSnapshots(snapshots, affectedInventories);

                ItemStackRequestActionProcessor<ItemStackRequestAction> processor =
                        (ItemStackRequestActionProcessor<ItemStackRequestAction>) PROCESSORS.get(action.getType());

                if (processor == null) {
                    // 未注册/未实现的 action 类型静默跳过，继续处理同一请求内的后续 action，
                    // 单条 action 不应令整条 request 失败。
                    log.warn("{}: unhandled item stack request action {}", player.getName(), action.getType());
                    continue;
                }

                try {
                    ItemStackRequestActionEvent event = new ItemStackRequestActionEvent(player, action, i);
                    player.getServer().getPluginManager().callEvent(event);
                    if (event.isCancelled()) {
                        error = true;
                        break;
                    }
                    if (event.getResponse() != null) {
                        if (!event.getResponse().success()) {
                            error = true;
                            break;
                        }
                        responseContainers.addAll(event.getResponse().containers());
                        continue;
                    }

                    ActionResponse response = processor.handle(action, player, context);
                    if (response == null) {
                        continue;
                    }
                    if (!response.success()) {
                        error = true;
                        break;
                    }
                    responseContainers.addAll(response.containers());
                } catch (Exception e) {
                    log.error("{}: error processing item stack request action {}", player.getName(), action.getType(), e);
                    error = true;
                    break;
                }
            }

            if (!error) {
                // commit 副作用（经验扣除、实体生成、NBT 更新等）是不可逆的。
                // 如果 commit 部分失败，回滚库存只会制造新的不一致
                // （如经验已扣但物品也恢复），因此 commit 失败时不回滚，
                // 仅记录日志并同步当前真实状态给客户端。
                if (!context.commit()) {
                    log.warn("{}: item stack request {} commit partially failed", player.getName(), request.getRequestId());
                }
            }

            if (error) {
                rollbackSnapshots(snapshots, context.getPluginModifiedSlots());
                resyncActor(player, snapshots.keySet());
            }

            syncAffectedInventories(player, affectedInventories);
            syncAffectedBundleOuters(player, affectedBundleOuters);
            ItemStackResponseStatus status = error ? ItemStackResponseStatus.ERROR : ItemStackResponseStatus.OK;
            responses.add(new ItemStackResponse(
                    status,
                    request.getRequestId(),
                    error ? List.of() : compactContainers(responseContainers)
            ));
        }

        ItemStackResponsePacket packet = new ItemStackResponsePacket();
        packet.entries.addAll(responses);
        packet.protocol = player.protocol;
        packet.gameVersion = player.getGameVersion();
        player.dataPacket(packet);
    }

    private static Set<Inventory> resolveAffectedInventories(Player player, ItemStackRequestAction action) {
        LinkedHashSet<Inventory> affected = new LinkedHashSet<>();

        try {
            if (action instanceof TransferItemStackRequestAction transfer) {
                addAffectedInventory(affected, player, transfer.getSource());
                addAffectedInventory(affected, player, transfer.getDestination());
            } else if (action instanceof SwapAction swap) {
                addAffectedInventory(affected, player, swap.getSource());
                addAffectedInventory(affected, player, swap.getDestination());
            } else if (action instanceof DropAction drop) {
                addAffectedInventory(affected, player, drop.getSource());
            } else if (action instanceof DestroyAction destroy) {
                addAffectedInventory(affected, player, destroy.getSource());
            } else if (action instanceof ConsumeAction consume) {
                addAffectedInventory(affected, player, consume.getSource());
            } else if (writesCreatedOutput(action)) {
                affected.add(player.getUIInventory());
            }
        } catch (Throwable t) {
            log.debug("{}: failed to resolve affected inventories for action {}", player.getName(), action.getType(), t);
        }

        return affected;
    }

    private static void addAffectedInventory(Set<Inventory> affected, Player player, ItemStackRequestSlotData slotData) {
        Inventory inventory = NetworkMapping.getInventory(player, slotData.getContainer(), slotData.getDynamicId());
        Inventory canonical = canonicalizeInventory(inventory);
        if (canonical != null) {
            affected.add(canonical);
        }
    }

    private static boolean writesCreatedOutput(ItemStackRequestAction action) {
        return action instanceof CreateAction
                || action instanceof CraftRecipeAction
                || action instanceof AutoCraftRecipeAction
                || action instanceof CraftCreativeAction
                || action instanceof CraftRecipeOptionalAction
                || action instanceof CraftGrindstoneAction
                || action instanceof CraftLoomAction
                || action instanceof CraftResultsDeprecatedAction;
    }

    static Inventory canonicalizeInventory(Inventory inventory) {
        if (inventory instanceof PlayerUIComponent component && component.getHolder() instanceof Player player) {
            return player.getUIInventory();
        }
        return inventory;
    }

    private static void captureSnapshots(Map<Inventory, Map<Integer, Item>> snapshots, Set<Inventory> inventories) {
        for (Inventory inventory : inventories) {
            Inventory canonical = canonicalizeInventory(inventory);
            if (canonical != null && !snapshots.containsKey(canonical)) {
                snapshots.put(canonical, copyContents(canonical));
            }
        }
    }

    private static Map<Integer, Item> copyContents(Inventory inventory) {
        LinkedHashMap<Integer, Item> snapshot = new LinkedHashMap<>();
        for (var entry : inventory.getContents().entrySet()) {
            Item item = entry.getValue();
            if (item != null && !item.isNull() && item.getCount() > 0) {
                snapshot.put(entry.getKey(), item.clone());
            }
        }
        return snapshot;
    }

    private static void rollbackSnapshots(Map<Inventory, Map<Integer, Item>> snapshots,
                                          Map<Inventory, Map<Integer, Item>> pluginModifiedSlots) {
        for (var entry : snapshots.entrySet()) {
            restoreInventory(entry.getKey(), entry.getValue());
            replayPluginModifiedSlots(entry.getKey(), pluginModifiedSlots);
        }
    }

    private static void restoreInventory(Inventory inventory, Map<Integer, Item> snapshot) {
        Inventory canonical = canonicalizeInventory(inventory);
        if (canonical == null) {
            return;
        }

        LinkedHashSet<Integer> currentSlots = new LinkedHashSet<>(canonical.getContents().keySet());
        for (int slot = 0; slot < canonical.getSize(); slot++) {
            currentSlots.add(slot);
        }

        // Use setItemForce to bypass EntityInventoryChangeEvent / EntityArmorChangeEvent
        // — rollback is a server-authoritative restore and must not be vetoed by
        // plugin event handlers, otherwise the slot stays in its post-action state
        // and the client receives an inconsistent inventory.
        for (int slot : currentSlots) {
            if (!snapshot.containsKey(slot)) {
                Item current = canonical.getItem(slot);
                if (current != null && !current.isNull()) {
                    canonical.setItemForce(slot, Item.get(Item.AIR));
                }
            }
        }

        for (var entry : snapshot.entrySet()) {
            Item item = entry.getValue();
            if (item != null && !item.isNull() && item.getCount() > 0) {
                canonical.setItemForce(entry.getKey(), item.clone());
            } else {
                canonical.setItemForce(entry.getKey(), Item.get(Item.AIR));
            }
        }
    }

    private static void replayPluginModifiedSlots(Inventory inventory, Map<Inventory, Map<Integer, Item>> pluginModifiedSlots) {
        Inventory canonical = canonicalizeInventory(inventory);
        if (canonical == null || pluginModifiedSlots == null) {
            return;
        }

        Map<Integer, Item> modifiedSlots = pluginModifiedSlots.get(canonical);
        if (modifiedSlots == null || modifiedSlots.isEmpty()) {
            return;
        }

        for (var entry : modifiedSlots.entrySet()) {
            Item item = entry.getValue();
            if (item == null || item.isNull() || item.getCount() <= 0) {
                canonical.setItemForce(entry.getKey(), Item.get(Item.AIR));
            } else {
                canonical.setItemForce(entry.getKey(), item.clone());
            }
        }
    }

    private static void resyncActor(Player actor, Collection<Inventory> inventories) {
        actor.getCursorInventory().sendContents(actor);
        actor.sendAllInventories();
        actor.getInventory().sendHeldItem(actor);
        actor.getInventory().sendArmorContents(actor);
        actor.getOffhandInventory().sendContents(actor);

        for (Inventory inventory : inventories) {
            if (inventory == null || inventory instanceof PlayerInventory || inventory instanceof PlayerUIInventory) {
                continue;
            }
            inventory.sendContents(actor);
        }
    }

    private static Set<NetworkMapping.BundleHolderRef> resolveAffectedBundleOuters(Player player, ItemStackRequestAction action) {
        LinkedHashSet<NetworkMapping.BundleHolderRef> refs = new LinkedHashSet<>();

        try {
            if (action instanceof TransferItemStackRequestAction transfer) {
                collectBundleOuter(refs, player, transfer.getSource());
                collectBundleOuter(refs, player, transfer.getDestination());
            } else if (action instanceof SwapAction swap) {
                collectBundleOuter(refs, player, swap.getSource());
                collectBundleOuter(refs, player, swap.getDestination());
            } else if (action instanceof DropAction drop) {
                collectBundleOuter(refs, player, drop.getSource());
            } else if (action instanceof DestroyAction destroy) {
                collectBundleOuter(refs, player, destroy.getSource());
            } else if (action instanceof ConsumeAction consume) {
                collectBundleOuter(refs, player, consume.getSource());
            }
        } catch (Throwable t) {
            log.debug("{}: failed to resolve bundle outer for action {}", player.getName(), action.getType(), t);
        }

        return refs;
    }

    private static void collectBundleOuter(Set<NetworkMapping.BundleHolderRef> refs, Player player, ItemStackRequestSlotData slotData) {
        if (slotData == null || slotData.getContainer() != ContainerSlotType.DYNAMIC_CONTAINER) {
            return;
        }
        Integer dynamicId = slotData.getDynamicId();
        if (dynamicId == null) {
            return;
        }
        NetworkMapping.BundleHolderRef ref = NetworkMapping.findBundleHolder(player, dynamicId);
        if (ref != null) {
            refs.add(ref);
        }
    }

    private static void syncAffectedInventories(Player actor, Set<Inventory> inventories) {
        for (Inventory inventory : inventories) {
            try {
                InventoryObserverSync.syncOtherViewers(actor, inventory);
            } catch (Throwable t) {
                log.debug("{}: failed to sync observers for inventory {}", actor.getName(), inventory.getClass().getName(), t);
            }
        }
    }

    private static void syncAffectedBundleOuters(Player actor, Set<NetworkMapping.BundleHolderRef> refs) {
        for (NetworkMapping.BundleHolderRef ref : refs) {
            try {
                // Cascade saveNBT from leaf to root so the root bundle's storage NBT
                // reflects every nested change before the outer slot is re-sent.
                List<ItemBundle> chain = ref.chain();
                for (int i = chain.size() - 1; i >= 0; i--) {
                    chain.get(i).saveNBT();
                }
                InventoryObserverSync.resendOuterSlot(actor, ref.outer(), ref.outerSlot());
            } catch (Throwable t) {
                log.debug("{}: failed to sync bundle outer slot", actor.getName(), t);
            }
        }
    }

    private static List<ItemStackResponseContainer> compactContainers(List<ItemStackResponseContainer> containers) {
        LinkedHashMap<FullContainerName, LinkedHashMap<Long, ItemStackResponseSlot>> merged = new LinkedHashMap<>();

        for (ItemStackResponseContainer container : containers) {
            FullContainerName containerName = container.getContainerName() != null
                    ? container.getContainerName()
                    : new FullContainerName(container.getContainer(), null);
            LinkedHashMap<Long, ItemStackResponseSlot> items = merged.computeIfAbsent(containerName, ignored -> new LinkedHashMap<>());
            for (ItemStackResponseSlot slot : container.getItems()) {
                // 用 (slot<<32)|hotbarSlot 作为组合 key，避免 Objects.hash 碰撞导致
                // 同一容器不同 slot 的响应被相互覆盖（这会让客户端丢失更新并回滚对应 slot）。
                long key = ((long) slot.getSlot() << 32) | (slot.getHotbarSlot() & 0xFFFFFFFFL);
                items.put(key, slot);
            }
        }

        List<ItemStackResponseContainer> compacted = new ArrayList<>(merged.size());
        for (var entry : merged.entrySet()) {
            compacted.add(new ItemStackResponseContainer(
                    entry.getKey().getContainer(),
                    new ArrayList<>(entry.getValue().values()),
                    entry.getKey()
            ));
        }
        return compacted;
    }
}
