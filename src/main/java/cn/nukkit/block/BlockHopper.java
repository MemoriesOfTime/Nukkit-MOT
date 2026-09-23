package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityFurnace;
import cn.nukkit.blockentity.BlockEntityHopper;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.event.inventory.InventoryMoveItemEvent;
import cn.nukkit.inventory.ContainerInventory;
import cn.nukkit.inventory.FurnaceInventory;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemHopper;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

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
            return player.addWindow(((BlockEntityHopper) blockEntity).getInventory()) != -1;
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

                // Wake up the hopper block entity when unpowered
                if (!powered) {
                    BlockEntity be = this.level.getBlockEntity(this);
                    if (be instanceof BlockEntityHopper hopper) {
                        hopper.scheduleUpdate();
                    }
                }
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

            if (blockEntity instanceof BlockEntityFurnace) {
                FurnaceInventory inv = ((BlockEntityFurnace) blockEntity).getInventory();
                Item item = inv.getResult();

                if (!item.isNull()) {
                    Item itemToAdd = item.clone();
                    itemToAdd.count = 1;

                    if (!inventory.canAddItem(itemToAdd)) {
                        return false;
                    }

                    InventoryMoveItemEvent ev = new InventoryMoveItemEvent(inv, inventory, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                    ev.call();

                    if (ev.isCancelled()) {
                        return false;
                    }

                    Item[] items = inventory.addItem(itemToAdd);

                    if (items.length <= 0) {
                        item.count--;
                        inv.setResult(item);
                        return true;
                    }
                }
            } else if (blockEntity instanceof InventoryHolder) {
                Inventory inv = ((InventoryHolder) blockEntity).getInventory();

                for (int i = 0; i < inv.getSize(); i++) {
                    Item item = inv.getItem(i);

                    if (!item.isNull()) {
                        Item itemToAdd = item.clone();
                        itemToAdd.count = 1;

                        if (!inventory.canAddItem(itemToAdd)) {
                            continue;
                        }

                        InventoryMoveItemEvent ev = new InventoryMoveItemEvent(inv, inventory, this, itemToAdd, InventoryMoveItemEvent.Action.SLOT_CHANGE);
                        ev.call();

                        if (ev.isCancelled()) {
                            continue;
                        }

                        Item[] items = inventory.addItem(itemToAdd);

                        if (items.length >= 1) {
                            continue;
                        }

                        item.count--;

                        inv.setItem(i, item);
                        return true;
                    }
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

            for (Entity entity : this.getPosition().getLevel().getCollidingEntities(pickupArea)) {
                if (entity.isClosed() || !(entity instanceof EntityItem)) {
                    continue;
                }

                EntityItem itemEntity = (EntityItem) entity;
                Item item = itemEntity.getItem();

                if (item.isNull()) {
                    continue;
                }

                int originalCount = item.getCount();

                if (!inventory.canAddItem(item)) {
                    continue;
                }

                InventoryMoveItemEvent ev = new InventoryMoveItemEvent(null, inventory, this, item, InventoryMoveItemEvent.Action.PICKUP, itemEntity);
                ev.call();

                if (ev.isCancelled()) {
                    continue;
                }

                Item[] items = inventory.addItem(item);

                if (items.length == 0) {
                    entity.close();
                    pickedUpItem = true;
                    continue;
                }

                if (items[0].getCount() != originalCount) {
                    pickedUpItem = true;
                    item.setCount(items[0].getCount());
                }
            }

            return pickedUpItem;
        }

        boolean pushItems();
    }
}
