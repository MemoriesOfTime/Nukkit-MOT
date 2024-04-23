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
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

/**
 * @author CreeperFace
 */
public class BlockHopper extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityHopper> {

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
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public double getResistance() {
        return 24;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
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
            Inventory inventory = this.getInventory();

            if (inventory.isFull()) {
                return false;
            }

            BlockEntity blockEntity = this.getPosition().getLevel().getBlockEntity(this.getPosition().up());
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

                InventoryMoveItemEvent ev = new InventoryMoveItemEvent(null, inventory, this, item, InventoryMoveItemEvent.Action.PICKUP);
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
