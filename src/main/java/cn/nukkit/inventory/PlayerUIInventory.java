package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;

import java.util.HashMap;

public class PlayerUIInventory extends BaseInventory {

    private final Player player;

    private final PlayerCursorInventory cursorInventory;
    private final CraftingGrid craftingGrid;
    private final BigCraftingGrid bigCraftingGrid;

    public PlayerUIInventory(Player player) {
        super(player, InventoryType.UI, new HashMap<>(), 54);
        this.player = player;

        this.cursorInventory = new PlayerCursorInventory(this);
        this.craftingGrid = new CraftingGrid(this);
        this.bigCraftingGrid = new BigCraftingGrid(this);
        this.viewers.add(player);
    }

    public PlayerCursorInventory getCursorInventory() {
        return cursorInventory;
    }

    public CraftingGrid getCraftingGrid() {
        return craftingGrid;
    }

    public BigCraftingGrid getBigCraftingGrid() {
        return bigCraftingGrid;
    }

    @Override
    public void onOpen(Player who) {

    }

    @Override
    public void onClose(Player who) {

    }

    @Override
    public void setSize(int size) {
        throw new UnsupportedOperationException("UI size is immutable");
    }

    @Override
    public void sendSlot(int index, Player... target) {
        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = this.getItem(index);

        // v1.21.30+ requires the correct container type in InventorySlotPacket,
        // otherwise the client ignores the update (default is ANVIL_INPUT).
        pk.containerNameData = new FullContainerName(resolveUISlotType(index), null);

        for (Player p : target) {
            if (p == this.getHolder()) {
                pk.inventoryId = ContainerIds.UI;
                if (p.protocol < ProtocolInfo.v1_16_0) {
                    p.dataPacket(pk);
                }
            } else {
                int id;

                if ((id = p.getWindowId(this)) == ContainerIds.NONE) {
                    this.close(p);
                    continue;
                }
                pk.inventoryId = id;
                if (p.protocol < ProtocolInfo.v1_16_0) {
                    p.dataPacket(pk);
                }
            }
            if (p.protocol >= ProtocolInfo.v1_16_0) {
                p.dataPacket(pk);
            }
        }
    }

    @Override
    public void sendContents(Player... target) {
        //sendSlot(0, target); //update cursor slot

        InventoryContentPacket pk = new InventoryContentPacket();
        pk.slots = new Item[this.getSize()];
        for (int i = 0; i < this.getSize(); ++i) {
            pk.slots[i] = this.getItem(i);
        }
        pk.containerNameData = new FullContainerName(ContainerSlotType.CRAFTING_INPUT, null);

        for (Player p : target) {
            if (p == this.getHolder()) {
                pk.inventoryId = ContainerIds.UI;
                if (p.protocol < ProtocolInfo.v1_16_0) {
                    p.dataPacket(pk);
                }
            } else {
                int id;

                if ((id = p.getWindowId(this)) == ContainerIds.NONE) {
                    this.close(p);
                    continue;
                }
                pk.inventoryId = id;
                if (p.protocol < ProtocolInfo.v1_16_0) {
                    p.dataPacket(pk);
                }
            }
            if (p.protocol >= ProtocolInfo.v1_16_0) {
                p.dataPacket(pk);
            }
            //https://github.com/CloudburstMC/Nukkit/commit/f96ce6eb90d47ab99ced368dd7129601f14c0b2b
        }
    }

    private static ContainerSlotType resolveUISlotType(int slot) {
        if (slot == 0) {
            return ContainerSlotType.CURSOR;
        }
        if (slot == PlayerUIComponent.CREATED_ITEM_OUTPUT_UI_SLOT) {
            return ContainerSlotType.CREATED_OUTPUT;
        }
        return switch (slot) {
            case 1 -> ContainerSlotType.ANVIL_INPUT;
            case 2 -> ContainerSlotType.ANVIL_MATERIAL;
            case 3 -> ContainerSlotType.STONECUTTER_INPUT;
            case 9 -> ContainerSlotType.LOOM_INPUT;
            case 10 -> ContainerSlotType.LOOM_DYE;
            case 11 -> ContainerSlotType.LOOM_MATERIAL;
            case 12 -> ContainerSlotType.CARTOGRAPHY_INPUT;
            case 13 -> ContainerSlotType.CARTOGRAPHY_ADDITIONAL;
            case 14 -> ContainerSlotType.ENCHANTING_INPUT;
            case 15 -> ContainerSlotType.ENCHANTING_MATERIAL;
            case 16 -> ContainerSlotType.GRINDSTONE_INPUT;
            case 17 -> ContainerSlotType.GRINDSTONE_ADDITIONAL;
            case 27 -> ContainerSlotType.BEACON_PAYMENT;
            case 51 -> ContainerSlotType.SMITHING_TABLE_INPUT;
            case 52 -> ContainerSlotType.SMITHING_TABLE_MATERIAL;
            case 53 -> ContainerSlotType.SMITHING_TABLE_TEMPLATE;
            default -> ContainerSlotType.CRAFTING_INPUT;
        };
    }

    @Override
    public int getSize() {
        return 51;
    }

    @Override
    public Player getHolder() {
        return player;
    }
}
