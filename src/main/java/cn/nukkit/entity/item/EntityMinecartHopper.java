package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntityChest;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.MinecartType;

public class EntityMinecartHopper extends EntityMinecartAbstract implements InventoryHolder {

    public static final int NETWORK_ID = 96;

    protected MinecartHopperInventory inventory;

    public int transferCooldown = 8;

    public EntityMinecartHopper(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        setDisplayBlock(Block.get(Block.HOPPER_BLOCK), false);
        setName("Minecart with Hopper");
    }

    @Override
    public MinecartType getType() {
        return MinecartType.valueOf(5);
    }

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public void dropItem() {
        for (Item item : this.inventory.getContents().values()) {
            this.level.dropItem(this, item);
        }

        if (this.lastDamageCause instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) this.lastDamageCause).getDamager();
            if (damager instanceof Player && ((Player) damager).isCreative()) {
                return;
            }
        }
        this.level.dropItem(this, Item.get(Item.HOPPER_MINECART));
    }

    @Override
    public void kill() {
        super.kill();
        this.inventory.clearAll();
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        return false;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        player.addWindow(this.inventory);
        return false; // If true, the count of items player has in hand decreases
    }

    @Override
    public MinecartHopperInventory getInventory() {
        return inventory;
    }

    @Override
    public void initEntity() {
        super.initEntity();

        this.inventory = new MinecartHopperInventory(this);
        if (this.namedTag.contains("Items") && this.namedTag.get("Items") instanceof ListTag) {
            ListTag<CompoundTag> inventoryList = this.namedTag.getList("Items", CompoundTag.class);
            for (CompoundTag item : inventoryList.getAll()) {
                this.inventory.setItem(item.getByte("Slot"), NBTIO.getItemHelper(item));
            }
        }

        this.dataProperties
                .putByte(DATA_CONTAINER_TYPE, 11)
                .putInt(DATA_CONTAINER_BASE_SIZE, this.inventory.getSize())
                .putInt(DATA_CONTAINER_EXTRA_SLOTS_PER_STRENGTH, 0);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putList(new ListTag<CompoundTag>("Items"));
        if (this.inventory != null) {
            for (int slot = 0; slot < 5; ++slot) {
                Item item = this.inventory.getItem(slot);
                if (item != null && item.getId() != Item.AIR) {
                    this.namedTag.getList("Items", CompoundTag.class)
                            .add(NBTIO.putItemHelper(item, slot));
                }
            }
        }
    }

    @Override
    public String getInteractButtonText() {
        return "action.interact.opencontainer";
    }

    @Override
    public boolean onUpdate(int currentTick) {
        MinecartHopperInventory minecartHopperInventory = this.getInventory();
        for (Entity nearbyEntity : this.getLevel().getNearbyEntities(new SimpleAxisAlignedBB(this.getPosition().add(1, 0.5, 1), this.getPosition().subtract(1, -0.5, 1)))) {
            if(nearbyEntity instanceof EntityItem) {
                if(minecartHopperInventory.canAddItem(((EntityItem) nearbyEntity).getItem())) {
                    minecartHopperInventory.addItem(((EntityItem) nearbyEntity).item);
                    nearbyEntity.kill();
                    nearbyEntity.close();
                }
            }
        }
        if(!isOnTransferCooldown()) {
            Block upperBlock = this.getPosition().floor().add(0, 1, 0).getLevelBlock();
            if (upperBlock != null) {
                switch (upperBlock.getId()) {
                    case Block.CHEST:
                        BlockEntityChest entityChest = (BlockEntityChest) upperBlock.getLevel().getBlockEntity(upperBlock);
                        ChestInventory chestInventory = (ChestInventory) entityChest.getInventory();
                        for (Item pullItem : chestInventory.getContents().values()) {
                            pullItem = pullItem.clone();
                            pullItem.setCount(1);
                            if (!pullItem.isNull() && !minecartHopperInventory.isFull() && minecartHopperInventory.canAddItem(pullItem)) {
                                chestInventory.removeItem(pullItem);
                                minecartHopperInventory.addItem(pullItem);
                                break;
                            }
                        }
                        break;
                    case Block.HOPPER_BLOCK:
                        BlockEntityHopper entityHopper = (BlockEntityHopper) upperBlock.getLevel().getBlockEntity(upperBlock);
                        HopperInventory hopperInventory = entityHopper.getInventory();
                        for (Item pullItem : hopperInventory.getContents().values()) {
                            pullItem = pullItem.clone();
                            pullItem.setCount(1);
                            if (!pullItem.isNull() && !minecartHopperInventory.isFull() && minecartHopperInventory.canAddItem(pullItem)) {
                                hopperInventory.removeItem(pullItem);
                                minecartHopperInventory.addItem(pullItem);
                                break;
                            }
                        }
                        break;
                    case Block.FURNACE:
                        BlockEntityFurnace entityFurnace = (BlockEntityFurnace) upperBlock.getLevel().getBlockEntity(upperBlock);
                        FurnaceInventory furnaceInventory = entityFurnace.getInventory();
                        Item resultItem = furnaceInventory.getResult().clone();
                        resultItem.setCount(1);
                        if (!resultItem.isNull() && !minecartHopperInventory.isFull() && minecartHopperInventory.canAddItem(resultItem)) {
                            furnaceInventory.removeItem(resultItem);
                            minecartHopperInventory.addItem(resultItem);
                        }
                        break;
                }
            }

            Item pullItemToBottom = minecartHopperInventory.getItem(0).clone();
            if(pullItemToBottom.getId() != Item.AIR) {
                for (Item value : minecartHopperInventory.getContents().values()) {
                    if(value.getId() != Item.AIR){
                        pullItemToBottom = value.clone();
                        break;
                    }
                }
                pullItemToBottom.setCount(1);
                Block bottomBlock = this.getPosition().floor().subtract(0, 1, 0).getLevelBlock();
                if (bottomBlock != null) {
                    switch (bottomBlock.getId()) {
                        case Block.HOPPER_BLOCK:
                            BlockEntityHopper entityHopper = (BlockEntityHopper) bottomBlock.getLevel().getBlockEntity(bottomBlock);
                            HopperInventory hopperInventory = entityHopper.getInventory();
                            if (!hopperInventory.isFull() && hopperInventory.canAddItem(pullItemToBottom)) {
                                hopperInventory.addItem(pullItemToBottom);
                                minecartHopperInventory.removeItem(pullItemToBottom);
                            }
                            break;
                        case Block.FURNACE:
                            BlockEntityFurnace entityFurnace = (BlockEntityFurnace) bottomBlock.getLevel().getBlockEntity(bottomBlock);
                            FurnaceInventory furnaceInventory = entityFurnace.getInventory();
                            Item fuel = furnaceInventory.getFuel();
                            if (fuel.isNull() || fuel.equals(pullItemToBottom, false, false)) {
                                if(fuel.isNull()){
                                    furnaceInventory.setFuel(pullItemToBottom);
                                }else{
                                    pullItemToBottom.increment(fuel.getCount());
                                    furnaceInventory.setItem(0, pullItemToBottom);
                                }
                                minecartHopperInventory.removeItem(pullItemToBottom);
                            }
                            break;
                    }
                }
            }
            transferCooldown = 8;
        } else {
            transferCooldown--;
        }
        return super.onUpdate(currentTick);
    }

    public boolean isOnTransferCooldown() {
        return this.transferCooldown > 0;
    }

    public void setTransferCooldown(int transferCooldown) {
        this.transferCooldown = transferCooldown;
    }
}
