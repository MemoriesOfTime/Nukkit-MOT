package cn.nukkit.inventory.request;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBundle;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Translate between the network-level {@link ContainerSlotType} / slot index pair
 * and the server-side {@link Inventory} / internal slot pair. Used throughout the
 * ItemStackRequest processor chain to resolve action targets.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>)
 */
public final class NetworkMapping {

    private NetworkMapping() {
    }

    /**
     * Resolve the server-side Inventory referenced by a network-level slot type.
     * The dynamicId parameter is reserved for DYNAMIC_CONTAINER (bundles); callers
     * may pass null when the action does not carry one.
     */
    @Nullable
    public static Inventory getInventory(Player player, ContainerSlotType type, @Nullable Integer dynamicId) {
        Inventory topWindow = player.getTopWindow().orElse(null);
        if (topWindow instanceof FakeBlockUIComponent fakeUI && isSlotTypeCompatibleWithFakeUI(fakeUI, type)) {
            return fakeUI;
        }

        PlayerUIInventory ui = player.getUIInventory();
        return switch (type) {
            case CURSOR -> ui.getCursorInventory();
            case CREATED_OUTPUT -> ui; // slot 50 of PlayerUIInventory hosts the created output
            case CRAFTING_INPUT, CRAFTING_OUTPUT -> {
                // Crafting tables are tracked through Player.craftingGrid rather
                // than a normal top window. Only use topWindow when it is already a
                // crafting-grid view; otherwise return the player's active grid.
                yield topWindow instanceof CraftingGrid ? topWindow : player.getCraftingGrid();
            }
            case HOTBAR, INVENTORY, HOTBAR_AND_INVENTORY, ARMOR -> player.getInventory();
            case OFFHAND -> player.getOffhandInventory();
            case HORSE_EQUIP -> resolveHorseInventory(player);
            case ANVIL_INPUT, ANVIL_MATERIAL, ANVIL_RESULT -> player.getWindowById(Player.ANVIL_WINDOW_ID);
            case ENCHANTING_INPUT, ENCHANTING_MATERIAL -> player.getWindowById(Player.ENCHANT_WINDOW_ID);
            case GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, GRINDSTONE_RESULT -> player.getWindowById(Player.GRINDSTONE_WINDOW_ID);
            case SMITHING_TABLE_INPUT, SMITHING_TABLE_MATERIAL, SMITHING_TABLE_RESULT, SMITHING_TABLE_TEMPLATE ->
                    player.getWindowById(Player.SMITHING_WINDOW_ID);
            case LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, LOOM_RESULT -> player.getWindowById(Player.LOOM_WINDOW_ID);
            case STONECUTTER_INPUT, STONECUTTER_RESULT -> player.getWindowById(Player.STONECUTTER_WINDOW_ID);
            case CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_RESULT ->
                    topWindow instanceof CartographyTableInventory ? topWindow : null;
            case BEACON_PAYMENT -> player.getWindowById(Player.BEACON_WINDOW_ID);
            case COMPOUND_CREATOR_INPUT, COMPOUND_CREATOR_OUTPUT,
                 ELEMENT_CONSTRUCTOR_OUTPUT,
                 MATERIAL_REDUCER_INPUT, MATERIAL_REDUCER_OUTPUT,
                 LAB_TABLE_INPUT -> topWindow;
            case TRADE_INGREDIENT_1, TRADE_INGREDIENT_2, TRADE_RESULT,
                 TRADE2_INGREDIENT_1, TRADE2_INGREDIENT_2, TRADE2_RESULT ->
                    topWindow;
            case FURNACE_FUEL, FURNACE_INGREDIENT, FURNACE_RESULT,
                 BLAST_FURNACE_INGREDIENT, SMOKER_INGREDIENT,
                 BREWING_INPUT, BREWING_RESULT, BREWING_FUEL,
                 SHULKER_BOX, BARREL,
                 LEVEL_ENTITY, CRAFTER_BLOCK_CONTAINER -> topWindow;
            case DYNAMIC_CONTAINER -> resolveDynamicContainer(player, dynamicId);
            default -> null;
        };
    }

