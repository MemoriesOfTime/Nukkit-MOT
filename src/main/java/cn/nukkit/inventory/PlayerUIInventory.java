package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.network.protocol.types.ContainerIds;

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
    public void sendSlot(int index, Player... target) {
        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = this.getItem(index);

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
            /*if (p.protocol >= ProtocolInfo.v1_16_0) {
                p.dataPacket(pk);
            }*/
            //https://github.com/CloudburstMC/Nukkit/commit/f96ce6eb90d47ab99ced368dd7129601f14c0b2b
        }
    }

    @Override
    public int getSize() {
        return 51;
    }

    @Override
    public void setSize(int size) {
        throw new UnsupportedOperationException("UI size is immutable");
    }

    @Override
    public Player getHolder() {
        return player;
    }
}
