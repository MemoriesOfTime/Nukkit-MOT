package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.*;
import cn.nukkit.item.Item;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import lombok.Getter;
import lombok.Setter;

import java.util.HashSet;

/**
 * Created by CreeperFace on 8.5.2017.
 */
public class BlockEntityHopper extends BlockEntitySpawnableContainer implements BlockEntityNameable, BlockHopper.IHopper {

    public int transferCooldown;

    private AxisAlignedBB pickupArea;

    //由容器矿车检测漏斗并通知更新，这样子能大幅优化性能
    @Getter
    @Setter
    private InventoryHolder minecartInvPickupFrom = null;
    @Getter
    @Setter
    private InventoryHolder minecartInvPushTo = null;

    public BlockEntityHopper(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    protected void initBlockEntity() {
        if (this.namedTag.contains("TransferCooldown")) {
            this.transferCooldown = this.namedTag.getInt("TransferCooldown");
        } else {
            this.transferCooldown = 8;
        }

        this.inventory = new HopperInventory(this);

        if (!this.namedTag.contains("Items") || !(this.namedTag.get("Items") instanceof ListTag)) {
            this.namedTag.putList(new ListTag<CompoundTag>("Items"));
        }

        for (int i = 0; i < this.getSize(); i++) {
            this.inventory.setItem(i, this.getItem(i));
        }

        this.pickupArea = new SimpleAxisAlignedBB(this.x, this.y, this.z, this.x + 1, this.y + 2, this.z + 1);

        this.scheduleUpdate();

        super.initBlockEntity();
    }

    @Override
    public boolean isBlockEntityValid() {
        return this.level.getBlockIdAt(chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ()) == Block.HOPPER_BLOCK;
    }

    @Override
    public String getName() {
        return this.hasName() ? this.namedTag.getString("CustomName") : "Hopper";
    }

    @Override
    public void setName(String name) {
        if (name == null || name.isEmpty()) {
            this.namedTag.remove("CustomName");
            return;
        }

        this.namedTag.putString("CustomName", name);
    }

    @Override
    public boolean hasName() {
        return this.namedTag.contains("CustomName");
    }

    @Override
    public boolean isOnTransferCooldown() {
        return this.transferCooldown > 0;
    }

    @Override
    public void setTransferCooldown(int transferCooldown) {
        this.transferCooldown = transferCooldown;
    }

    @Override
    public int getSize() {
        return 5;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putInt("TransferCooldown", this.transferCooldown);
    }

    @Override
    public HopperInventory getInventory() {
        return (HopperInventory) inventory;
    }

    @Override
    public boolean onUpdate() {
        if (this.closed) {
            return false;
        }

        this.transferCooldown--;

        if (!this.isOnTransferCooldown()) {
            if (this.level.isBlockPowered(getBlock())) {
                return true;
            }

            boolean changed = pushItems() || pushItemsIntoMinecart();

            if (!changed) {
                BlockEntity blockEntity = this.level.getBlockEntity(this.up());
                if (!(blockEntity instanceof BlockEntityContainer)) {
                    changed = pickupItems() || pullItemsFromMinecart();
                } else {
                    changed = pullItems();
                }
            }

            if (changed) {
                this.setTransferCooldown(8);
                setDirty();
            }
        }


        return true;
    }

    public boolean pullItemsFromMinecart() {
        if (this.inventory.isFull()) {
            return false;
        }

        if (getMinecartInvPickupFrom() != null) {
            var inv = getMinecartInvPickupFrom().getInventory();

            for (int i = 0; i < inv.getSize(); i++) {
                Item item = inv.getItem(i);

                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.count = 1;
                    if (!this.inventory.canAddItem(itemToAdd))
                        continue;

                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(inv, this.inventory, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                    this.server.getPluginManager().callEvent(ev);
                    if (ev.isCancelled())
                        continue;

                    Item[] items = this.inventory.addItem(itemToAdd);
                    if (items.length >= 1)
                        continue;

                    item.count--;
                    inv.setItem(i, item);

                    //归位为null
                    setMinecartInvPickupFrom(null);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean pickupItems() {
        return pickupItems(this.pickupArea);
    }

    public boolean pushItemsIntoMinecart() {
        if (getMinecartInvPushTo() != null) {
            Inventory holderInventory = getMinecartInvPushTo().getInventory();

            if (holderInventory.isFull()) {
                return false;
            }

            for (int i = 0; i < this.inventory.getSize(); i++) {
                Item item = this.inventory.getItem(i);

                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    if (!holderInventory.canAddItem(itemToAdd)) {
                        continue;
                    }

                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, holderInventory, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                    this.server.getPluginManager().callEvent(ev);

                    if (ev.isCancelled()) {
                        continue;
                    }

                    Item[] items = holderInventory.addItem(itemToAdd);

                    if (items.length > 0) {
                        continue;
                    }

                    item.count--;
                    this.inventory.setItem(i, item);

                    //归位为null
                    setMinecartInvPushTo(null);
                    return true;
                }
            }

        }

        return false;
    }

    @Override
    public boolean pushItems() {
        if (this.inventory.isEmpty()) {
            return false;
        }

        BlockEntity be = this.level.getBlockEntity(this.getSide(BlockFace.fromIndex(this.level.getBlockDataAt(this.getFloorX(), this.getFloorY(), this.getFloorZ()))));

        if (be instanceof BlockEntityHopper && this.getBlock().getDamage() == 0 || !(be instanceof InventoryHolder)) {
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

                    if (this.getBlock().getDamage() == 0) {
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
                    } else if (Fuel.duration.containsKey(itemToAdd.getId())) {
                        Item fuel = targetInv.getFuel();
                        if (fuel.isNull()) {
                            event = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                            this.server.getPluginManager().callEvent(event);

                            if (!event.isCancelled()) {
                                targetInv.setFuel(itemToAdd);
                                item.count--;
                                pushedItem = true;
                            }
                        } else if (fuel.getId() == itemToAdd.getId() && fuel.getDamage() == itemToAdd.getDamage() && fuel.count < fuel.getMaxStackSize()) {
                            event = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                            this.server.getPluginManager().callEvent(event);

                            if (!event.isCancelled()) {
                                fuel.count++;
                                targetInv.setFuel(fuel);
                                item.count--;
                                pushedItem = true;
                            }
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
    public void close() {
        if (!closed) {
            for (Player player : new HashSet<>(this.inventory.getViewers())) {
                player.removeWindow(this.inventory);
            }
            super.close();
        }
    }

    @Override
    public CompoundTag getSpawnCompound() {
        CompoundTag c = new CompoundTag()
                .putString("id", BlockEntity.HOPPER)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);

        if (this.hasName()) {
            c.put("CustomName", this.namedTag.get("CustomName"));
        }

        return c;
    }

    @Override
    public Position getPosition() {
        return this;
    }
}