package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityPickupClaim;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.BaseInventory;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.NativeContainerTransaction;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemHopper;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Faceable;
import cn.nukkit.utils.CollisionHelper;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @author CreeperFace
 */
public class BlockHopper extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityHopper> {

    /**
     * 漏斗的碰撞按 BDS 逐盒建模：y10-11px 满幅法兰、其上 2px 厚四壁、内缩 4-12px 的漏斗体（y4-10），
     * 以及随输出朝向变化的出料管（朝下为底部 6-10px 竖管，朝向水平时为该侧 y4-8px 横管）；
     * 法兰以下的四角为空，玩家头部可从侧面探入。
     * <p>
     * Collision per BDS: a full-footprint flange at y10-11px, 2px walls above it, an inset funnel
     * (y4-10) and an output spout that points down (bottom tube) or sideways with the facing.
     * The corners below the flange stay open so entities can enter them from the side.
     */
    private AxisAlignedBB[] recalculateCollisionBoxes() {
        return new AxisAlignedBB[]{
                // 法兰 / flange
                new SimpleAxisAlignedBB(x, y + 10d / 16d, z, x + 1d, y + 11d / 16d, z + 1d),
                // 西壁 / west wall
                new SimpleAxisAlignedBB(x, y + 11d / 16d, z, x + 2d / 16d, y + 1d, z + 1d),
                // 东壁 / east wall
                new SimpleAxisAlignedBB(x + 14d / 16d, y + 11d / 16d, z, x + 1d, y + 1d, z + 1d),
                // 北壁 / north wall
                new SimpleAxisAlignedBB(x + 2d / 16d, y + 11d / 16d, z, x + 14d / 16d, y + 1d, z + 2d / 16d),
                // 南壁 / south wall
                new SimpleAxisAlignedBB(x + 2d / 16d, y + 11d / 16d, z + 14d / 16d, x + 14d / 16d, y + 1d, z + 1d),
                // 漏斗体 / funnel body
                new SimpleAxisAlignedBB(x + 4d / 16d, y + 4d / 16d, z + 4d / 16d, x + 12d / 16d, y + 10d / 16d, z + 12d / 16d),
                spoutBox()
        };
    }

    /** 出料管：朝下（或未用到的朝上）为底部竖管，水平朝向为该侧横管 / The spout tube per output facing. */
    private AxisAlignedBB spoutBox() {
        return switch (this.getFacing()) {
            case NORTH -> new SimpleAxisAlignedBB(
                    x + 6d / 16d, y + 4d / 16d, z, x + 10d / 16d, y + 8d / 16d, z + 4d / 16d);
            case SOUTH -> new SimpleAxisAlignedBB(
                    x + 6d / 16d, y + 4d / 16d, z + 12d / 16d, x + 10d / 16d, y + 8d / 16d, z + 1d);
            case WEST -> new SimpleAxisAlignedBB(
                    x, y + 4d / 16d, z + 6d / 16d, x + 4d / 16d, y + 8d / 16d, z + 10d / 16d);
            case EAST -> new SimpleAxisAlignedBB(
                    x + 12d / 16d, y + 4d / 16d, z + 6d / 16d, x + 1d, y + 8d / 16d, z + 10d / 16d);
            default -> new SimpleAxisAlignedBB(
                    x + 6d / 16d, y, z + 6d / 16d, x + 10d / 16d, y + 4d / 16d, z + 10d / 16d);
        };
    }

