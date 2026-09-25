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

    /**
     * How long a redstone lock read from the world is trusted without an update of the hopper block.
     * Every change of a neighbour's power reaches the hopper as a normal or redstone block update, which
     * refreshes or forgets the lock at once; the window only bounds a notification a redstone source
     * failed to send (a lit torch placed under the block next to the hopper sends none).
     */
    static final int REDSTONE_LOCK_TTL = 40;
    private static final byte LOCK_UNKNOWN = 0;
    private static final byte LOCK_FREE = 1;
    private static final byte LOCK_POWERED = 2;
    private byte redstoneLock = LOCK_UNKNOWN;
    private long redstoneLockTick;

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

        if (!(this.namedTag.get("Items") instanceof ListTag)) {
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
            // The redstone lock and the push target below may reach a neighbouring chunk. At the
            // edge of the loaded area that chunk is not in memory, and the loading getBlock would
            // read it from disk in the tick. Wait for it instead: keep polling without sleeping,
            // so the hopper carries on as soon as the neighbour loads.
            if (!this.isNeighbourhoodLoaded()) {
                this.setTransferCooldown(8);
                return true;
            }

            // Sleep when redstone-locked (checked after cooldown decrement for plugin compatibility)
            if (this.isRedstoneLocked()) {
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

    /**
     * Whether redstone locks the hopper. Reading the power of the six neighbours - and of the six
     * neighbours of every solid one - costs up to 42 block lookups, and was done on every transfer
     * attempt of every awake hopper: half of all hopper time and most of its allocations. Vanilla keeps
     * the lock in the block (toggle_bit / ENABLED) and changes it only from neighbour updates; this
     * keeps the last answer until {@link BlockHopper} reports an update or {@link #REDSTONE_LOCK_TTL}
     * ticks pass.
     */
    private boolean isRedstoneLocked() {
        long now = this.level.getCurrentTick();
        if (this.redstoneLock == LOCK_UNKNOWN || now - this.redstoneLockTick >= REDSTONE_LOCK_TTL
                || now < this.redstoneLockTick) {
            this.redstoneLock = this.level.isBlockPowered(this) ? LOCK_POWERED : LOCK_FREE;
            this.redstoneLockTick = now;
        }
        return this.redstoneLock == LOCK_POWERED;
    }

    /**
     * A normal update of the hopper block has just read the power of its neighbours. A hopper that slept
     * locked is woken once the power is gone - whatever its toggle bit says, which a redstone update
     * never used to change.
     */
    public void setRedstonePowered(boolean powered) {
        boolean wasPowered = this.redstoneLock == LOCK_POWERED;
        this.redstoneLock = powered ? LOCK_POWERED : LOCK_FREE;
        this.redstoneLockTick = this.level.getCurrentTick();
        if (wasPowered && !powered && !this.closed) {
            this.scheduleUpdate();
        }
    }

    /**
     * A redstone update reached the hopper block: power arriving through the block a lever, button,
     * torch or repeater acts on. Forget the lock; the next transfer attempt reads it. A hopper that slept
     * locked is woken to do that read, as nothing else would wake it.
     */
    public void invalidateRedstonePower() {
        boolean wasPowered = this.redstoneLock == LOCK_POWERED;
        this.redstoneLock = LOCK_UNKNOWN;
        if (wasPowered && !this.closed) {
            this.scheduleUpdate();
        }
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
        FullChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
        BlockEntityHopper hopper = loadedHopperAt(level, chunk, x, y, z);
        if (hopper != null) {
            hopper.scheduleUpdate();
        }
    }

    private static void wakeupHopperFacingTo(Level level, int x, int y, int z, int requiredFacing) {
        FullChunk chunk = level.getChunkIfLoaded(x >> 4, z >> 4);
        BlockEntityHopper hopper = loadedHopperAt(level, chunk, x, y, z);
        if (hopper != null && (chunk.getBlockData(x & 0x0f, y, z & 0x0f) & 0x7) == requiredFacing) {
            hopper.scheduleUpdate();
        }
    }

    /**
     * The hopper standing at the position, looked up only in a chunk that is in memory.
     * <p>
     * A hopper in an unloaded chunk does not tick, and it schedules itself when its chunk loads
     * ({@link #initBlockEntity()}), so there is nothing to wake there. Reading that chunk would load
     * it from disk on the main thread - a container on a chunk border woke its unloaded neighbour
     * on every slot change, including the ones made while the container's own chunk was unloading.
     */
    private static BlockEntityHopper loadedHopperAt(Level level, FullChunk chunk, int x, int y, int z) {
        if (chunk == null || level.getBlockIdAt(chunk, x, y, z) != Block.HOPPER_BLOCK) {
            return null;
        }
        BlockEntity be = level.getBlockEntityIfLoaded(chunk, new Vector3(x, y, z));
        return be instanceof BlockEntityHopper hopper && !hopper.closed ? hopper : null;
    }

    /**
     * How far {@link #onUpdate()} reads around the hopper: the push target is one block away, and
     * the redstone lock asks a solid neighbour for its strong power, which a redstone wire two
     * blocks away answers by looking at its own neighbours - three blocks.
     */
    private static final int NEIGHBOURHOOD_REACH = 3;

    /** Whether every chunk {@link #onUpdate()} may read is in memory, so reading never loads one. */
    private boolean isNeighbourhoodLoaded() {
        int x = this.getFloorX();
        int z = this.getFloorZ();
        int minChunkX = (x - NEIGHBOURHOOD_REACH) >> 4;
        int maxChunkX = (x + NEIGHBOURHOOD_REACH) >> 4;
        int minChunkZ = (z - NEIGHBOURHOOD_REACH) >> 4;
        int maxChunkZ = (z + NEIGHBOURHOOD_REACH) >> 4;
        if (minChunkX == maxChunkX && minChunkZ == maxChunkZ) {
            // Only the hopper's own chunk, which is loaded because the hopper ticks.
            return true;
        }
        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                if (!this.level.isChunkLoaded(chunkX, chunkZ)) {
                    return false;
                }
            }
        }
        return true;
    }
}
