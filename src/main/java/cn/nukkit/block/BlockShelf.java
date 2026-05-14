package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BooleanBlockProperty;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.blockentity.BlockEntityShelf;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Abstract base class for Shelf blocks.
 * <p>
 * Adapted from PowerNukkitX.
 */
public abstract class BlockShelf extends BlockTransparentMeta implements Faceable, BlockEntityHolder<BlockEntityShelf>, BlockPropertiesHelper {

    private static final int TYPE_MASK = 0b0000_0011;
    private static final int POWERED_MASK = 0b0000_0100;
    private static final int DIRECTION_MASK = 0b0001_1000;

    private static final IntBlockProperty POWERED_SHELF_TYPE = new IntBlockProperty("powered_shelf_type", false, 3, 0);
    private static final BooleanBlockProperty POWERED_BIT = new BooleanBlockProperty("powered_bit", false);
    private static final BlockProperties PROPERTIES = new BlockProperties(POWERED_SHELF_TYPE, POWERED_BIT, VanillaProperties.CARDINAL_DIRECTION);

    public BlockShelf() {
        this(0);
    }

    public BlockShelf(int meta) {
        super(meta);
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @NotNull
    @Override
    public Class<? extends BlockEntityShelf> getBlockEntityClass() {
        return BlockEntityShelf.class;
    }

    @NotNull
    @Override
    public String getBlockEntityType() {
        return BlockEntity.SHELF;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public int getBurnChance() {
        return 30;
    }

    @Override
    public int getBurnAbility() {
        return 20;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, @Nullable Player player) {
        this.setBlockFace(player != null ? player.getHorizontalFacing().getOpposite() : BlockFace.SOUTH);
        this.level.setBlock(this, this, true);
        this.getOrCreateBlockEntity();
        this.updateConnection(this);
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(@NotNull Item item, @Nullable Player player) {
        return false;
    }

    @Override
    public int onTouch(@NotNull Vector3 vector, @NotNull Item item, @NotNull BlockFace face, float fx, float fy, float fz,
                       @Nullable Player player, PlayerInteractEvent.Action action) {
        if (action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || player == null || player.isSneaking()
                || face != this.getBlockFace() || fy <= 0.25f || fy >= 0.75f) {
            return super.onTouch(vector, item, face, fx, fy, fz, player, action);
        }

        BlockEntityShelf blockEntity = this.getOrCreateBlockEntity();
        if (!this.isGettingPower()) {
            BlockFace blockFace = this.getBlockFace();
            Vector3 faceValues = blockFace.rotateYCCW().getUnitVector();
            double distance = (fx * faceValues.x) + (fz * faceValues.z);
            if (distance < 0) distance++;
            int slot = distance < (1 / 3f) ? 0 : distance < (2 / 3f) ? 1 : 2;

            Inventory inventory = blockEntity.getInventory();
            Item slotItem = inventory.getItem(slot);
            if (!slotItem.isNull()) {
                inventory.clear(slot);
                if (!player.isCreative()) {
                    for (Item leftover : player.getInventory().addItem(slotItem)) {
                        this.level.dropItem(player, leftover);
                    }
                }
            } else if (!item.isNull()) {
                Item stored = item.clone();
                stored.setCount(1);
                inventory.setItem(slot, stored);
                if (!player.isCreative()) {
                    item.count--;
                    player.getInventory().setItemInHand(item);
                }
            } else {
                return 0;
            }
            blockEntity.setDirty();
        } else {
            List<BlockShelf> shelves = getConnectedBlocks();
            for (int i = 0; i < shelves.size(); i++) {
                BlockEntityShelf shelf = shelves.get(i).getOrCreateBlockEntity();
                for (int j = 0; j < shelf.getSize(); j++) {
                    Item shelfItem = shelf.getItem(j);
                    int playerSlot = (i * shelf.getSize()) + j;
                    Item playerItem = player.getInventory().getItem(playerSlot);
                    shelf.getInventory().setItem(j, playerItem);
                    player.getInventory().setItem(playerSlot, shelfItem);
                }
                shelf.setDirty();
            }
        }
        return 1;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_REDSTONE) {
            this.updateConnection(this);
            boolean powered = this.isGettingPower();
            if (powered != this.isPowered()) {
                this.setPowered(powered);
                this.level.setBlock(this, this, true, false);
            }
        }
        return super.onUpdate(type);
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getProperty(DIRECTION_MASK));
    }

    @Override
    public void setBlockFace(BlockFace face) {
        int horizontalIndex = face.getHorizontalIndex();
        this.setProperty(DIRECTION_MASK, horizontalIndex >= 0 ? horizontalIndex : BlockFace.SOUTH.getHorizontalIndex());
    }

    @Override
    public boolean hasComparatorInputOverride() {
        return true;
    }

    @Override
    public int getComparatorInputOverride() {
        Map<Integer, Item> items = this.getOrCreateBlockEntity().getInventory().getContents();
        int output = 0;
        for (Map.Entry<Integer, Item> entry : items.entrySet()) {
            if (!entry.getValue().isNull()) {
                output |= (1 << entry.getKey());
            }
        }
        return output;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{this.toItem()};
    }

    public boolean isGettingPower() {
        return this.level.isBlockPowered(this);
    }

    public boolean isPowered() {
        return this.getProperty(POWERED_MASK) != 0;
    }

    public void setPowered(boolean powered) {
        this.setProperty(POWERED_MASK, powered ? 1 : 0);
    }

    public PoweredShelfType getShelfType() {
        return PoweredShelfType.values()[this.getProperty(TYPE_MASK)];
    }

    public void setShelfType(PoweredShelfType type) {
        this.setProperty(TYPE_MASK, type.ordinal());
    }

    public boolean canConnect(@NotNull BlockShelf shelf) {
        if (!isGettingPower()) {
            return false;
        }
        return switch (getShelfType()) {
            case LEFT -> canConnectToSide(shelf, getBlockFace().rotateYCCW(), PoweredShelfType.RIGHT);
            case RIGHT -> canConnectToSide(shelf, getBlockFace().rotateY(), PoweredShelfType.LEFT);
            default -> true;
        };
    }

    private boolean canConnectToSide(BlockShelf shelf, BlockFace sideFace, PoweredShelfType expectedType) {
        Block sideBlock = getSide(sideFace);
        if (shelf.equals(sideBlock)) {
            return true;
        }
        if (sideBlock instanceof BlockShelf other) {
            return other.getShelfType() == expectedType;
        }
        return false;
    }

    public void updateConnection(@NotNull Block origin) {
        PoweredShelfType newType = PoweredShelfType.UNCONNECTED;

        BlockFace face = getBlockFace().rotateY();
        Block right = getSide(face);
        Block left = getSide(face, -1);

        if (isGettingPower()) {
            boolean connectRight = right instanceof BlockShelf s && s.canConnect(this);
            boolean connectLeft = left instanceof BlockShelf s && s.canConnect(this);

            if (connectLeft && !connectRight) {
                newType = PoweredShelfType.LEFT;
            } else if (!connectLeft && connectRight) {
                newType = PoweredShelfType.RIGHT;
            } else if (connectLeft) {
                newType = determineCenterType(left, right);
            }
        }

        if (newType != getShelfType()) {
            setShelfType(newType);
            level.setBlock(this, this, true, false);

            if (right != origin && right instanceof BlockShelf s) {
                s.updateConnection(this);
            }
            if (left != origin && left instanceof BlockShelf s) {
                s.updateConnection(this);
            }
        }
    }

    private PoweredShelfType determineCenterType(Block left, Block right) {
        if (right instanceof BlockShelf rs && rs.getShelfType() == PoweredShelfType.UNCONNECTED && rs.canConnect(this)) {
            return PoweredShelfType.LEFT;
        }
        if (left instanceof BlockShelf ls && ls.getShelfType() == PoweredShelfType.UNCONNECTED && ls.canConnect(this)) {
            return PoweredShelfType.RIGHT;
        }

        boolean rightIsRight = right instanceof BlockShelf rs2 && rs2.getShelfType() == PoweredShelfType.RIGHT;
        boolean leftIsLeft = left instanceof BlockShelf ls2 && ls2.getShelfType() == PoweredShelfType.LEFT;
        if (rightIsRight && leftIsLeft) {
            return PoweredShelfType.RIGHT;
        }

        boolean rightIsCenter = right instanceof BlockShelf rs3 && rs3.getShelfType() == PoweredShelfType.CENTER;
        boolean leftIsCenter = left instanceof BlockShelf ls3 && ls3.getShelfType() == PoweredShelfType.CENTER;
        if (rightIsCenter) {
            return PoweredShelfType.RIGHT;
        }
        if (leftIsCenter) {
            return PoweredShelfType.LEFT;
        }

        return PoweredShelfType.CENTER;
    }

    protected List<BlockShelf> getConnectedBlocks() {
        if (this.getShelfType() == PoweredShelfType.UNCONNECTED || !this.isGettingPower()) {
            return new ArrayList<>(Collections.singletonList(this));
        }

        List<BlockShelf> shelves = new ArrayList<>();
        BlockFace face = getBlockFace().rotateY();
        Block right = getSide(face);
        Block left = getSide(face, -1);

        switch (getShelfType()) {
            case CENTER -> {
                if (right instanceof BlockShelf shelf) {
                    shelves.add(shelf);
                }
                shelves.add(this);
                if (left instanceof BlockShelf shelf) {
                    shelves.add(shelf);
                }
            }
            case RIGHT -> {
                shelves.add(this);
                if (right instanceof BlockShelf shelf) {
                    shelves.add(shelf);
                    if (shelf.getShelfType() == PoweredShelfType.CENTER) {
                        Block right1 = getSide(face, 2);
                        if (right1 instanceof BlockShelf shelf1) {
                            shelves.add(shelf1);
                        }
                    }
                }
                Collections.reverse(shelves);
            }
            case LEFT -> {
                shelves.add(this);
                if (left instanceof BlockShelf shelf) {
                    shelves.add(shelf);
                    if (shelf.getShelfType() == PoweredShelfType.CENTER) {
                        Block left1 = getSide(face, -2);
                        if (left1 instanceof BlockShelf shelf1) {
                            shelves.add(shelf1);
                        }
                    }
                }
            }
        }
        return shelves;
    }

    private void setProperty(int mask, int value) {
        int shift = Integer.numberOfTrailingZeros(mask);
        int maxValue = mask >>> shift;
        this.setDamage((this.getDamage() & ~mask) | ((value & maxValue) << shift));
    }

    private int getProperty(int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        return (this.getDamage() & mask) >>> shift;
    }

    public enum PoweredShelfType {
        UNCONNECTED,
        RIGHT,
        CENTER,
        LEFT
    }
}
