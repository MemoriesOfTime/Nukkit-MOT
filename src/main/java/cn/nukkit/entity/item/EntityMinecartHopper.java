package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityContainer;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.FurnaceInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.inventory.MinecartHopperInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.MinecartType;

public class EntityMinecartHopper extends EntityMinecartAbstract implements InventoryHolder, BlockHopper.IHopper {

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
        if (!super.onUpdate(currentTick)) {
            return false;
        }

        this.transferCooldown--;

        if (!this.isOnTransferCooldown()) {
            boolean changed = pushItems();

            if (!changed) {
                BlockEntity blockEntity = this.level.getBlockEntity(this.up());
                if (!(blockEntity instanceof BlockEntityContainer)) {
                    changed = pickupItems(new SimpleAxisAlignedBB(this.getPosition().add(0.8, 0.5, 0.8), this.getPosition().subtract(0.8, 0.5, 0.8)));
                } else {
                    changed = pullItems();
                }
            }

            if (changed) {
                this.setTransferCooldown(8);
            }
        }

        return true;
    }

    @Override
    public boolean pushItems() {
        if (this.inventory.isEmpty()) {
            return false;
        }

        Block bottomBlock = this.getPosition().floor().subtract(0, 1, 0).getLevelBlock();
        BlockEntity be = this.level.getBlockEntity(bottomBlock);

        if (be instanceof BlockEntityHopper // 漏斗会主动从漏斗矿车中拉取
                || !(be instanceof InventoryHolder)) {
            return false;
        }

        InventoryMoveItemEvent event;

        if (be instanceof BlockEntityFurnace furnace) {
            FurnaceInventory targetInv = furnace.getInventory();
            if (targetInv.isFull()) {
                return false;
            }

            boolean pushedItem = false;

            for (int i = 0; i < this.inventory.getSize(); i++) {
                Item item = this.inventory.getItem(i);
                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    Item smelting = targetInv.getSmelting();
                    if (smelting.isNull()) {
                        event = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(event);

                        if (!event.isCancelled()) {
                            targetInv.setSmelting(itemToAdd);
                            item.count--;
                            pushedItem = true;
                        }
                    } else if (targetInv.getSmelting().getId() == itemToAdd.getId() && targetInv.getSmelting().getDamage() == itemToAdd.getDamage() && smelting.count < smelting.getMaxStackSize()) {
                        event = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(event);

                        if (!event.isCancelled()) {
                            smelting.count++;
                            targetInv.setSmelting(smelting);
                            item.count--;
                            pushedItem = true;
                        }
                    }

                    if (pushedItem) {
                        this.inventory.setItem(i, item);
                    }
                }
            }

            return pushedItem;
        } else {
            Inventory target = ((InventoryHolder) be).getInventory();

            if (target.isFull()) {
                return false;
            }

            for (int i = 0; i < this.inventory.getSize(); i++) {
                Item item = this.inventory.getItem(i);

                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    if (!target.canAddItem(itemToAdd)) {
                        continue;
                    }

                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, target, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                    this.server.getPluginManager().callEvent(ev);

                    if (ev.isCancelled()) {
                        continue;
                    }

                    Item[] items = target.addItem(itemToAdd);

                    if (items.length > 0) {
                        continue;
                    }

                    item.count--;
                    this.inventory.setItem(i, item);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean isOnTransferCooldown() {
        return this.transferCooldown > 0;
    }

    @Override
    public void setTransferCooldown(int transferCooldown) {
        this.transferCooldown = transferCooldown;
    }

}
