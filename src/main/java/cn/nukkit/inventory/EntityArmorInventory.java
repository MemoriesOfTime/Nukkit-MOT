package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.InventoryContentPacket;
import cn.nukkit.network.protocol.InventorySlotPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;

import java.util.HashSet;
import java.util.Set;

public class EntityArmorInventory extends BaseInventory {

    private Entity entity;

    private final Set<Player> viewers = new HashSet<>();

    public static final int SLOT_HEAD = 0;
    public static final int SLOT_CHEST = 1;
    public static final int SLOT_LEGS = 2;
    public static final int SLOT_FEET = 3;
    // This is not part of the armor inventory, but it's used for happy ghasts
    public static final int SLOT_BODY = 4;

    public EntityArmorInventory(InventoryHolder holder) {
        super(holder, InventoryType.ENTITY_ARMOR);
        this.entity = (Entity) holder;
    }

    @Override
    public InventoryHolder getHolder() {
        return this.holder;
    }

    @Override
    public String getName() {
        return "Entity Armor";
    }

    @Override
    public int getSize() {
        return 5;
    }

    public Item getHelmet() {
        return this.getItem(SLOT_HEAD);
    }

    public Item getChestplate() {
        return this.getItem(SLOT_CHEST);
    }

    public Item getLeggings() {
        return this.getItem(SLOT_LEGS);
    }

    public Item getBoots() {
        return this.getItem(SLOT_FEET);
    }

    public Item getBody() {
        return this.getItem(SLOT_BODY);
    }

    public void setHelmet(Item item) {
        this.setItem(SLOT_HEAD, item);
    }

    public void setChestplate(Item item) {
        this.setItem(SLOT_CHEST, item);
    }

    public void setLeggings(Item item) {
        this.setItem(SLOT_LEGS, item);
    }

    public void setBoots(Item item) {
        this.setItem(SLOT_FEET, item);
    }

    public void setBody(Item item) {
        this.setItem(SLOT_BODY, item);
    }

    @Override
    public void sendSlot(int index, Player... players) {
        for (Player player : players) {
            this.sendSlot(index, player);
        }
    }

    @Override
    public void sendSlot(int index, Player player) {
        if (player == this.holder) {
            InventorySlotPacket inventorySlotPacket = new InventorySlotPacket();
            inventorySlotPacket.inventoryId = player.getWindowId(this);
            inventorySlotPacket.slot = index;
            inventorySlotPacket.item = this.getItem(index);
            player.dataPacket(inventorySlotPacket);
        } else {
            MobArmorEquipmentPacket mobArmorEquipmentPacket = new MobArmorEquipmentPacket();
            mobArmorEquipmentPacket.eid = this.entity.getId();
            mobArmorEquipmentPacket.slots = new Item[]{this.getHelmet(), this.getChestplate(), this.getLeggings(), this.getBoots()};
            mobArmorEquipmentPacket.body = this.getBody();
            player.dataPacket(mobArmorEquipmentPacket);
        }
    }

    @Override
    public void sendContents(Player... players) {
        for (Player player : players) {
            this.sendContents(player);
        }
    }

    @Override
    public void sendContents(Player player) {
        if (player == this.holder) {
            InventoryContentPacket inventoryContentPacket = new InventoryContentPacket();
            inventoryContentPacket.inventoryId = player.getWindowId(this);
            inventoryContentPacket.slots = new Item[]{this.getHelmet(), this.getChestplate(), this.getLeggings(), this.getBoots()};
            player.dataPacket(inventoryContentPacket);
        } else {
            MobArmorEquipmentPacket mobArmorEquipmentPacket = new MobArmorEquipmentPacket();
            mobArmorEquipmentPacket.eid = this.entity.getId();
            mobArmorEquipmentPacket.slots = new Item[]{this.getHelmet(), this.getChestplate(), this.getLeggings(), this.getBoots()};
            mobArmorEquipmentPacket.body = this.getBody();
            player.dataPacket(mobArmorEquipmentPacket);
        }
    }

    @Override
    public void onOpen(Player who) {
        this.viewers.add(who);
    }

    @Override
    public void onClose(Player who) {
        this.viewers.remove(who);
    }

    @Override
    public Set<Player> getViewers() {
        return this.viewers;
    }
}