    private static boolean isSlotTypeCompatibleWithFakeUI(FakeBlockUIComponent fakeUI, ContainerSlotType type) {
        return switch (fakeUI.getFakeBlockType()) {
            case ANVIL -> type == ContainerSlotType.ANVIL_INPUT
                    || type == ContainerSlotType.ANVIL_MATERIAL
                    || type == ContainerSlotType.ANVIL_RESULT;
            case ENCHANT_TABLE -> type == ContainerSlotType.ENCHANTING_INPUT
                    || type == ContainerSlotType.ENCHANTING_MATERIAL;
            case BEACON -> type == ContainerSlotType.BEACON_PAYMENT;
            case LOOM -> type == ContainerSlotType.LOOM_INPUT
                    || type == ContainerSlotType.LOOM_DYE
                    || type == ContainerSlotType.LOOM_MATERIAL
                    || type == ContainerSlotType.LOOM_RESULT;
            case SMITHING_TABLE -> type == ContainerSlotType.SMITHING_TABLE_INPUT
                    || type == ContainerSlotType.SMITHING_TABLE_MATERIAL
                    || type == ContainerSlotType.SMITHING_TABLE_RESULT
                    || type == ContainerSlotType.SMITHING_TABLE_TEMPLATE;
            case GRINDSTONE -> type == ContainerSlotType.GRINDSTONE_INPUT
                    || type == ContainerSlotType.GRINDSTONE_ADDITIONAL
                    || type == ContainerSlotType.GRINDSTONE_RESULT;
            case STONECUTTER -> type == ContainerSlotType.STONECUTTER_INPUT
                    || type == ContainerSlotType.STONECUTTER_RESULT;
            case CARTOGRAPHY -> type == ContainerSlotType.CARTOGRAPHY_INPUT
                    || type == ContainerSlotType.CARTOGRAPHY_ADDITIONAL
                    || type == ContainerSlotType.CARTOGRAPHY_RESULT;
            default -> false;
        };
    }

    /**
     * Convert a network-level slot index to the server-side internal slot index
     * for the given container type.
     * <p>
     * For a player's main inventory, Bedrock uses the same indices as the
     * server-side native slot numbering: HOTBAR 0-8, INVENTORY 9-35 and
     * HOTBAR_AND_INVENTORY 0-35 are all identity mappings — the INVENTORY
     * values already include the 9-slot hotbar offset, so we must NOT add 9
     * again (doing so shifts every main-inventory click by 9 slots and makes
     * equipment / hotbar state diverge from the client).
     */
    public static int toInternalSlot(ContainerSlotType type, int networkSlot) {
        return switch (type) {
            case HOTBAR, HOTBAR_AND_INVENTORY, INVENTORY -> networkSlot;
            case ARMOR -> networkSlot + 36;
            case CURSOR, OFFHAND, BEACON_PAYMENT -> 0;
            case CREATED_OUTPUT -> 50;
            case CRAFTING_INPUT, CRAFTING_OUTPUT -> {
                if (networkSlot >= 32 && networkSlot <= 40) {
                    yield networkSlot - 32;
                }
                if (networkSlot >= 28 && networkSlot <= 31) {
                    yield networkSlot - 28;
                }
                yield networkSlot;
            }
            case ANVIL_INPUT, ANVIL_MATERIAL, ANVIL_RESULT -> mapRange(networkSlot, 1, 3);
            case STONECUTTER_INPUT, STONECUTTER_RESULT -> mapRange(networkSlot, 3, 4);
            case LOOM_INPUT, LOOM_DYE, LOOM_MATERIAL, LOOM_RESULT -> mapRange(networkSlot, 9, 12);
            case CARTOGRAPHY_INPUT, CARTOGRAPHY_ADDITIONAL, CARTOGRAPHY_RESULT -> mapRange(networkSlot, 12, 14);
            case ENCHANTING_INPUT, ENCHANTING_MATERIAL -> mapRange(networkSlot, 14, 15);
            case GRINDSTONE_INPUT, GRINDSTONE_ADDITIONAL, GRINDSTONE_RESULT -> mapRange(networkSlot, 16, 18);
            case SMITHING_TABLE_INPUT, SMITHING_TABLE_MATERIAL, SMITHING_TABLE_TEMPLATE, SMITHING_TABLE_RESULT ->
                    mapRange(networkSlot, 51, 54);
            // Villager trade inventory places the two input slots at fixed
            // physical indices; map both the 1.16+ TRADE2_* and legacy TRADE_*
            // aliases to the same slots.
            case TRADE_INGREDIENT_1, TRADE2_INGREDIENT_1 -> 0;
            case TRADE_INGREDIENT_2, TRADE2_INGREDIENT_2 -> 1;
            default -> networkSlot;
        };
    }

    private static int mapRange(int networkSlot, int firstNetworkSlot, int lastNetworkSlot) {
        if (networkSlot >= firstNetworkSlot && networkSlot <= lastNetworkSlot) {
            return networkSlot - firstNetworkSlot;
        }
        return networkSlot;
    }

