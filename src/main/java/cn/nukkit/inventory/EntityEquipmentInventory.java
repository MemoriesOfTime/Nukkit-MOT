package cn.nukkit.inventory;

import cn.nukkit.Player;
import cn.nukkit.entity.item.EntityArmorStand;
import cn.nukkit.item.Item;
import cn.nukkit.network.protocol.MobEquipmentPacket;

import java.util.HashSet;
import java.util.Set;

public class EntityEquipmentInventory extends BaseInventory {

    private static final int MAINHAND = 0;
    private static final int OFFHAND = 1;
    private final Set<Player> viewers = new HashSet<>();
    private final EntityArmorStand entityLiving;

    public EntityEquipmentInventory(EntityArmorStand entity) {
        super(entity, InventoryType.ENTITY_EQUIPMENT);
        this.entityLiving = entity;
    }


    @Override
    public String getName() {
        return "Entity Equipment";
    }

    @Override
    public int getSize() {
        return 2;
    }

    @Override
    public InventoryHolder getHolder() {
        return this.holder;
    }

    @Override
    public void sendSlot(int index, Player... players) {
        for (Player player : players) {
            this.sendSlot(index, player);
        }
    }

    @Override
    public void sendSlot(int index, Player player) {
        MobEquipmentPacket mobEquipmentPacket = new MobEquipmentPacket();
        mobEquipmentPacket.eid = this.entityLiving.getId();
        mobEquipmentPacket.inventorySlot = mobEquipmentPacket.hotbarSlot = index;
        mobEquipmentPacket.item = this.getItem(index);
        player.dataPacket(mobEquipmentPacket);
    }

    @Override
    public Set<Player> getViewers() {
        return this.viewers;
    }

    @Override
    public boolean open(Player who) {
        return this.viewers.add(who);
    }

    @Override
    public void onClose(Player who) {
        this.viewers.remove(who);
    }

    public Item getItemInHand() {
        return this.getItem(MAINHAND);
    }

    public Item getOffHandItem() {
        return this.getItem(OFFHAND);
    }

    public boolean setItemInHand(Item item, boolean send) {
        return this.setItem(MAINHAND, item, send);
    }

    public boolean setOffhandItem(Item item, boolean send) {
        return this.setItem(OFFHAND, item, send);
    }

    @Override
    public void sendContents(Player target) {
        this.sendSlot(MAINHAND, target);
        this.sendSlot(OFFHAND, target);
    }

    @Override
    public void sendContents(Player... target) {
        for (Player player : target) {
            this.sendContents(player);
        }
    }
}
