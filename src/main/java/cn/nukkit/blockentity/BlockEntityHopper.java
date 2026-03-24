package cn.nukkit.blockentity;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockComposter;
import cn.nukkit.block.BlockHopper;
import cn.nukkit.event.blockentity.HopperSearchItemEvent;
import cn.nukkit.event.blockentity.HopperUpdateEvent;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.FurnaceInventory;
import cn.nukkit.inventory.HopperInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
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

        HopperUpdateEvent ev = new HopperUpdateEvent(this);
        ev.call();
        if (ev.isCancelled()) {
            return true;
        }

        this.transferCooldown = ev.getTransferCooldown() - 1;

        if (!this.isOnTransferCooldown()) {
            // Sleep when redstone-locked (checked after cooldown decrement for plugin compatibility)
            if (this.level.isBlockPowered(this.getBlock())) {
                return false;
            }

            // Check if there's a container above (for sleep decision later)
            boolean hasContainerAbove;
            BlockEntity blockEntityAbove = this.level.getBlockEntity(this.up());
            Block blockAbove = null;
            if (blockEntityAbove instanceof BlockEntityContainer) {
                hasContainerAbove = true;
            } else {
                blockAbove = this.level.getBlock(this.chunk, this.getFloorX(), this.getFloorY() + 1, this.getFloorZ(), false);
                hasContainerAbove = blockAbove instanceof BlockComposter;
            }

            HopperSearchItemEvent searchEvent = new HopperSearchItemEvent(this, false, this.pickupArea);
            searchEvent.call();

            boolean changed = false;

            if (!searchEvent.isCancelled() && !searchEvent.isCancelPull()) {
                if (!this.inventory.isFull()) {
                    if (hasContainerAbove) {
                        changed = this.pullItems(blockEntityAbove, blockAbove);
                    } else {
                        changed = this.pullItemsFromMinecart() || this.pickupItems(searchEvent.getPickupArea());
                    }
                }
            }

            if (!changed && !searchEvent.isCancelled() && !searchEvent.isCancelPush()) {
                if (!this.inventory.isEmpty()) {
                    changed = this.pushItemsIntoMinecart() || this.pushItems();
                }
            }

            if (changed) {
                this.setTransferCooldown(8);
                this.setDirty();
            } else if (searchEvent.isCancelled()) {
                // Plugin cancelled the search — keep polling to match original behavior
                this.setTransferCooldown(8);
            } else if (!hasContainerAbove && !this.inventory.isFull()) {
                // No container above, may receive ground items — poll with vanilla 8-tick interval
                this.setTransferCooldown(8);
            } else {
                // Sleep — woken by adjacent container changes or own inventory changes
                return false;
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

                    setMinecartInvPickupFrom(null);
                    return true;
                }
            }
        }

        return false;
    }

    public boolean pickupItems() {
        return this.pickupItems(this.pickupArea);
    }

    public boolean pushItemsIntoMinecart() {
        if (this.getMinecartInvPushTo() != null) {
            Inventory holderInventory = this.getMinecartInvPushTo().getInventory();

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

                    setMinecartInvPushTo(null);
                    return true;
                }
            }
        }

        return false;
    }

    @Override
    public boolean pushItems() {
        HopperInventory inv = this.getInventory();
        if (inv.isEmpty()) {
            return false;
        }

        int x = this.getFloorX();
        int y = this.getFloorY();
        int z = this.getFloorZ();
        int blockData = this.level.getBlockDataAt(x, y, z) & 0x7;
        Position side = this.getSide(BlockFace.fromIndex(blockData));
        Block block = this.level.getBlock(side);
        BlockEntity be = this.level.getBlockEntity(side);

        if ((be instanceof BlockEntityHopper && blockData == 0) || (!(be instanceof InventoryHolder) && !(block instanceof BlockComposter))) {
            return false;
        }

        if (block instanceof BlockComposter composter) {
            if (composter.isFull()) {
                return false;
            }
            for (int i = 0; i < inv.getSize(); i++) {
                Item item = inv.getItem(i);
                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    int chance = BlockComposter.getChance(itemToAdd);
                    if (chance > 0 && composter.addItem(itemToAdd, null, chance)) {
                        item.count--;
                        inv.setItem(i, item);
                        this.setDirty();
                        return true;
                    }
                }
            }
            return false;
        }

        if (be instanceof BlockEntityFurnace furnace) {
            FurnaceInventory targetInv = furnace.getInventory();
            if (targetInv.isFull()) {
                return false;
            }

            boolean pushedItem = false;

            for (int i = 0; i < inv.getSize(); i++) {
                Item item = inv.getItem(i);
                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    if (blockData == 0) {
                        Item smelting = targetInv.getSmelting();
                        if (smelting.isNull()) {
                            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inv, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                            this.server.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                targetInv.setSmelting(itemToAdd);
                                item.count--;
                                pushedItem = true;
                            }
                        } else if (smelting.equals(itemToAdd, true, false) && smelting.count < smelting.getMaxStackSize()) {
                            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inv, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                            this.server.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                smelting.count++;
                                targetInv.setSmelting(smelting);
                                item.count--;
                                pushedItem = true;
                            }
                        }
                    } else if (itemToAdd.getFuelTime() != null) {
                        Item fuel = targetInv.getFuel();
                        if (fuel.isNull()) {
                            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inv, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                            this.server.getPluginManager().callEvent(event);
                            if (!event.isCancelled()) {
                                targetInv.setFuel(itemToAdd);
                                item.count--;
                                pushedItem = true;
                            }
                        } else if (fuel.equals(itemToAdd, true, false) && fuel.count < fuel.getMaxStackSize()) {
                            InventoryMoveItemEvent event = new InventoryMoveItemEvent(inv, targetInv, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
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
                        inv.setItem(i, item);
                    }
                }
            }

            return pushedItem;
        } else {
            Inventory target = ((InventoryHolder) be).getInventory();

            if (target.isFull()) {
                return false;
            }

            for (int i = 0; i < inv.getSize(); i++) {
                Item item = inv.getItem(i);

                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);

                    if (!target.canAddItem(itemToAdd)) {
                        continue;
                    }

                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(inv, target, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                    this.server.getPluginManager().callEvent(ev);

                    if (ev.isCancelled()) {
                        continue;
                    }

                    Item[] items = target.addItem(itemToAdd);

                    if (items.length > 0) {
                        continue;
                    }

                    item.count--;
                    inv.setItem(i, item);
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

    /**
     * Notify sleeping hoppers around the given position that a container's inventory changed.
     * This wakes hoppers that pull from above or push into this container.
     */
    public static void wakeupHoppersAround(Level level, int x, int y, int z) {
        wakeupHopperAt(level, x, y - 1, z);
        wakeupHopperFacingTo(level, x, y + 1, z, BlockFace.DOWN.getIndex());
        wakeupHopperFacingTo(level, x, y, z + 1, BlockFace.NORTH.getIndex());
        wakeupHopperFacingTo(level, x, y, z - 1, BlockFace.SOUTH.getIndex());
        wakeupHopperFacingTo(level, x + 1, y, z, BlockFace.WEST.getIndex());
        wakeupHopperFacingTo(level, x - 1, y, z, BlockFace.EAST.getIndex());
    }

    private static void wakeupHopperAt(Level level, int x, int y, int z) {
        if (level.getBlockIdAt(x, y, z) == Block.HOPPER_BLOCK) {
            BlockEntity be = level.getBlockEntity(new Vector3(x, y, z));
            if (be instanceof BlockEntityHopper hopper && !hopper.closed) {
                hopper.scheduleUpdate();
            }
        }
    }

    private static void wakeupHopperFacingTo(Level level, int x, int y, int z, int requiredFacing) {
        if (level.getBlockIdAt(x, y, z) == Block.HOPPER_BLOCK) {
            if ((level.getBlockDataAt(x, y, z) & 0x7) == requiredFacing) {
                BlockEntity be = level.getBlockEntity(new Vector3(x, y, z));
                if (be instanceof BlockEntityHopper hopper && !hopper.closed) {
                    hopper.scheduleUpdate();
                }
            }
        }
    }
}