    private boolean collidesWithHopper(AxisAlignedBB bb) {
        for (AxisAlignedBB part : recalculateCollisionBoxes()) {
            if (bb.intersectsWith(part)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean collidesWithBB(AxisAlignedBB bb) {
        return collidesWithHopper(bb);
    }

    @Override
    public boolean collidesWithBB(AxisAlignedBB bb, boolean collisionBB) {
        return collisionBB ? collidesWithHopper(bb) : super.collidesWithBB(bb, false);
    }

    @Override
    public void addCollisionBoxesToList(AxisAlignedBB bb, List<AxisAlignedBB> collidingBoxes) {
        for (AxisAlignedBB part : recalculateCollisionBoxes()) {
            if (bb.intersectsWith(part)) {
                collidingBoxes.add(part);
            }
        }
    }

    public BlockHopper() {
        this(0);
    }

    public BlockHopper(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return HOPPER_BLOCK;
    }

    @Override
    public String getName() {
        return "Hopper Block";
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityHopper> getBlockEntityClass() {
        return BlockEntityHopper.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.HOPPER;
    }

    @Override
    public double getHardness() {
        return 3;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public double getResistance() {
        return 24;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        BlockFace facing = face.getOpposite();

        if (facing == BlockFace.UP) {
            facing = BlockFace.DOWN;
        }

        this.setDamage(facing.getIndex());

        boolean powered = this.level.isBlockPowered(this);

        if (powered == this.isEnabled()) {
            this.setEnabled(!powered);
        }

        this.level.setBlock(this, this);

        CompoundTag nbt = new CompoundTag()
                .putList(new ListTag<>("Items"))
                .putString("id", BlockEntity.HOPPER)
                .putInt("x", (int) this.x)
                .putInt("y", (int) this.y)
                .putInt("z", (int) this.z);

        BlockEntity.createBlockEntity(BlockEntity.HOPPER, this.getChunk(), nbt);
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityHopper) {
            player.requestWindowOpen(((BlockEntityHopper) blockEntity).getInventory());
            return true;
        }

        return false;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        BlockEntity blockEntity = this.level.getBlockEntity(this);

        if (blockEntity instanceof BlockEntityHopper) {
            return ContainerInventory.calculateRedstone(((BlockEntityHopper) blockEntity).getInventory());
        }

        return super.getComparatorInputOverride();
    }

    public BlockFace getFacing() {
        return BlockFace.fromIndex(this.getDamage() & 7);
    }

    public boolean isEnabled() {
        return (this.getDamage() & 0x08) != 8;
    }

    public void setEnabled(boolean enabled) {
        if (isEnabled() != enabled) {
            this.setDamage(this.getDamage() ^ 0x08);
        }
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            boolean powered = this.level.isBlockPowered(this);

            if (powered == this.isEnabled()) {
                this.setEnabled(!powered);
                this.level.setBlock(this, this, true, false);
            }

            // The block entity keeps this answer instead of reading the neighbours on every transfer,
            // and wakes itself when the lock is gone.
            BlockEntity be = this.level.getBlockEntity(this);
            if (be instanceof BlockEntityHopper hopper) {
                hopper.setRedstonePowered(powered);
            }

            return type;
        }

        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            // Power arriving through a neighbouring block (lever, button, torch, repeater on it) reaches
            // the hopper only as a redstone update. It used to be ignored, so a hopper that slept locked
            // stayed asleep after the power was gone.
            BlockEntity be = this.level.getBlockEntity(this);
            if (be instanceof BlockEntityHopper hopper) {
                hopper.invalidateRedstonePower();
            }

            return type;
        }

        return 0;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.getTier() >= ItemTool.TIER_WOODEN) {
            return new Item[]{toItem()};
        }

        return Item.EMPTY_ARRAY;
    }

    @Override
    public Item toItem() {
        return new ItemHopper();
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }

    /** The source stays claimed through accepted retirement and destination lease release. */
    private static boolean pickupPublishedItem(IHopper hopper, BaseInventory inventory, EntityItem entity) {
        try (EntityPickupClaim.Lease source = EntityPickupClaim.tryClaim(entity)) {
            if (source == null) return false;
            try (InventoryMutationGuard.Lease destination = InventoryMutationGuard.tryClaim(Collections.singleton(inventory))) {
                if (destination == null) return false;
                Position actor = hopper.getPosition();
                Object actorChunk = hopper instanceof BlockEntity block ? block.chunk
                        : hopper instanceof Entity value ? value.chunk : null;
                HopperPickupContext context = new HopperPickupContext(hopper, inventory, actor.getLevel(),
                        actor.x, actor.y, actor.z, actorChunk, entity.getLevel(), entity.chunk,
                        entity.getId(), inventory.getSize(), inventory.getMaxStackSize());
                Item item = entity.getItem();
                if (!context.matches(entity) || item == null || item.isNull() || !inventory.canAddItem(item)) return false;
                InventoryMoveItemEvent event = new InventoryMoveItemEvent(null, inventory, hopper, item,
                        InventoryMoveItemEvent.Action.PICKUP, entity);
                event.call();
                if (event.isCancelled() || !context.matches(entity)) return false;
                // Callback-authored source or inventory edits precede this transfer's rollback boundary.
                item = entity.getItem();
                if (item == null || item.isNull() || item.getCount() <= 0) return false;
                Item offered = item.clone();
                Map<Integer, Item> before = new HashMap<>();
                inventory.slots.forEach((slot, stack) -> before.put(slot, stack.clone()));
                InventoryMutationGuard.NativeMetadataSnapshot metadata = destination.captureNativeMetadata();
                Item[] remainder;
                int accepted;
                boolean refused;
                try {
                    remainder = inventory.addItem(offered.clone());
                    int left = remainder.length == 0 ? 0 : remainder.length == 1 ? remainder[0].getCount() : -1;
                    accepted = offered.getCount() - left;
                    refused = left < 0 || accepted <= 0 || !context.matches(entity) || !metadata.matches()
                            || !entity.getItem().equalsExact(offered)
                            || !matchesPickupSlots(inventory, before, offered, accepted);
                    if (refused) {
                        restorePickupSlots(inventory, before, metadata);
                        restorePickupCount(context, entity, offered);
                    }
                } catch (RuntimeException | Error failure) {
                    try { restorePickupSlots(inventory, before, metadata); restorePickupCount(context, entity, offered); }
                    catch (RuntimeException | Error cleanup) { if (cleanup != failure) failure.addSuppressed(cleanup); }
                    try { inventory.sendContents(inventory.getViewers()); }
                    catch (RuntimeException | Error update) { if (update != failure) failure.addSuppressed(update); }
                    throw failure;
                }
                if (refused) {
                    // This resync is outside the provisional-write catch: a viewer callback must not
                    // replay the old snapshot over a separate change made after this rollback.
                    inventory.sendContents(inventory.getViewers());
                    return false;
                }
                // No rollback after accepted source retirement. Match the native accepted cooldown
                // before the destination lease drains deferred close callbacks.
                Throwable failure = null;
                try { hopper.setTransferCooldown(8); }
                catch (RuntimeException | Error effect) { failure = effect; }
                try {
                    if (remainder.length == 0) entity.close();
                    else entity.getItem().setCount(remainder[0].getCount());
                } catch (RuntimeException | Error retirement) {
                    if (failure == null) failure = retirement;
                    else if (failure != retirement) failure.addSuppressed(retirement);
                }
                if (failure instanceof RuntimeException exception) throw exception;
                if (failure instanceof Error error) throw error;
                return true;
            }
        }
    }

    /** Opaque public Inventory extensions keep their native insertion/remainder contract. */
    private static boolean pickupLegacyItem(IHopper hopper, Inventory inventory, EntityItem entity) {
        try (EntityPickupClaim.Lease source = EntityPickupClaim.tryClaim(entity)) {
            if (source == null) return false;
            Item item = entity.getItem();
            if (item.isNull()) return false;
            int originalCount = item.getCount();
            if (!inventory.canAddItem(item)) return false;
            InventoryMoveItemEvent event = new InventoryMoveItemEvent(null, inventory, hopper, item,
                    InventoryMoveItemEvent.Action.PICKUP, entity);
            event.call();
            if (event.isCancelled()) return false;
            Item[] items = inventory.addItem(item);
            if (items.length == 0) {
                entity.close();
                return true;
            }
            if (items[0].getCount() != originalCount) {
                item.setCount(items[0].getCount());
                return true;
            }
            return false;
        }
    }

    /** Count custody only: no NBT/world rewind, no replay of a closed or replaced actor. */
    private static void restorePickupCount(HopperPickupContext context, EntityItem source, Item offered) {
        if (context.sourceMatches(source)) source.getItem().setCount(offered.getCount());
    }

    private static boolean matchesPickupSlots(BaseInventory inventory, Map<Integer, Item> before, Item offered, int accepted) {
        Set<Integer> slots = new HashSet<>(before.keySet());
        slots.addAll(inventory.slots.keySet());
        int granted = 0;
        for (int slot : slots) {
            Item previous = before.get(slot), current = inventory.slots.get(slot);
            if (previous == null && current == null || previous != null && current != null && previous.equalsExact(current)) continue;
            if (slot < 0 || slot >= inventory.getSize() || current == null || current.isNull()) return false;
            Item expected = offered.clone();
            expected.setCount(current.getCount());
            int oldCount = previous == null || previous.isNull() ? 0 : previous.getCount();
            if (!InventoryMutationGuard.matchesTarget(current, expected)
                    || oldCount != 0 && !previous.equals(offered, true, true)
                    || current.getCount() <= oldCount
                    || current.getCount() > Math.min(current.getMaxStackSize(), inventory.getMaxStackSize())) return false;
            granted += current.getCount() - oldCount;
        }
        return granted == accepted;
    }

    private static void restorePickupSlots(BaseInventory inventory, Map<Integer, Item> before,
                                            InventoryMutationGuard.NativeMetadataSnapshot metadata) {
        metadata.restore();
        inventory.slots.clear();
        inventory.slots.putAll(before);
    }

    private record HopperPickupContext(IHopper hopper, BaseInventory inventory, Level level,
                                      double x, double y, double z, Object actorChunk,
                                      Level sourceLevel, FullChunk sourceChunk, long sourceId, int size, int limit) {
        boolean matches(EntityItem source) {
            Position actor = hopper.getPosition();
            if (inventory.destroyed || hopper.getInventory() != inventory || inventory.getHolder() != hopper
                    || inventory.getSize() != size || inventory.getMaxStackSize() != limit
                    || level == null || actor.getLevel() != level || actor.x != x || actor.y != y || actor.z != z
                    || sourceLevel != level || !sourceMatches(source)) return false;
            if (hopper instanceof BlockEntity block && (block.closed || block.chunk != actorChunk)) return false;
            return !(hopper instanceof Entity entity) || !entity.closed && entity.isAlive() && entity.chunk == actorChunk;
        }
        boolean sourceMatches(EntityItem source) {
            return !source.isClosed() && source.getLevel() == sourceLevel && sourceLevel != null
                    && source.chunk == sourceChunk && source.getId() == sourceId
                    && sourceLevel.getEntity(sourceId) == source;
        }
    }

    public interface IHopper extends InventoryHolder {

        Position getPosition();

        boolean isOnTransferCooldown();

        void setTransferCooldown(int transferCooldown);

        default boolean pullItems() {
            Vector3 up = this.getPosition().up();
            BlockEntity blockEntity = this.getPosition().getLevel().getBlockEntity(up);
            return this.pullItems(blockEntity, this.getPosition().getLevel().getBlock(up));
        }

        default boolean pullItems(BlockEntity blockEntity, Block block) {
            Inventory inventory = this.getInventory();

            if (inventory.isFull()) {
                return false;
            }

            if (blockEntity instanceof InventoryHolder holder) {
                Inventory source = holder.getInventory();
                int start = blockEntity instanceof BlockEntityFurnace ? 2 : 0;
                int end = blockEntity instanceof BlockEntityFurnace ? 3 : source.getSize();
                for (int i = start; i < end; i++) {
                    if (NativeContainerTransaction.move(this, source, i, inventory, -1,
                            () -> holder.getInventory() == source && this.getInventory() == inventory,
                            () -> this.setTransferCooldown(8))) return true;
                }
            } else {
                if (block instanceof BlockComposter composter) {
                    if (!composter.isFull()) {
                        return false;
                    }
                    Item item = composter.empty();
                    if (item == null || item.isNull()) {
                        return false;
                    }
                    Item itemToAdd = item.clone();
                    itemToAdd.setCount(1);
                    if (!inventory.canAddItem(itemToAdd)) {
                        return false;
                    }
                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(null, inventory, this, item, InventoryMoveItemEvent.Action.PICKUP);
                    ev.call();
                    if (ev.isCancelled()) {
                        return false;
                    }
                    Item[] items = inventory.addItem(itemToAdd);
                    return items.length < 1;
                }
            }
            return false;
        }

        default boolean pickupItems(AxisAlignedBB pickupArea) {
            Inventory inventory = this.getInventory();
            if (inventory.isFull()) {
                return false;
            }

            boolean pickedUpItem = false;

            for (Entity entity : CollisionHelper.getPickupEntities(this.getPosition().getLevel(), pickupArea, true)) {
                if (entity.isClosed() || entity.isPublicationDeferred() || !(entity instanceof EntityItem)) {
                    continue;
                }

                if (inventory instanceof BaseInventory nativeInventory
                        ? pickupPublishedItem(this, nativeInventory, (EntityItem) entity)
                        : pickupLegacyItem(this, inventory, (EntityItem) entity)) {
                    pickedUpItem = true;
                }
            }

            return pickedUpItem;
        }

        boolean pushItems();
    }
}
