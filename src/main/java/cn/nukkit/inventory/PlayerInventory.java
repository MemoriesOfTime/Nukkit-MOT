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
import cn.nukkit.event.player.PlayerOffhandInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemCrossbow;
import cn.nukkit.item.ItemMap;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.ContainerIds;
import cn.nukkit.network.protocol.types.inventory.ContainerSlotType;
import cn.nukkit.network.protocol.types.inventory.ContainerType;
import cn.nukkit.network.protocol.types.inventory.FullContainerName;
import cn.nukkit.network.protocol.v113.ContainerSetContentPacket_v113;
import cn.nukkit.network.protocol.v113.ContainerSetSlotPacket_v113;
import org.jetbrains.annotations.ApiStatus;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class PlayerInventory extends BaseInventory {

    protected int itemInHandIndex = 0;
    private long heldItemRevision;
    private boolean crossbowChangeInProgress;

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
            this.updateHeldItemIndex(index);

            if (this.getHolder() instanceof Player && send) {
                this.sendHeldItem((Player) this.getHolder());
            }

            this.sendHeldItem(this.getHolder().getViewers().values());
        }
    }

    private void updateHeldItemIndex(int index) {
        if (this.itemInHandIndex != index) {
            this.itemInHandIndex = index;
            this.heldItemRevision++;
            if (this.getHolder() instanceof Player player) {
                player.resetCrossbowInputState();
            }
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

    @ApiStatus.Internal
    public long getHeldItemRevision() {
        return this.heldItemRevision;
    }

    /**
     * 事件通过后同时提交弩与弹药，将弩元数据回写到 after。
     * <p>
     * Commits both slots after event approval and copies crossbow metadata back to after.
     */
    @ApiStatus.Internal
    public boolean commitCrossbowChange(long revision, ItemCrossbow before, ItemCrossbow after,
                                        BaseInventory ammunitionInventory, int ammunitionSlot) {
        if (this.crossbowChangeInProgress || !(this.getHolder() instanceof Player player)
                || !player.isConnected() || !player.spawned || !player.isAlive() || player.isSpectator()
                || revision != this.heldItemRevision || !before.equalsExact(this.getItemInHand())) {
            return false;
        }
        int heldSlot = this.itemInHandIndex;
        var level = player.getLevel();
        int gamemode = player.getGamemode();
        long inputGeneration = player.getCrossbowInputGeneration();
        Item replacement = after.isNull() ? Item.AIR_ITEM.clone() : after.clone();
        Item ammunition = null;
        Item remaining = null;
        Item ammunitionIdentity = null;
        if (ammunitionInventory != null) {
            if ((ammunitionInventory != this && ammunitionInventory != player.getOffhandInventory())
                    || ammunitionSlot < 0 || ammunitionSlot >= ammunitionInventory.getSize()
                    || (ammunitionInventory == this && ammunitionSlot == heldSlot)) {
                return false;
            }
            ammunition = ammunitionInventory.getItem(ammunitionSlot);
            if (ammunition.isNull() || !ammunition.equals(after.getChargedItem())) {
                return false;
            }
            ammunitionIdentity = ammunitionInventory.getItemFast(ammunitionSlot);
            remaining = ammunition.clone();
            remaining.setCount(remaining.getCount() - 1);
            if (remaining.isNull()) remaining = Item.AIR_ITEM.clone();
        }

        this.crossbowChangeInProgress = true;
        try {
            EntityInventoryChangeEvent heldEvent = new EntityInventoryChangeEvent(
                    player, before.clone(), replacement.clone(), heldSlot);
            player.getServer().getPluginManager().callEvent(heldEvent);
            if (heldEvent.isCancelled() || !preservesCrossbowState(replacement, heldEvent.getNewItem())) {
                return false;
            }
            // 保留服务端弩实例上的装填冷却，仅采纳事件修改的元数据。
            // Keep the server crossbow's charge cooldown while accepting event metadata.
            replacement.setCompoundTag(heldEvent.getNewItem().getCompoundTag().clone());
            if (ammunitionInventory != null) {
                EntityInventoryChangeEvent ammoEvent = new EntityInventoryChangeEvent(
                        player, ammunition.clone(), remaining.clone(), ammunitionSlot);
                player.getServer().getPluginManager().callEvent(ammoEvent);
                if (ammoEvent.isCancelled() || !preservesStack(remaining, ammoEvent.getNewItem())) {
                    return false;
                }
                remaining.setCompoundTag(ammoEvent.getNewItem().getCompoundTag().clone());
                if (ammunitionInventory == player.getOffhandInventory()) {
                    PlayerOffhandInventoryChangeEvent offhandEvent = new PlayerOffhandInventoryChangeEvent(
                            player, ammunition.clone(), remaining.clone());
                    player.getServer().getPluginManager().callEvent(offhandEvent);
                    if (offhandEvent.isCancelled() || !preservesStack(remaining, offhandEvent.getNewItem())) {
                        return false;
                    }
                    remaining.setCompoundTag(offhandEvent.getNewItem().getCompoundTag().clone());
                }
            }
            if (!player.isConnected() || !player.spawned || !player.isAlive() || player.getGamemode() != gamemode
                    || player.getCrossbowInputGeneration() != inputGeneration
                    || player.getLevel() != level || heldSlot != this.itemInHandIndex
                    || revision != this.heldItemRevision || !before.equalsExact(this.getItemInHand())
                    || (ammunitionInventory != null
                    && (ammunitionIdentity != ammunitionInventory.getItemFast(ammunitionSlot)
                    || !ammunition.equalsExact(ammunitionInventory.getItem(ammunitionSlot))))) {
                return false;
            }

            // 两处写入必须先于通知，通知可能同步调用插件。
            // Both writes precede notifications, which can synchronously invoke plugins.
            if (!replacement.isNull()) after.setCompoundTag(replacement.getCompoundTag().clone());
            if (!replacement.isNull() && replacement.getStackNetId() == 0) replacement.autoAssignStackNetworkId();
            if (remaining != null && !remaining.isNull() && remaining.getStackNetId() == 0) remaining.autoAssignStackNetworkId();
            if (replacement.isNull()) this.slots.remove(heldSlot);
            else this.slots.put(heldSlot, replacement);
            if (ammunitionInventory != null) {
                if (remaining.isNull()) ammunitionInventory.slots.remove(ammunitionSlot);
                else ammunitionInventory.slots.put(ammunitionSlot, remaining);
            }
            this.onSlotChange(heldSlot, before, true);
            if (ammunitionInventory != null) {
                ammunitionInventory.onSlotChange(ammunitionSlot, ammunition, true);
            }
            return true;
        } finally {
            this.crossbowChangeInProgress = false;
        }
    }

    private static boolean preservesCrossbowState(Item expected, Item proposed) {
        return preservesStack(expected, proposed) && (expected.isNull()
                || (proposed instanceof ItemCrossbow
                && Objects.equals(expected.getNamedTagEntry("chargedItem"), proposed.getNamedTagEntry("chargedItem"))
                && Objects.equals(expected.getNamedTagEntry("Charged"), proposed.getNamedTagEntry("Charged"))
                && Objects.equals(expected.getNamedTagEntry("launchCount"), proposed.getNamedTagEntry("launchCount"))));
    }

    private static boolean preservesStack(Item expected, Item proposed) {
        return proposed != null && expected.getCount() == proposed.getCount()
                && expected.equals(proposed, true, false);
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

        this.updateHeldItemIndex(slot);

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
        if (index == this.itemInHandIndex) {
            this.heldItemRevision++;
            if (holder instanceof Player player) {
                player.resetCrossbowInputState();
            }
        }
        if (holder instanceof Player && !((Player) holder).spawned) {
            return;
        }

        if (index >= this.getSize()) {
            this.sendArmorSlot(index, this.getViewers());
            this.sendArmorSlot(index, this.getHolder().getViewers().values());
            if (holder instanceof Player player) {
                player.sendKnockBackResistanceAttribute();
            }
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

        if (!InventoryMutationGuard.acceptsWrite(this, index, item)) return false;

        if (item instanceof cn.nukkit.item.ItemBundle bundle) {
            ensureUniqueBundleId(index, bundle);
        }

        Item[] parts = splitOverstack(item);
        item = parts[0];

        Item old = this.getItem(index);
        this.slots.put(index, item.clone());
        this.onSlotChange(index, old, send);
        this.routeOverflow(parts[1]);
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

            if (!InventoryMutationGuard.acceptsWrite(this, index, item)) return false;

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
                    int id = InventoryContentPacket.SPECIAL_ARMOR;
                    pk2.inventoryId = id;
                    pk2.slots = armor;
                    pk2.containerNameData = new FullContainerName(ContainerSlotType.ARMOR, id);
                    player.dataPacket(pk2);
                } else {
                    ContainerSetContentPacket_v113 pk2 = new ContainerSetContentPacket_v113();
                    pk2.windowid = ContainerSetContentPacket_v113.SPECIAL_ARMOR;
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
                    int id = InventoryContentPacket.SPECIAL_ARMOR;
                    pk2.inventoryId = id;
                    pk2.slot = index - this.getSize();
                    pk2.item = this.getItem(index);
                    pk2.containerNameData = new FullContainerName(ContainerSlotType.ARMOR, id);
                    player.dataPacket(pk2);
                } else {
                    ContainerSetSlotPacket_v113 pk3 = new ContainerSetSlotPacket_v113();
                    pk3.windowid = ContainerSetContentPacket_v113.SPECIAL_ARMOR;
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
            ContainerSetContentPacket_v113 pk2 = new ContainerSetContentPacket_v113();
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
            pk.containerNameData = new FullContainerName(ContainerSlotType.HOTBAR_AND_INVENTORY, id);
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

        Item item = this.getItem(index);

        InventorySlotPacket pk = new InventorySlotPacket();
        pk.slot = index;
        pk.item = item;

        ContainerSetSlotPacket_v113 pk2 = null;

        for (Player player : players) {
            int id;
            if (player.equals(this.getHolder())) {
                id = ContainerIds.INVENTORY;
            } else {
                id = player.getWindowId(this);
                if (id == -1) {
                    this.close(player);
                    continue;
                }
            }

            if (player.protocol >= ProtocolInfo.v1_2_0) {
                pk.inventoryId = id;
                pk.containerNameData = resolvePlayerSlotContainerName(index, id);
                player.dataPacket(pk);
            } else {
                if (pk2 == null) {
                    pk2 = new ContainerSetSlotPacket_v113();
                    pk2.slot = index;
                    pk2.item = item.clone();
                }
                pk2.windowid = id;
                player.dataPacket(pk2);
            }
        }
    }

    private FullContainerName resolvePlayerSlotContainerName(int index, int dynamicId) {
        if (index < 9) {
            return new FullContainerName(ContainerSlotType.HOTBAR, dynamicId);
        }
        if (index < 36) {
            return new FullContainerName(ContainerSlotType.INVENTORY, dynamicId);
        }
        return new FullContainerName(ContainerSlotType.ARMOR, dynamicId);
    }

    public void sendCreativeContents() {
        if (!(this.getHolder() instanceof Player)) {
            return;
        }
        Player p = (Player) this.getHolder();

        if (p.protocol < 407) {
            if (p.protocol < ProtocolInfo.v1_2_0) {
                ContainerSetContentPacket_v113 pk = new ContainerSetContentPacket_v113();
                pk.windowid = ContainerSetContentPacket_v113.SPECIAL_CREATIVE;
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
                pk.creativeItems = Item.getCreativeItemsAndGroups();
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
