package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.inventory.Inventory;
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
            boolean error = false;

            if (log.isInfoEnabled()) {
                StringBuilder types = new StringBuilder();
                for (int i = 0; i < actions.length; i++) {
                    if (i > 0) types.append(',');
                    types.append(actions[i].getType());
                }
                log.info("{}: handling item stack request id={} actions=[{}]",
                        player.getName(), request.getRequestId(), types);
            }

            for (int i = 0; i < actions.length; i++) {
                ItemStackRequestAction action = actions[i];
                context.setCurrentActionIndex(i);
                affectedInventories.addAll(resolveAffectedInventories(player, action));
                affectedBundleOuters.addAll(resolveAffectedBundleOuters(player, action));

                ItemStackRequestActionProcessor<ItemStackRequestAction> processor =
                        (ItemStackRequestActionProcessor<ItemStackRequestAction>) PROCESSORS.get(action.getType());

                if (processor == null) {
                    log.warn("{}: unhandled item stack request action {}", player.getName(), action.getType());
                    error = true;
                    break;
                }

                try {
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
            }
        } catch (Throwable t) {
            log.debug("{}: failed to resolve affected inventories for action {}", player.getName(), action.getType(), t);
        }

        return affected;
    }

    private static void addAffectedInventory(Set<Inventory> affected, Player player, ItemStackRequestSlotData slotData) {
        Inventory inventory = NetworkMapping.getInventory(player, slotData.getContainer(), slotData.getDynamicId());
        if (inventory != null) {
            affected.add(inventory);
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
        LinkedHashMap<FullContainerName, LinkedHashMap<Integer, ItemStackResponseSlot>> merged = new LinkedHashMap<>();

        for (ItemStackResponseContainer container : containers) {
            FullContainerName containerName = container.getContainerName() != null
                    ? container.getContainerName()
                    : new FullContainerName(container.getContainer(), null);
            LinkedHashMap<Integer, ItemStackResponseSlot> items = merged.computeIfAbsent(containerName, ignored -> new LinkedHashMap<>());
            for (ItemStackResponseSlot slot : container.getItems()) {
                items.put(Objects.hash(slot.getSlot(), slot.getHotbarSlot()), slot);
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
