package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
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
        return this.level.getBlockIdAt(this.chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ()) == Block.HOPPER_BLOCK;
    }

    @Override
    public String getName() {
        return this.hasName() ? this.namedTag.getString("CustomName") : "Hopper";
    }

    @Override
    public boolean hasName() {
        return this.namedTag.contains("CustomName");
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
        return (HopperInventory) this.inventory;
    }

    @Override
    public boolean onUpdate() {
        if (this.closed) {
            return false;
        }

        if (!this.level.isChunkInUse(this.getChunkX(), this.getChunkZ())) {
            return false;
        }

        this.transferCooldown--;

        if (!this.isOnTransferCooldown()) {
            if (this.level.isBlockPowered(this.getBlock())) {
                return true;
            }

            boolean changed = false;

            if (!this.inventory.isEmpty()) {
                changed = this.pushItemsIntoMinecart() || this.pushItems();
            }

            if (!changed && !this.inventory.isFull()) {
                BlockEntity blockEntity = this.level.getBlockEntity(this.up());
                Block block = null;
                if (blockEntity instanceof BlockEntityContainer ||
                        (block = this.level.getBlock(this.chunk, this.getFloorX(), this.getFloorY() + 1, this.getFloorZ(), false)) instanceof BlockComposter) {
                    changed = this.pullItems(blockEntity, block);
                } else {
                    changed = this.pullItemsFromMinecart() || this.pickupItems();
                }
            }

            if (changed) {
                this.setTransferCooldown(8);
                this.setDirty();
            }
        }

        return true;
    }

    public boolean pullItemsFromMinecart() {
        if (this.inventory.isFull() || this.getMinecartInvPickupFrom() == null) {
            return false;
        }

        Inventory sourceInv = this.getMinecartInvPickupFrom().getInventory();
        for (int i = 0; i < sourceInv.getSize(); i++) {
            Item sourceItem = sourceInv.getItem(i);
            if (sourceItem.isNull()) continue;

            Item itemToAdd = sourceItem.clone();
            itemToAdd.setCount(1);

            Item[] leftover = this.inventory.addItem(itemToAdd);
            if (leftover.length == 0) {
                sourceItem.setCount(sourceItem.getCount() - 1);
                sourceInv.setItem(i, sourceItem);
                return true;
            }
        }
        return false;
    }

    public boolean pickupItems() {
        return this.pickupItems(this.pickupArea);
    }

    public boolean pushItemsIntoMinecart() {
        if (this.getMinecartInvPushTo() == null || this.inventory.isEmpty()) {
            return false;
        }

        Inventory targetInv = this.getMinecartInvPushTo().getInventory();
        if (targetInv.isFull()) {
            return false;
        }

        for (int i = 0; i < this.inventory.getSize(); i++) {
            Item item = this.inventory.getItem(i);
            if (item.isNull()) continue;

            Item itemToAdd = item.clone();
            itemToAdd.setCount(1);

            Item[] leftover = targetInv.addItem(itemToAdd);
            if (leftover.length == 0) {
                item.setCount(item.getCount() - 1);
                this.inventory.setItem(i, item);
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean pushItems() {
        if (this.inventory.isEmpty()) {
            return false;
        }

        int blockData = this.level.getBlockDataAt(this.getFloorX(), this.getFloorY(), this.getFloorZ()) & 0x7;
        Position side = this.getSide(BlockFace.fromIndex(blockData));
        Block block = this.level.getBlock(side);
        BlockEntity be = this.level.getBlockEntity(side);

        if ((be instanceof BlockEntityHopper && blockData == 0) ||
                (!(be instanceof InventoryHolder) && !(block instanceof BlockComposter))) {
            return false;
        }

        if (block instanceof BlockComposter composter) {
            if (composter.isFull()) return false;
            for (int i = 0; i < this.inventory.getSize(); i++) {
                Item item = this.inventory.getItem(i);
                if (item.isNull()) continue;

                int chance = BlockComposter.getChance(item);
                if (chance > 0) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);
                    if (composter.addItem(itemToAdd, null, chance)) {
                        item.setCount(item.getCount() - 1);
                        this.inventory.setItem(i, item);
                        return true;
                    }
                }
            }
            return false;
        }

        if (be instanceof BlockEntityFurnace furnace) {
            FurnaceInventory targetInv = furnace.getInventory();
            if (targetInv.isFull()) return false;

            for (int i = 0; i < this.inventory.getSize(); i++) {
                Item item = this.inventory.getItem(i);
                if (item.isNull()) continue;

                Item itemToAdd = item.clone();
                itemToAdd.setCount(1);

                if (blockData == 0) {
                    Item smelting = targetInv.getSmelting();
                    if (smelting.isNull()) {
                        InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            targetInv.setSmelting(itemToAdd);
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItem(i, item);
                            return true;
                        }
                    } else if (smelting.getId() == itemToAdd.getId() &&
                            smelting.getDamage() == itemToAdd.getDamage() &&
                            smelting.getCount() < smelting.getMaxStackSize()) {
                        InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            smelting.setCount(smelting.getCount() + 1);
                            targetInv.setSmelting(smelting);
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItem(i, item);
                            return true;
                        }
                    }
                } else if (Fuel.duration.containsKey(itemToAdd.getId())) {
                    Item fuel = targetInv.getFuel();
                    if (fuel.isNull()) {
                        InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            targetInv.setFuel(itemToAdd);
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItem(i, item);
                            return true;
                        }
                    } else if (fuel.getId() == itemToAdd.getId() &&
                            fuel.getDamage() == itemToAdd.getDamage() &&
                            fuel.getCount() < fuel.getMaxStackSize()) {
                        InventoryMoveItemEvent ev = new InventoryMoveItemEvent(this.inventory, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        this.server.getPluginManager().callEvent(ev);
                        if (!ev.isCancelled()) {
                            fuel.setCount(fuel.getCount() + 1);
                            targetInv.setFuel(fuel);
                            item.setCount(item.getCount() - 1);
                            this.inventory.setItem(i, item);
                            return true;
                        }
                    }
                }
            }
            return false;
        }

        Inventory target = ((InventoryHolder) be).getInventory();
        if (target.isFull()) return false;

        for (int i = 0; i < this.inventory.getSize(); i++) {
            Item item = this.inventory.getItem(i);
            if (item.isNull()) continue;

            Item itemToAdd = item.clone();
            itemToAdd.setCount(1);

            Item[] leftover = target.addItem(itemToAdd);
            if (leftover.length == 0) {
                item.setCount(item.getCount() - 1);
                this.inventory.setItem(i, item);
                return true;
            }
        }

        return false;
    }

    @Override
    public void close() {
        if (!this.closed) {
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