    /**
     * Convert a server-side internal slot index back to the network-level slot
     * index. Used when constructing ItemStackResponseSlot entries.
     */
    public static int toNetworkSlot(Inventory inventory, int internalSlot) {
        if (inventory instanceof PlayerInventory) {
            // HOTBAR (0-8) and INVENTORY (9-35) are identity-mapped on the wire
            // — see toInternalSlot. Armor slots (36-39) are published through
            // MobArmorEquipment / the ARMOR container at indices 0-3.
            if (internalSlot < 36) {
                return internalSlot;
            }
            return internalSlot - 36;
        }
        return internalSlot;
    }

    /**
     * Derive the network-level ContainerSlotType for a given inventory + internal
     * slot. Used by the ItemStackRequest response path to label the slots the
     * server is echoing back.
     */
    public static ContainerSlotType getSlotType(Inventory inventory, int internalSlot) {
        if (inventory instanceof PlayerCursorInventory) {
            return ContainerSlotType.CURSOR;
        }
        if (inventory instanceof PlayerInventory) {
            if (internalSlot < 9) {
                return ContainerSlotType.HOTBAR;
            }
            if (internalSlot < 36) {
                return ContainerSlotType.INVENTORY;
            }
            return ContainerSlotType.ARMOR;
        }
        if (inventory instanceof PlayerUIInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.CURSOR;
                case 50 -> ContainerSlotType.CREATED_OUTPUT;
                default -> ContainerSlotType.CRAFTING_INPUT;
            };
        }
        if (inventory instanceof AnvilInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.ANVIL_INPUT;
                case 1 -> ContainerSlotType.ANVIL_MATERIAL;
                default -> ContainerSlotType.ANVIL_RESULT;
            };
        }
        if (inventory instanceof EnchantInventory) {
            return internalSlot == 0 ? ContainerSlotType.ENCHANTING_INPUT : ContainerSlotType.ENCHANTING_MATERIAL;
        }
        if (inventory instanceof GrindstoneInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.GRINDSTONE_INPUT;
                case 1 -> ContainerSlotType.GRINDSTONE_ADDITIONAL;
                default -> ContainerSlotType.GRINDSTONE_RESULT;
            };
        }
        if (inventory instanceof SmithingInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.SMITHING_TABLE_INPUT;
                case 1 -> ContainerSlotType.SMITHING_TABLE_MATERIAL;
                case 2 -> ContainerSlotType.SMITHING_TABLE_TEMPLATE;
                default -> ContainerSlotType.SMITHING_TABLE_RESULT;
            };
        }
        if (inventory instanceof LoomInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.LOOM_INPUT;
                case 1 -> ContainerSlotType.LOOM_DYE;
                case 2 -> ContainerSlotType.LOOM_MATERIAL;
                default -> ContainerSlotType.LOOM_RESULT;
            };
        }
        if (inventory instanceof StonecutterInventory) {
            return internalSlot == 0 ? ContainerSlotType.STONECUTTER_INPUT : ContainerSlotType.STONECUTTER_RESULT;
        }
        if (inventory instanceof CartographyTableInventory) {
            return switch (internalSlot) {
                case 0 -> ContainerSlotType.CARTOGRAPHY_INPUT;
                case 1 -> ContainerSlotType.CARTOGRAPHY_ADDITIONAL;
                default -> ContainerSlotType.CARTOGRAPHY_RESULT;
            };
        }
        if (inventory instanceof BeaconInventory) {
            return ContainerSlotType.BEACON_PAYMENT;
        }
        if (inventory instanceof CraftingGrid) {
            return internalSlot == 0 ? ContainerSlotType.CRAFTING_OUTPUT : ContainerSlotType.CRAFTING_INPUT;
        }
        if (inventory instanceof BundleInventory) {
            return ContainerSlotType.DYNAMIC_CONTAINER;
        }
        if (inventory instanceof CrafterInventory) {
            return ContainerSlotType.CRAFTER_BLOCK_CONTAINER;
        }
        if (inventory instanceof HorseInventory) {
            return internalSlot <= HorseInventory.SLOT_ARMOR
                    ? ContainerSlotType.HORSE_EQUIP
                    : ContainerSlotType.HOTBAR_AND_INVENTORY;
        }
        return ContainerSlotType.HOTBAR_AND_INVENTORY;
    }

    @Nullable
    private static Inventory resolveHorseInventory(Player player) {
        Inventory topWindow = player.getTopWindow().orElse(null);
        if (topWindow instanceof HorseInventory) {
            // Explicit horse windows resolve here even when the player is not
            // currently mounted; the riding lookup below is only the fallback
            // for the current mount-backed interaction flow.
            return topWindow;
        }

        Entity riding = player.getRiding();
        if (riding instanceof InventoryHolder inventoryHolder) {
            Inventory inv = inventoryHolder.getInventory();
            if (inv instanceof HorseInventory) {
                return inv;
            }
        }

        return null;
    }

    @Nullable
    private static Inventory resolveDynamicContainer(Player player, @Nullable Integer dynamicId) {
        if (dynamicId == null) {
            return null;
        }

        LinkedHashSet<Inventory> inventories = new LinkedHashSet<>();
        player.getTopWindow().ifPresent(inventories::add);
        inventories.add(player.getInventory());
        inventories.add(player.getOffhandInventory());
        inventories.add(player.getCursorInventory());
        inventories.add(player.getCraftingGrid());

        Set<Integer> visitedBundleIds = new LinkedHashSet<>();
        for (Inventory inventory : inventories) {
            Inventory resolved = findBundleInventory(inventory, dynamicId, visitedBundleIds);
            if (resolved != null) {
                return resolved;
            }
        }

        return null;
    }

    @Nullable
    private static Inventory findBundleInventory(@Nullable Inventory inventory, int dynamicId, Set<Integer> visitedBundleIds) {
        if (inventory == null) {
            return null;
        }

        for (Item item : inventory.getContents().values()) {
            if (!(item instanceof ItemBundle bundle)) {
                continue;
            }

            int bundleId = bundle.getBundleId();
            if (!visitedBundleIds.add(bundleId)) {
                continue;
            }
            if (bundleId == dynamicId) {
                return bundle.getInventory();
            }

            Inventory nested = findBundleInventory(bundle.getInventory(), dynamicId, visitedBundleIds);
            if (nested != null) {
                return nested;
            }
        }

        return null;
    }

    /**
     * Result of {@link #findBundleHolder}. {@code outer}/{@code outerSlot} point to
     * the non-bundle inventory + slot index that holds the root {@link ItemBundle};
     * {@code chain} lists the bundles traversed from root to the target bundle
     * (inclusive). Used by the ItemStackRequest path to cascade saveNBT and re-send
     * the outer slot to viewers.
     */
    public record BundleHolderRef(Inventory outer, int outerSlot, List<ItemBundle> chain) {
    }

    /**
     * Locate the non-bundle inventory holding the bundle with the given dynamic id.
     * Search order mirrors {@link #resolveDynamicContainer}: topWindow, player
     * inventory, offhand, cursor, crafting grid.
     */
    @Nullable
    public static BundleHolderRef findBundleHolder(Player player, int dynamicId) {
        LinkedHashSet<Inventory> inventories = new LinkedHashSet<>();
        player.getTopWindow().ifPresent(inventories::add);
        inventories.add(player.getInventory());
        inventories.add(player.getOffhandInventory());
        inventories.add(player.getCursorInventory());
        inventories.add(player.getCraftingGrid());

        Set<Integer> visitedBundleIds = new LinkedHashSet<>();
        for (Inventory inventory : inventories) {
            BundleHolderRef ref = findBundleHolder(inventory, dynamicId, visitedBundleIds, new ArrayList<>());
            if (ref != null) {
                return ref;
            }
        }
        return null;
    }

    @Nullable
    private static BundleHolderRef findBundleHolder(
            @Nullable Inventory inventory,
            int dynamicId,
            Set<Integer> visitedBundleIds,
            List<ItemBundle> chainSoFar
    ) {
        if (inventory == null) {
            return null;
        }

        for (var entry : inventory.getContents().entrySet()) {
            Item item = entry.getValue();
            if (!(item instanceof ItemBundle bundle)) {
                continue;
            }

            int bundleId = bundle.getBundleId();
            if (!visitedBundleIds.add(bundleId)) {
                continue;
            }

            // Build a chain that includes this bundle; used for saveNBT cascade.
            List<ItemBundle> nextChain = new ArrayList<>(chainSoFar.size() + 1);
            nextChain.addAll(chainSoFar);
            nextChain.add(bundle);

            if (bundleId == dynamicId) {
                // Intermediate result: outer here may be a BundleInventory (nested
                // case); the caller at the top level will replace it with the real
                // outer inventory before returning.
                return new BundleHolderRef(inventory, entry.getKey(), nextChain);
            }

            BundleHolderRef nested = findBundleHolder(bundle.getInventory(), dynamicId, visitedBundleIds, nextChain);
            if (nested != null) {
                // At the top level (first non-bundle inventory), replace the inner
                // outer with the real non-bundle outer + its slot index.
                if (chainSoFar.isEmpty()) {
                    return new BundleHolderRef(inventory, entry.getKey(), nested.chain());
                }
                return nested;
            }
        }

        return null;
    }
}
