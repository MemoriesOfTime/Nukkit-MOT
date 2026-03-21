package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.EntityHumanType;
import cn.nukkit.event.entity.EntityInventoryChangeEvent;
import cn.nukkit.event.player.PlayerOffhandInventoryChangeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.network.protocol.types.ContainerIds;

public class PlayerOffhandInventory extends BaseInventory {

    /**
     * Items that can be put to offhand inventory on Bedrock Edition
     */
    //private static final IntSet OFFHAND_ITEMS = new IntOpenHashSet(Arrays.asList(ItemID.SHIELD, ItemID.ARROW, ItemID.TOTEM, ItemID.MAP, ItemID.FIREWORKS, ItemID.NAUTILUS_SHELL, ItemID.SPARKLER));

    public PlayerOffhandInventory(EntityHumanType holder) {
        super(holder, InventoryType.OFFHAND);
    }

    @Override
    public void setSize(int size) {
        throw new UnsupportedOperationException("Offhand can only carry one item at a time");
    }

    @Override
    public void onSlotChange(int index, Item before, boolean send) {
        EntityHuman holder = this.getHolder();
        if (holder instanceof Player && !((Player) holder).spawned) {
            return;
        }

        Item after = this.getItemFast(0);
        if (!after.isNull() && !after.equalsFast(before) && holder.level != null) {
            holder.level.addLevelSoundEvent(holder, LevelSoundEventPacket.SOUND_ARMOR_EQUIP_GENERIC);
        }

        this.sendContents(this.getViewers());
        this.sendContents(holder.getViewers().values());
    }

    @Override
    public void sendContents(Player... players) {
        Item item = this.getItemFast(0);
        MobEquipmentPacket pk = this.createMobEquipmentPacket(item);

        for (Player player : players) {
            if (player == this.getHolder()) {
                InventoryContentPacket pk2 = new InventoryContentPacket();
                pk2.inventoryId = ContainerIds.OFFHAND;
                pk2.slots = new Item[]{item};
                player.dataPacket(pk2);
            } else {
                player.dataPacket(pk);
            }
        }
    }

    @Override
    public void sendSlot(int index, Player... players) {
        Item item = this.getItemFast(0);
        MobEquipmentPacket pk = this.createMobEquipmentPacket(item);

        for (Player player : players) {
            if (player == this.getHolder()) {
                InventorySlotPacket pk2 = new InventorySlotPacket();
                pk2.inventoryId = ContainerIds.OFFHAND;
                pk2.item = item;
                player.dataPacket(pk2);
            } else {
                player.dataPacket(pk);
            }
        }
    }

    private MobEquipmentPacket createMobEquipmentPacket(Item item) {
        MobEquipmentPacket pk = new MobEquipmentPacket();
        pk.eid = this.getHolder().getId();
        pk.item = item;
        pk.inventorySlot = 1;
        pk.windowId = ContainerIds.OFFHAND;
        return pk;
    }

    @Override
    public EntityHuman getHolder() {
        return (EntityHuman) super.getHolder();
    }

    @Override
    public boolean allowedToAdd(Item item) {
        //return OFFHAND_ITEMS.contains(item.getId());
        return true;
    }

    @Override
    public boolean setItem(int index, Item item, boolean send) {
        if (index < 0 || index >= this.size || !this.allowedToAdd(item)) {
            return false;
        } else if (item.getId() == 0 || item.getCount() <= 0) {
            return this.clear(index, send);
        }

        EntityHuman holder = this.getHolder();
        Item oldItem = this.getItem(index);
        EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent(holder, oldItem, item, index);
        Server.getInstance().getPluginManager().callEvent(ev);
        if (ev.isCancelled()) {
            this.sendSlot(index, this.getViewers());
            if (holder instanceof Player) {
                this.sendSlot(index, (Player) holder);
            }
            return false;
        }
        item = ev.getNewItem();

        if (holder instanceof Player) {
            PlayerOffhandInventoryChangeEvent ev2 = new PlayerOffhandInventoryChangeEvent((Player) holder, oldItem, item);
            Server.getInstance().getPluginManager().callEvent(ev2);
            if (ev2.isCancelled()) {
                this.sendSlot(index, this.getViewers());
                this.sendSlot(index, (Player) holder);
                return false;
            }
            item = ev2.getNewItem();
        }

        this.slots.put(index, item.clone());
        this.onSlotChange(index, oldItem, send);
        return true;
    }

    @Override
    public boolean clear(int index, boolean send) {
        Item old = this.slots.get(index);
        if (old != null && old.getId() != Item.AIR) {
            Item item = new ItemBlock(cn.nukkit.block.Block.get(cn.nukkit.block.BlockID.AIR), null, 0);

            EntityHuman holder = this.getHolder();
            EntityInventoryChangeEvent ev = new EntityInventoryChangeEvent(holder, old, item, index);
            Server.getInstance().getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                this.sendSlot(index, this.getViewers());
                if (holder instanceof Player) {
                    this.sendSlot(index, (Player) holder);
                }
                return false;
            }
            item = ev.getNewItem();

            if (holder instanceof Player) {
                PlayerOffhandInventoryChangeEvent ev2 = new PlayerOffhandInventoryChangeEvent((Player) holder, old, item);
                Server.getInstance().getPluginManager().callEvent(ev2);
                if (ev2.isCancelled()) {
                    this.sendSlot(index, this.getViewers());
                    this.sendSlot(index, (Player) holder);
                    return false;
                }
                item = ev2.getNewItem();
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
}
