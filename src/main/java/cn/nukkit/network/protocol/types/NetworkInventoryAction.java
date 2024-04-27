package cn.nukkit.network.protocol.types;

import cn.nukkit.Player;
import cn.nukkit.inventory.*;
import cn.nukkit.inventory.transaction.action.*;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.network.protocol.InventoryTransactionPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

import java.util.Optional;

/**
 * @author CreeperFace
 */
@ToString
public class NetworkInventoryAction {

    public static final int SOURCE_CONTAINER = 0;

    public static final int SOURCE_WORLD = 2;
    public static final int SOURCE_CREATIVE = 3;
    public static final int SOURCE_TODO = 99999;
    public static final int SOURCE_CRAFT_SLOT = 100;

    /**
     * Fake window IDs for the SOURCE_TODO type (99999)
     * <p>
     * These identifiers are used for inventory source types which are not currently implemented server-side in MCPE.
     * As a general rule of thumb, anything that doesn't have a permanent inventory is client-side. These types are
     * to allow servers to track what is going on in client-side windows.
     * <p>
     * Expect these to change in the future.
     */
    public static final int SOURCE_TYPE_CRAFTING_ADD_INGREDIENT = -2;
    public static final int SOURCE_TYPE_CRAFTING_REMOVE_INGREDIENT = -3;
    public static final int SOURCE_TYPE_CRAFTING_RESULT = -4;
    public static final int SOURCE_TYPE_CRAFTING_USE_INGREDIENT = -5;

    public static final int SOURCE_TYPE_ANVIL_INPUT = -10;
    public static final int SOURCE_TYPE_ANVIL_MATERIAL = -11;
    public static final int SOURCE_TYPE_ANVIL_RESULT = -12;
    public static final int SOURCE_TYPE_ANVIL_OUTPUT = -13;

    public static final int SOURCE_TYPE_ENCHANT_INPUT = -15;
    public static final int SOURCE_TYPE_ENCHANT_MATERIAL = -16;
    public static final int SOURCE_TYPE_ENCHANT_OUTPUT = -17;

    public static final int SOURCE_TYPE_BEACON = -24;

    public static final int SOURCE_TYPE_TRADING_OUTPUT = -30;
    public static final int SOURCE_TYPE_TRADING_INPUT_1 = -31;
    public static final int SOURCE_TYPE_TRADING_INPUT_2 = -32;
    public static final int SOURCE_TYPE_TRADING_USE_INPUTS = -33;

    /**
     * Any client-side window dropping its contents when the player closes it
     */
    public static final int SOURCE_TYPE_CONTAINER_DROP_CONTENTS = -100;


    public int sourceType;
    public int windowId;
    public long unknown;
    public int inventorySlot;
    public Item oldItem;
    public Item newItem;
    public int stackNetworkId;

    public NetworkInventoryAction read(InventoryTransactionPacket packet) {
        this.sourceType = (int) packet.getUnsignedVarInt();

        switch (this.sourceType) {
            case SOURCE_CONTAINER:
                this.windowId = packet.getVarInt();
                break;
            case SOURCE_WORLD:
                this.unknown = packet.getUnsignedVarInt();
                break;
            case SOURCE_CREATIVE:
                break;
            case SOURCE_CRAFT_SLOT:
            case SOURCE_TODO:
                this.windowId = packet.getVarInt();

                switch (this.windowId) {
                    case SOURCE_TYPE_CRAFTING_RESULT:
                    case SOURCE_TYPE_CRAFTING_USE_INGREDIENT:
                        packet.isCraftingPart = true;
                        break;
                    case SOURCE_TYPE_ENCHANT_INPUT:
                    case SOURCE_TYPE_ENCHANT_OUTPUT:
                    case SOURCE_TYPE_ENCHANT_MATERIAL:
                        packet.isEnchantingPart = true;
                        break;
                    case SOURCE_TYPE_ANVIL_INPUT:
                    case SOURCE_TYPE_ANVIL_MATERIAL:
                    case SOURCE_TYPE_ANVIL_RESULT:
                        packet.isRepairItemPart = true;
                        break;
                    case SOURCE_TYPE_TRADING_INPUT_1:
                    case SOURCE_TYPE_TRADING_INPUT_2:
                    case SOURCE_TYPE_TRADING_USE_INPUTS:
                    case SOURCE_TYPE_TRADING_OUTPUT:
                        packet.isTradeItemPart = true;
                        break;
                }
                break;
        }

        this.inventorySlot = (int) packet.getUnsignedVarInt();
        this.oldItem = packet.getSlot(packet.protocol);
        this.newItem = packet.getSlot(packet.protocol);

        if (packet.hasNetworkIds && packet.protocol >= 407 && packet.protocol < ProtocolInfo.v1_16_220) {
            this.stackNetworkId = packet.getVarInt();
        }

        return this;
    }

