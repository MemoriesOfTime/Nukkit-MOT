package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockAir;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.event.entity.EntityArmorChangeEvent;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.player.PlayerItemHeldEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemMap;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacketV113;
import cn.nukkit.network.protocol.v113.ContainerSetSlotPacketV113;

import java.util.Arrays;
import java.util.Collection;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class PlayerInventory extends BaseInventory {

    protected int itemInHandIndex = 0;

    public PlayerInventory(EntityHumanType player) {
        super(player, InventoryType.PLAYER);
    }

    @Override
    public int getSize() {
        return super.getSize() - 4;
    }

    @Override
    public void setSize(int size) {
        super.setSize(size + 4);
        this.sendContents(this.getViewers());
    }

    /**
     * Called when a client equips a hotbar inventorySlot. This method should not be used by plugins.
     * This method will call PlayerItemHeldEvent.
     *
     * @param slot hotbar slot Number of the hotbar slot to equip.
     * @return boolean if the equipment change was successful, false if not.
     */
    public boolean equipItem(int slot) {
        if (!isHotbarSlot(slot)) {
            this.sendContents((Player) this.getHolder());
            return false;
        }

        if (this.getHolder() instanceof Player) {
            Player player = (Player) this.getHolder();
            Item item = this.getItem(slot);
            PlayerItemHeldEvent ev = new PlayerItemHeldEvent(player, item, slot);
            this.getHolder().getLevel().getServer().getPluginManager().callEvent(ev);

            if (ev.isCancelled()) {
                this.sendContents(this.getViewers());
                return false;
            }

            if (player.fishing != null) {
                if (!(item.equals(player.fishing.rod))) {
                    player.stopFishing(false);
                }
            }
            if (player.protocol >= ProtocolInfo.v1_19_50 //TODO check version
                    && item instanceof ItemMap itemMap) {
                itemMap.trySendImage(player);
            }
        }

        this.setHeldItemIndex(slot, false);
        return true;
    }

    private boolean isHotbarSlot(int slot) {
        return slot >= 0 && slot <= this.getHotbarSize();
    }

    public int getHotbarSlotIndex(int index) {
        return index;
    }

    public void setHotbarSlotIndex(int index, int slot) {
    }

    public int getHeldItemIndex() {
        return this.itemInHandIndex;
    }

    public void setHeldItemIndex(int index) {
        setHeldItemIndex(index, true);
    }

    public void setHeldItemIndex(int index, boolean send) {
        if (index >= 0 && index < this.getHotbarSize()) {
            this.itemInHandIndex = index;

            if (this.getHolder() instanceof Player && send) {
                this.sendHeldItem((Player) this.getHolder());
            }

            this.sendHeldItem(this.getHolder().getViewers().values());
        }
    }

    public Item getItemInHand() {
        Item item = this.getItem(this.itemInHandIndex);
        if (item != null) {
            return item;
        } else {
            return new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        }
    }

    public Item getItemInHandFast() {
        Item item = this.getItemFast(this.getHeldItemIndex());
        if (item != null) {
            return item;
        } else {
            return air;
        }
    }

    public boolean setItemInHand(Item item) {
        return this.setItem(this.itemInHandIndex, item);
    }

    public int getHeldItemSlot() {
        return this.itemInHandIndex;
    }

    public void setHeldItemSlot(int slot) {
        if (!isHotbarSlot(slot)) {
            return;
        }

        this.itemInHandIndex = slot;

        if (this.getHolder() instanceof Player) {
            this.sendHeldItem((Player) this.getHolder());
        }

        this.sendHeldItem(this.getViewers());
    }

    public void sendHeldItem(Player... players) {
        Item item = this.getItemInHand();

        MobEquipmentPacket pk = new MobEquipmentPacket();
        pk.item = item;
        pk.inventorySlot = pk.hotbarSlot = this.itemInHandIndex;

        for (Player player : players) {
            pk.eid = this.getHolder().getId();
            if (player.equals(this.getHolder())) {
                pk.eid = player.getId();
                this.sendSlot(this.itemInHandIndex, player);
            }

            player.dataPacket(pk);
        }
    }

    public void sendHeldItemIfNotAir(Player player) {
        Item item = this.getItemInHand();
        if (item.getId() != 0) {
            MobEquipmentPacket pk = new MobEquipmentPacket();
            pk.item = item;
            pk.inventorySlot = pk.hotbarSlot = this.itemInHandIndex;
            pk.eid = player.getId();
            this.sendSlot(this.itemInHandIndex, player);
            player.dataPacket(pk);
        }
    }

    public void sendHeldItem(Collection<Player> players) {
        this.sendHeldItem(players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        EntityHuman holder = this.getHolder();
        if (holder instanceof Player && !((Player) holder).spawned) {
            return;
        }

        if (index >= this.getSize()) {
            this.sendArmorSlot(index, this.getViewers());
            this.sendArmorSlot(index, this.getHolder().getViewers().values());
        } else {
            super.onSlotChange(index, before, send);
        }
    }

    public int getHotbarSize() {
        return 9;
    }

    public Item getArmorItem(int index) {
        return this.getItem(this.getSize() + index);
    }

    public boolean setArmorItem(int index, Item item) {
        return this.setArmorItem(index, item, false);
    }

    public boolean setArmorItem(int index, Item item, boolean ignoreArmorEvents) {
        return this.setItem(this.getSize() + index, item, ignoreArmorEvents);
    }

    public Item getHelmet() {
        return this.getItem(this.getSize());
    }

    public Item getHelmetFast() {
        return this.getItemFast(36);
    }

    public Item getChestplate() {
        return this.getItem(this.getSize() + 1);
    }

    public Item getChestplateFast() {
        return this.getItemFast(37);
    }

    public Item getLeggings() {
        return this.getItem(this.getSize() + 2);
    }

    public Item getLeggingsFast() {
        return this.getItemFast(38);
    }

    public Item getBoots() {
        return this.getItem(this.getSize() + 3);
    }

    public Item getBootsFast() {
        return this.getItemFast(39);
    }

    public boolean setHelmet(Item helmet) {
        return this.setItem(this.getSize(), helmet);
    }

    public boolean setChestplate(Item chestplate) {
        return this.setItem(this.getSize() + 1, chestplate);
    }

    public boolean setLeggings(Item leggings) {
        return this.setItem(this.getSize() + 2, leggings);
    }

    public boolean setBoots(Item boots) {
        return this.setItem(this.getSize() + 3, boots);
    }

    @Override
    public boolean setItem(int index, Item item) {
        return setItem(index, item, true);
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (index < 0 || index >= this.size) {
            return false;
        } else if (item.getId() == 0 || item.getCount() <= 0) {
            return this.clear(index, send);
        }

        if (index >= this.getSize()) { // Armor change
            EntityArmorChangeEvent ev = new EntityArmorChangeEvent(this.getHolder(), this.getItem(index), item, index);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (ev.isCancelled() && this.getHolder() != null) {
                this.sendArmorSlot(index, this.getViewers());
                return false;
            }
            item = ev.getNewItem();
        } else {
            EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent(this.getHolder(), this.getItem(index), item, index);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                this.sendSlot(index, this.getViewers());
                return false;
            }
            item = ev.getNewItem();
        }

        Item old = this.getItem(index);
        this.slots.put(index, item.clone());
        this.onSlotChange(index, old, send);
        return true;
    }

    @Override
    public boolean clear(int index, boolean send) {
        Item old = this.slots.get(index);
        if (old != null) {
            Item item = new ItemBlock(Block.get(BlockID.AIR), null, 0);
            if (index >= this.getSize() && index < this.size) {
                EntityArmorChangeEvent ev = new EntityArmorChangeEvent(this.getHolder(), old, item, index);
                Server.getInstance().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    if (index >= this.size) {
                        this.sendArmorSlot(index, this.getViewers());
                    } else {
                        this.sendSlot(index, this.getViewers());
                    }
                    return false;
                }
                item = ev.getNewItem();
            } else {
                EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent(this.getHolder(), old, item, index);
                Server.getInstance().getPluginManager().callEvent(ev);
                if (ev.isCancelled()) {
                    if (index >= this.size) {
                        this.sendArmorSlot(index, this.getViewers());
                    } else {
                        this.sendSlot(index, this.getViewers());
                    }
                    return false;
                }
                item = ev.getNewItem();
            }

            if (item.getId() != Item.AIR) {
                this.slots.put(index, item.clone());
            } else {
                this.slots.remove(index);
            }

            this.onSlotChange(index, old, send);
        }

        return true;
    }

    public Item[] getArmorContents() {
        Item[] armor = new Item[4];
        for (int i = 0; i < 4; i++) {
            armor[i] = this.getItem(this.getSize() + i);
        }

        return armor;
    }

    @Override
    public void clearAll() {
        int limit = this.getSize() + 4;
        for (int index = 0; index < limit; ++index) {
            this.clear(index);
        }
        getHolder().getOffhandInventory().clearAll();
    }

    public void sendArmorContents(Player player) {
        this.sendArmorContents(new Player[]{player});
    }

    public void sendArmorContents(Player[] players) {
        Item[] armor = this.getArmorContents();

        MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
        pk.eid = this.getHolder().getId();
        pk.slots = armor;

        for (Player player : players) {
            if (player.equals(this.getHolder())) {
                if (player.protocol >= ProtocolInfo.v1_2_0) {
                    InventoryContentPacket pk2 = new InventoryContentPacket();
                    pk2.inventoryId = InventoryContentPacket.SPECIAL_ARMOR;
                    pk2.slots = armor;
                    player.dataPacket(pk2);
                } else {
                    ContainerSetContentPacketV113 pk2 = new ContainerSetContentPacketV113();
                    pk2.windowid = ContainerSetContentPacketV113.SPECIAL_ARMOR;
                    pk2.eid = player.getId();
                    pk2.slots = armor;
                    player.dataPacket(pk2);
                }
            } else {
                player.dataPacket(pk);
            }
        }
    }

    public void sendArmorContentsIfNotAr(Player player) {
        Item[] armor = this.getArmorContents();
        if (armor[0].getId() != 0 || armor[1].getId() != 0 || armor[2].getId() != 0 || armor[3].getId() != 0) {
            MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
            pk.eid = this.getHolder().getId();
            pk.slots = armor;
            player.dataPacket(pk);
        }
    }

    public void setArmorContents(Item[] items) {
        if (items.length < 4) {
            Item[] newItems = new Item[4];
            System.arraycopy(items, 0, newItems, 0, items.length);
            items = newItems;
        }

        for (int i = 0; i < 4; ++i) {
            if (items[i] == null) {
                items[i] = new ItemBlock(Block.get(BlockID.AIR), null, 0);
            }

            if (items[i].getId() == Item.AIR) {
                this.clear(this.getSize() + i);
            } else {
                this.setItem(this.getSize() + i, items[i]);
            }
        }
    }

    public void sendArmorContents(Collection<Player> players) {
        this.sendArmorContents(players.toArray(Player.EMPTY_ARRAY));
    }

    public void sendArmorSlot(int index, Player player) {
        this.sendArmorSlot(index, new Player[]{player});
    }

    public void sendArmorSlot(int index, Player[] players) {
        Item[] armor = this.getArmorContents();

        MobArmorEquipmentPacket pk = new MobArmorEquipmentPacket();
        pk.eid = this.getHolder().getId();
        pk.slots = armor;

        for (Player player : players) {
            if (player.equals(this.getHolder())) {
                if (player.protocol >= ProtocolInfo.v1_2_0) {
                    InventorySlotPacket pk2 = new InventorySlotPacket();
                    pk2.inventoryId = InventoryContentPacket.SPECIAL_ARMOR;
                    pk2.slot = index - this.getSize();
                    pk2.item = this.getItem(index);
                    player.dataPacket(pk2);
                } else {
                    ContainerSetSlotPacketV113 pk3 = new ContainerSetSlotPacketV113();
                    pk3.windowid = ContainerSetContentPacketV113.SPECIAL_ARMOR;
                    pk3.slot = index - this.getSize();
                    pk3.item = this.getItem(index);
                    player.dataPacket(pk3);
                }
            } else {
                player.dataPacket(pk);
            }
        }
    }

    public void sendArmorSlot(int index, Collection<Player> players) {
        this.sendArmorSlot(index, players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public void sendContents(Player player) {
        this.sendContents(new Player[]{player});
    }

    @Override
    public void sendContents(Collection<Player> players) {
        this.sendContents(players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public void sendContents(Player[] players) {
        InventoryContentPacket pk = new InventoryContentPacket();
        pk.slots = new Item[this.getSize()];
        for (int i = 0; i < this.getSize(); ++i) {
            pk.slots[i] = this.getItem(i);
        }

        if (Server.getInstance().minimumProtocol <= ProtocolInfo.v1_1_0) {
            ContainerSetContentPacketV113 pk2 = new ContainerSetContentPacketV113();
            pk2.slots = Arrays.copyOf(pk.slots.clone(), pk.slots.length + 9);
            for(int i = this.getSize(); i < this.getSize() + 9; ++i){
                pk2.slots[i] = new ItemBlock(new BlockAir());
            }
            for (Player player : players) {
                if (player.protocol > ProtocolInfo.v1_1_0) {
                    continue;
                }
                if (player.equals(this.getHolder())) {
                    pk2.hotbar = new int[this.getHotbarSize()];
                    for (int i = 0; i < this.getHotbarSize(); ++i) {
                        int index = this.getHotbarSlotIndex(i);
                        pk2.hotbar[i] = index <= -1 ? -1 : index + 9;
                    }
                }
                int id = player.getWindowId(this);
                if (id == -1 || !player.spawned) {
                    this.close(player);
                    continue;
                }
                pk2.eid = player.getId();
                pk2.windowid = (byte) id;
                player.dataPacket(pk2.clone());
            }
        }

        for (Player player : players) {
            if (player.protocol < ProtocolInfo.v1_2_0) {
                continue;
            }
            int id = player.getWindowId(this);
            if (id == -1) {
                if (this.getHolder() != player) this.close(player);
                continue;
            }
            pk.inventoryId = id;
            player.dataPacket(pk.clone());
        }
    }

    @Override
    public void sendSlot(int index, Player player) {
        this.sendSlot(index, new Player[]{player});
    }

    @Override
    public void sendSlot(int index, Collection<Player> players) {
        this.sendSlot(index, players.toArray(Player.EMPTY_ARRAY));
    }

    @Override
    public void sendSlot(int index, Player... players) {
        if (players.length == 0 && this.getHolder() instanceof Player) {
            Player p = (Player) this.getHolder();
            if (p.protocol >= 407) {
                players = new Player[]{p};
            }
        }

        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = this.getItem(index).clone();

        ContainerSetSlotPacketV113 pk2 = new ContainerSetSlotPacketV113();
        pk2.slot = index;
        pk2.item = pk.item.clone();

        for (Player player : players) {
            if (player.equals(this.getHolder())) {
                pk.inventoryId = ContainerIds.INVENTORY;
                pk2.windowid = 0;
                if (player.protocol >= ProtocolInfo.v1_2_0) {
                    player.dataPacket(pk);
                } else {
                    player.dataPacket(pk2);
                }
            } else {
                int id = player.getWindowId(this);
                if (id == -1) {
                    this.close(player);
                    continue;
                }
                pk.inventoryId = id;
                pk2.windowid = id;
                if (player.protocol >= ProtocolInfo.v1_2_0) {
                    player.dataPacket(pk.clone());
                } else {
                    player.dataPacket(pk2.clone());
                }
            }
        }
    }

    public void sendCreativeContents() {
        if (!(this.getHolder() instanceof Player)) {
            return;
        }
        Player p = (Player) this.getHolder();

        if (p.protocol < 407) {
            if (p.protocol < ProtocolInfo.v1_2_0) {
                ContainerSetContentPacketV113 pk = new ContainerSetContentPacketV113();
                pk.windowid = ContainerSetContentPacketV113.SPECIAL_CREATIVE;
                pk.eid = p.getId();
                if (!p.isSpectator()) {
                    pk.slots = Item.getCreativeItems(p.getGameVersion()).toArray(Item.EMPTY_ARRAY);
                }
                p.dataPacket(pk);
            } else {
                InventoryContentPacket pk = new InventoryContentPacket();
                pk.inventoryId = ContainerIds.CREATIVE;
                if (!p.isSpectator()) { //fill it for all gamemodes except spectator
                    pk.slots = Item.getCreativeItems(p.getGameVersion()).toArray(Item.EMPTY_ARRAY);
                }
                p.dataPacket(pk);
            }
        } else {
            CreativeContentPacket pk = new CreativeContentPacket();
            if (!p.isSpectator()) {
                pk.creativeItems = Item.getCreativeItemsAndGroups(p.getGameVersion());
            }
            p.dataPacket(pk);
        }
    }

    @Override
    public EntityHuman getHolder() {
        return (EntityHuman) super.getHolder();
    }

    @Override
    public void onOpen(Player who) {
        super.onOpen(who);
        ContainerOpenPacket pk = new ContainerOpenPacket();
        pk.windowId = who.getWindowId(this);
        pk.type = this.getType().getNetworkType();
        pk.x = who.getFloorX();
        pk.y = who.getFloorY();
        pk.z = who.getFloorZ();
        pk.entityId = who.getId();
        who.dataPacket(pk);
    }

    @Override
    public void onClose(Player who) {
        if (who.getClosingWindowId() != Integer.MAX_VALUE) {
            ContainerClosePacket pk = new ContainerClosePacket();
            pk.windowId = who.getWindowId(this);
            pk.wasServerInitiated = who.getClosingWindowId() != pk.windowId;
            pk.type = ContainerType.from(this.type.getNetworkType());
            who.dataPacket(pk);
        }

        // Player can never stop viewing their own inventory
        if (who != holder) {
            super.onClose(who);
        }
    }
}
