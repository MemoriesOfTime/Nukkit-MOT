package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.level.particle.BoneMealParticle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockCarpetMossPale extends BlockTransparentMeta implements BlockPropertiesHelper {

    public enum SideType {
        NONE, SHORT, TALL
    }

    public static final BlockProperty<SideType> SIDE_EAST = new EnumBlockProperty<>("pale_moss_carpet_side_east", false, SideType.values());
    public static final BlockProperty<SideType> SIDE_NORTH = new EnumBlockProperty<>("pale_moss_carpet_side_north", false, SideType.values());
    public static final BlockProperty<SideType> SIDE_SOUTH = new EnumBlockProperty<>("pale_moss_carpet_side_south", false, SideType.values());
    public static final BlockProperty<SideType> SIDE_WEST = new EnumBlockProperty<>("pale_moss_carpet_side_west", false, SideType.values());

    private static final BlockProperties PROPERTIES = new BlockProperties(
            SIDE_EAST, SIDE_NORTH, SIDE_SOUTH, SIDE_WEST,
            VanillaProperties.UPPER_BLOCK
    );

    public BlockCarpetMossPale() {
        this(0);
    }

    public BlockCarpetMossPale(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_MOSS_CARPET;
    }

    @Override
    public String getName() {
        return "Pale Moss Carpet";
    }

    @Override
    public String getIdentifier() {
        return "minecraft:pale_moss_carpet";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public boolean canBeFlowedInto() {
        return true;
    }

    @Override
    public double getHardness() {
        return 0.1;
    }

    @Override
    public double getResistance() {
        return 0.5;
    }

    @Override
    public boolean isSolid() {
        return !isUpper();
    }

    @Override
    public boolean canPassThrough() {
        return isUpper();
    }

    @Override
    public double getMaxY() {
        if (isUpper()) {
            return this.y;
        }
        return this.y + 0.0625;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        if (isUpper()) {
            return null;
        }
        return super.recalculateBoundingBox();
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face,
                         double fx, double fy, double fz, Player player) {
        Block down = this.down();
        if (down.getId() == AIR) {
            return false;
        }
        this.setUpper(false);
        clearSides();
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public int getBurnChance() {
        return 15;
    }

    @Override
    public int getBurnAbility() {
        return 100;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (isUpper()) {
            return Item.EMPTY_ARRAY;
        }
        return new Item[]{this.toItem()};
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.getId() == Item.DYE && item.getDamage() == 0x0f) {
            if (isUpper()) {
                return true;
            }

            Block above = this.up();
            BlockCarpetMossPale upperCarpet;
            if (above.getId() == this.getId()) {
                upperCarpet = (BlockCarpetMossPale) above;
                if (upperCarpet.isUpper()) {
                    return true;
                }
            } else if (!above.isAir()) {
                return true;
            } else {
                upperCarpet = (BlockCarpetMossPale) Block.get(this.getId());
            }

            upperCarpet.setUpper(true);
            upperCarpet.clearSides();

            for (BlockFace side : BlockFace.Plane.HORIZONTAL) {
                Block neighbor = above.getSide(side);
                if (hasFullSupport(neighbor, side.getOpposite())) {
                    upperCarpet.setSideProperty(side, SideType.TALL);
                }
            }

            if (!hasAnySide(upperCarpet)) {
                return false;
            }

            this.getLevel().setBlock(above, upperCarpet, true);

            if (player != null && !player.isCreative()) {
                item.count--;
            }
            this.level.addParticle(new BoneMealParticle(this));
            return true;
        }
        return false;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            this.getLevel().scheduleUpdate(this, 1);
            return type;
        }

        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            boolean upper = isUpper();

            Block below = this.down();
            if (upper) {
                if (below.getId() != this.getId() || !(below instanceof BlockCarpetMossPale b && !b.isUpper())) {
                    this.getLevel().useBreakOn(this);
                    return Level.BLOCK_UPDATE_NORMAL;
                }
            } else if (below.isAir()) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }

            BlockCarpetMossPale otherCarpet = null;
            if (upper) {
                otherCarpet = (BlockCarpetMossPale) below;
            } else {
                Block above = this.up();
                if (above.getId() == this.getId() && above instanceof BlockCarpetMossPale a && a.isUpper()) {
                    otherCarpet = a;
                }
            }

            int oldMeta = this.getDamage();

            for (BlockFace side : BlockFace.Plane.HORIZONTAL) {
                SideType oldType = this.getSideProperty(side);
                if (oldType == SideType.NONE) {
                    continue;
                }

                if (otherCarpet != null && oldType == SideType.TALL
                        && otherCarpet.getSideProperty(side) == SideType.NONE) {
                    this.setSideProperty(side, SideType.NONE);
                    continue;
                }

                if (!upper && otherCarpet != null
                        && otherCarpet.getSideProperty(side) != SideType.NONE) {
                    this.setSideProperty(side, SideType.TALL);
                    continue;
                }

                Block neighbor = this.getSide(side);
                if (!hasFullSupport(neighbor, side.getOpposite())) {
                    this.setSideProperty(side, SideType.NONE);
                }
            }

            if (this.getDamage() != oldMeta) {
                this.getLevel().setBlock(this, this, true);
            }
            return Level.BLOCK_UPDATE_NORMAL;
        }

        return 0;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_BLOCK_COLOR;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    public boolean isUpper() {
        return this.getBooleanValue(VanillaProperties.UPPER_BLOCK);
    }

    public void setUpper(boolean upper) {
        this.setBooleanValue(VanillaProperties.UPPER_BLOCK, upper);
    }

    private void clearSides() {
        this.setPropertyValue(SIDE_NORTH, SideType.NONE);
        this.setPropertyValue(SIDE_EAST, SideType.NONE);
        this.setPropertyValue(SIDE_SOUTH, SideType.NONE);
        this.setPropertyValue(SIDE_WEST, SideType.NONE);
    }

    private SideType getSideProperty(BlockFace face) {
        return switch (face) {
            case NORTH -> getPropertyValue(SIDE_NORTH);
            case EAST -> getPropertyValue(SIDE_EAST);
            case SOUTH -> getPropertyValue(SIDE_SOUTH);
            case WEST -> getPropertyValue(SIDE_WEST);
            default -> SideType.NONE;
        };
    }

    private void setSideProperty(BlockFace face, SideType type) {
        switch (face) {
            case NORTH -> setPropertyValue(SIDE_NORTH, type);
            case EAST -> setPropertyValue(SIDE_EAST, type);
            case SOUTH -> setPropertyValue(SIDE_SOUTH, type);
            case WEST -> setPropertyValue(SIDE_WEST, type);
        }
    }

    private static boolean hasAnySide(BlockCarpetMossPale carpet) {
        for (BlockFace side : BlockFace.Plane.HORIZONTAL) {
            if (carpet.getSideProperty(side) != SideType.NONE) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasFullSupport(Block block, BlockFace face) {
        if (block.isSolid()) {
            return true;
        }
        return block.getId() == Block.PALE_MOSS_CARPET;
    }
}