    public void write(InventoryTransactionPacket packet) {
        packet.putUnsignedVarInt(this.sourceType);

        switch (this.sourceType) {
            case SOURCE_CONTAINER:
                packet.putVarInt(this.windowId);
                break;
            case SOURCE_WORLD:
                packet.putUnsignedVarInt(this.unknown);
                break;
            case SOURCE_CREATIVE:
                break;
            case SOURCE_CRAFT_SLOT:
            case SOURCE_TODO:
                packet.putVarInt(this.windowId);
                break;
        }

        packet.putUnsignedVarInt(this.inventorySlot);
        packet.putSlot(packet.protocol, this.oldItem);
        packet.putSlot(packet.protocol, this.newItem);

        if (packet.hasNetworkIds && packet.protocol >= 407 && packet.protocol < ProtocolInfo.v1_16_220) {
            packet.putVarInt(this.stackNetworkId);
        }
    }

    public InventoryAction createInventoryAction(Player player) {
        switch (this.sourceType) {
            case SOURCE_CONTAINER:
                if (this.windowId == ContainerIds.ARMOR) {
                    this.inventorySlot += 36;
                    this.windowId = ContainerIds.INVENTORY;
                    if (this.newItem == null ||
                            (this.inventorySlot == 36 && !this.newItem.canBePutInHelmetSlot() && !this.oldItem.canBePutInHelmetSlot()) ||
                            (this.inventorySlot == 37 && !this.newItem.isChestplate() && !this.oldItem.isChestplate()) ||
                            (this.inventorySlot == 38 && !this.newItem.isLeggings() && !this.oldItem.isLeggings()) ||
                            (this.inventorySlot == 39 && !this.newItem.isBoots()) && !this.oldItem.isBoots()) {
                        player.getServer().getLogger().error("Player " + player.getName() + " tried to set an invalid armor item");
                        return null;
                    }
                }

                // ID 124 with slot 14/15 is enchant inventory
                if (this.windowId == ContainerIds.UI && player.protocol >= ProtocolInfo.v1_16_0) {
                    switch (this.inventorySlot) {
                        case PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT:
                            if (player.getWindowById(Player.ANVIL_WINDOW_ID) != null) {
                                this.windowId = Player.ANVIL_WINDOW_ID;
                                this.inventorySlot = 2;
                            }
                            break;
                        case EnchantInventory.ENCHANT_INPUT_ITEM_UI_SLOT:
                            if (player.getWindowById(Player.ENCHANT_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have enchant window open");
                                return null;
                            }
                            this.windowId = Player.ENCHANT_WINDOW_ID;
                            this.inventorySlot = 0;
                            // TODO, check if unenchanted item and send EnchantOptionsPacket
                            break;
                        case EnchantInventory.ENCHANT_REAGENT_UI_SLOT:
                            if (player.getWindowById(Player.ENCHANT_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have enchant window open");
                                return null;
                            }
                            this.windowId = Player.ENCHANT_WINDOW_ID;
                            this.inventorySlot = 1;
                            break;
                        case AnvilInventory.ANVIL_INPUT_UI_SLOT:
                            if (player.getWindowById(Player.ANVIL_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have anvil window open");
                                return null;
                            }
                            this.windowId = Player.ANVIL_WINDOW_ID;
                            this.inventorySlot = 0;
                            break;
                        case AnvilInventory.ANVIL_MATERIAL_UI_SLOT:
                            if (player.getWindowById(Player.ANVIL_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have anvil window open");
                                return null;
                            }
                            this.windowId = Player.ANVIL_WINDOW_ID;
                            this.inventorySlot = 1;
                            break;
                        //124:51 -> 6:0
                        case SmithingInventory.SMITHING_EQUIPMENT_UI_SLOT:
                            if (player.getWindowById(Player.SMITHING_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have smithing table window open");
                                return null;
                            }
                            this.windowId = Player.SMITHING_WINDOW_ID;
                            this.inventorySlot = 0;
                            break;
                        //124:52 -> 6:1
                        case SmithingInventory.SMITHING_INGREDIENT_UI_SLOT:
                            if (player.getWindowById(Player.SMITHING_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have smithing table window open");
                                return null;
                            }
                            this.windowId = Player.SMITHING_WINDOW_ID;
                            this.inventorySlot = 1;
                            break;
                        //124:53 -> 6:2
                        case SmithingInventory.SMITHING_TEMPLATE_UI_SLOT:
                            if (player.getWindowById(Player.SMITHING_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have smithing table window open");
                                return null;
                            }
                            this.windowId = Player.SMITHING_WINDOW_ID;
                            this.inventorySlot = 2;
                            break;
                        //124:4 -> 500:0
                        case TradeInventory.TRADE_INPUT1_UI_SLOT:
                            if (player.getWindowById(Player.TRADE_WINDOW_ID) == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have trade window open");
                                return null;
                            }
                            this.windowId = Player.TRADE_WINDOW_ID;
                            this.inventorySlot = 0;
                            break;
                        //124:5 -> 500:1
                        case TradeInventory.TRADE_INPUT2_UI_SLOT:
                            if (player.getTradeInventory() == null) {
                                player.getServer().getLogger().error("Player " + player.getName() + " does not have trade window open");
                                return null;
                            }
                            this.windowId = Player.TRADE_WINDOW_ID;
                            this.inventorySlot = 1;
                            break;
                    }
                }

                Inventory window = player.getWindowById(this.windowId);
                if (window != null) {
                    if (window instanceof BaseInventory) {
                        if (((BaseInventory) window).destroyed) {
                            player.getServer().getLogger().debug("Player " + player.getName() + " has an invalid open container with window ID " + this.windowId);
                            return null;
                        }
                    }
                    return new SlotChangeAction(window, this.inventorySlot, this.oldItem, this.newItem);
                }

                player.getServer().getLogger().debug("Player " + player.getName() + " has no open container with window ID " + this.windowId);
                return null;
            case SOURCE_WORLD:
                if (this.inventorySlot != InventoryTransactionPacket.ACTION_MAGIC_SLOT_DROP_ITEM) {
                    player.getServer().getLogger().debug("Only expecting drop-item world actions from the client!");
                    return null;
                }

                return new DropItemAction(this.oldItem, this.newItem);
            case SOURCE_CREATIVE:
                if (!player.isCreative()) {
                    return null;
                }

                int type;

                switch (this.inventorySlot) {
                    case InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_DELETE_ITEM:
                        type = CreativeInventoryAction.TYPE_DELETE_ITEM;
                        break;
                    case InventoryTransactionPacket.ACTION_MAGIC_SLOT_CREATIVE_CREATE_ITEM:
                        type = CreativeInventoryAction.TYPE_CREATE_ITEM;
                        break;
                    default:
                        player.getServer().getLogger().debug("Unexpected creative action type " + this.inventorySlot);
                        return null;
                }

                return new CreativeInventoryAction(this.oldItem, this.newItem, type);
            case SOURCE_CRAFT_SLOT:
            case SOURCE_TODO:
                switch (this.windowId) {
                    case SOURCE_TYPE_CRAFTING_ADD_INGREDIENT:
                    case SOURCE_TYPE_CRAFTING_REMOVE_INGREDIENT:
                        return new SlotChangeAction(player.getCraftingGrid(), this.inventorySlot, this.oldItem, this.newItem);
                    case SOURCE_TYPE_CONTAINER_DROP_CONTENTS:
                        Optional<Inventory> inventory = player.getTopWindow();
                        if (!inventory.isPresent()) {
                            // No window open?
                            return null;
                        }
                        return new SlotChangeAction(inventory.get(), this.inventorySlot, this.oldItem, this.newItem);
                    case SOURCE_TYPE_CRAFTING_RESULT:
                        return new CraftingTakeResultAction(this.oldItem, this.newItem);
                    case SOURCE_TYPE_CRAFTING_USE_INGREDIENT:
                        return new CraftingTransferMaterialAction(this.oldItem, this.newItem, this.inventorySlot);
                }

                //-13 -10 anvil actions
                if (this.windowId >= SOURCE_TYPE_ANVIL_OUTPUT && this.windowId <= SOURCE_TYPE_ANVIL_INPUT) {
                    Inventory inv;
                    if ((inv = player.getWindowById(Player.ANVIL_WINDOW_ID)) instanceof AnvilInventory) {
                        AnvilInventory anvil = (AnvilInventory) inv;
                        switch (this.windowId) {
                            case SOURCE_TYPE_ANVIL_INPUT:
                            case SOURCE_TYPE_ANVIL_MATERIAL:
                            case SOURCE_TYPE_ANVIL_RESULT:
                                return new RepairItemAction(this.oldItem, this.newItem, this.windowId);
                        }
                        return new SlotChangeAction(anvil, this.inventorySlot, this.oldItem, this.newItem);
                    } else if (player.getWindowById(Player.SMITHING_WINDOW_ID) instanceof SmithingInventory) {
                        switch (this.windowId) {
                            case SOURCE_TYPE_ANVIL_INPUT:
                            case SOURCE_TYPE_ANVIL_MATERIAL:
                            case SOURCE_TYPE_ANVIL_OUTPUT:
                            case SOURCE_TYPE_ANVIL_RESULT:
                                return new SmithingItemAction(this.oldItem, this.newItem, this.inventorySlot);
                        }
                    } else {
                        player.getServer().getLogger().debug("Player " + player.getName() + " has no open anvil or smithing inventory");
                        return null;
                    }
                } else if (this.windowId >= SOURCE_TYPE_ENCHANT_OUTPUT && this.windowId <= SOURCE_TYPE_ENCHANT_INPUT) { //-17 -15
                    Inventory inv = player.getWindowById(Player.ENCHANT_WINDOW_ID);

                    if (!(inv instanceof EnchantInventory)) {
                        player.getServer().getLogger().debug("Player " + player.getName() + " has no open enchant inventory");
                        return null;
                    }
                    EnchantInventory enchant = (EnchantInventory) inv;

                    switch (this.windowId) {
                        case SOURCE_TYPE_ENCHANT_INPUT:
                            if (player.protocol < 407) {
                                if (this.inventorySlot != 0) {
                                    // Input should only be in slot 0
                                    return null;
                                }
                                break;
                            } else {
                                return new EnchantingAction(this.oldItem, this.newItem, SOURCE_TYPE_ENCHANT_INPUT);
                            }
                        case SOURCE_TYPE_ENCHANT_MATERIAL:
                            if (player.protocol < 407) {
                                if (this.inventorySlot != 1) {
                                    // Material should only be in slot 1
                                    return null;
                                }
                            } else {
                                return new EnchantingAction(this.newItem, this.oldItem, SOURCE_TYPE_ENCHANT_MATERIAL); // Mojang ish backwards?
                            }
                            break;
                        case SOURCE_TYPE_ENCHANT_OUTPUT:
                            if (player.protocol < 407) {
                                if (this.inventorySlot != 0) {
                                    // Outputs should only be in slot 0
                                    return null;
                                }
                                if (Item.get(ItemID.DYE, 4).equals(this.newItem, true, false)) {
                                    this.inventorySlot = 2; // Fake slot to store used material
                                    if (this.newItem.getCount() < 1 || this.newItem.getCount() > 3) {
                                        // Invalid material
                                        return null;
                                    }
                                    Item material = enchant.getItem(1);
                                    // Material to take away.
                                    int toRemove = this.newItem.getCount();
                                    if (material.getId() != ItemID.DYE && material.getDamage() != 4 &&
                                            material.getCount() < toRemove) {
                                        // Invalid material or not enough
                                        return null;
                                    }
                                } else {
                                    Item toEnchant = enchant.getItem(0);
                                    Item material = enchant.getItem(1);
                                    if (toEnchant.equals(this.newItem, true, true) &&
                                            (material.getId() == ItemID.DYE && material.getDamage() == 4 || player.isCreative())) {
                                        this.inventorySlot = 3; // Fake slot to store the resultant item

                                        //TODO: Check (old) item has valid enchantments
                                        enchant.setItem(3, this.oldItem, false);
                                    } else {
                                        return null;
                                    }
                                }
                            } else {
                                return new EnchantingAction(this.oldItem, this.newItem, SOURCE_TYPE_ENCHANT_OUTPUT);
                            }
                    }

                    return new SlotChangeAction(enchant, this.inventorySlot, this.oldItem, this.newItem);
                } else if (this.windowId == SOURCE_TYPE_BEACON) {
                    Inventory inv = player.getWindowById(Player.BEACON_WINDOW_ID);

                    if (!(inv instanceof BeaconInventory)) {
                        player.getServer().getLogger().debug("Player " + player.getName() + " has no open beacon inventory");
                        return null;
                    }
                    BeaconInventory beacon = (BeaconInventory) inv;

                    this.inventorySlot = 0;
                    return new SlotChangeAction(beacon, this.inventorySlot, this.oldItem, this.newItem);
                } else if (this.windowId >= SOURCE_TYPE_TRADING_USE_INPUTS && this.windowId <= SOURCE_TYPE_TRADING_OUTPUT) {
                    Inventory inv = player.getWindowById(Player.TRADE_WINDOW_ID);
                    //verify
                    if (!(inv instanceof TradeInventory trade)) {
                        player.getServer().getLogger().debug("Player " + player.getName() + " has no open trade inventory");
                        return null;
                    }
                    return new TradeAction(this.oldItem, this.newItem, this.windowId, trade.getHolder());
                }

                player.getServer().getLogger().debug("Player " + player.getName() + " has no open container with window ID " + this.windowId);
                return null;
            default:
                player.getServer().getLogger().debug("Unknown inventory source type " + this.sourceType);
                return null;
        }
    }
}