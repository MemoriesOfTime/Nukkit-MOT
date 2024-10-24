package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.IntBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;

import java.util.EnumSet;
import java.util.Set;

public class BlockGlowLichen extends BlockTransparentMeta implements BlockPropertiesHelper {

    // Currently multi_face_direction_bits: 0x01 - down, 0x02 - up, 0x04 - north, 0x08 - south, 0x10 - west, 0x20 - east
    private static final IntBlockProperty MULTI_FACE_DIRECTION = new IntBlockProperty("multi_face_direction_bits", false,  63);

    //TODO check
    public static final int DOWN_BIT = 0x01;
    public static final int UP_BIT = 0x02;
    public static final int NORTH_BIT = 0x10;
    public static final int SOUTH_BIT = 0x04;
    public static final int WEST_BIT = 0x08;
    public static final int EAST_BIT = 0x20;

    private static final BlockProperties PROPERTIES = new BlockProperties(MULTI_FACE_DIRECTION);

    public BlockGlowLichen() {
        this(0);
    }

    public BlockGlowLichen(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return GLOW_LICHEN;
    }

    @Override
    public String getName() {
        return "Glow Lichen";
    }

    @Override
    public String getIdentifier() {
        return "minecraft:glow_lichen";
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (!this.canPlaceOn(block.down(), target) || !target.isSolid()) {
            return false;
        }

        if (block.getId() == GLOW_LICHEN) {
            this.setDamage(block.getDamage());
        } else {
            this.setDamage(0);
        }

        this.setBlockFace(face.getOpposite(), true);
        this.getLevel().setBlock(this, this, false, true);
        return true;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShears()) {
            return new Item[] { this.toItem() };
        }
        return Item.EMPTY_ARRAY;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            this.getLevel().useBreakOn(this, null, null, true);
        } else if (type != Level.BLOCK_UPDATE_NORMAL) {
            return type;
        }

        boolean update = false;
        boolean support = false;

        Set<BlockFace> faces = this.getSupportedFaces();
        for (BlockFace face : faces) {
            Block block = this.getLevel().getBlock(this.getSide(face));
            if (block.isSolid()) {
                support = true;
            } else {
                update = true;
                this.setBlockFace(face, false);
            }
        }

        if (!support) {
            this.getLevel().scheduleUpdate(this, 1);
        } else if (update) {
            this.getLevel().setBlock(this, this, false, true);
        }
        return type;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    @Override
    public double getHardness() {
        return 0.2;
    }

    @Override
    public int getLightLevel() {
        return 7;
    }

    @Override
    public boolean canPassThrough() {
        return true;
    }

    @Override
    public boolean canBeReplaced() {
        return true;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return null;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.GRAY_BLOCK_COLOR;
    }

    public void setBlockFace(BlockFace face, boolean value) {
        switch (face) {
            case UP:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | UP_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~UP_BIT);
                break;
            case DOWN:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | DOWN_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~DOWN_BIT);
                break;
            case NORTH:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | NORTH_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~NORTH_BIT);
                break;
            case SOUTH:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | SOUTH_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~SOUTH_BIT);
                break;
            case WEST:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | WEST_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~WEST_BIT);
                break;
            case EAST:
                this.setIntValue(MULTI_FACE_DIRECTION, value ? this.getIntValue(MULTI_FACE_DIRECTION) | EAST_BIT : this.getIntValue(MULTI_FACE_DIRECTION) & ~EAST_BIT);
                break;
        }
    }

    public boolean hasBlockFace(BlockFace face) {
        return switch (face) {
            case UP -> (this.getIntValue(MULTI_FACE_DIRECTION) & UP_BIT) != 0;
            case DOWN -> (this.getIntValue(MULTI_FACE_DIRECTION) & DOWN_BIT) != 0;
            case NORTH -> (this.getIntValue(MULTI_FACE_DIRECTION) & NORTH_BIT) != 0;
            case SOUTH -> (this.getIntValue(MULTI_FACE_DIRECTION) & SOUTH_BIT) != 0;
            case WEST -> (this.getIntValue(MULTI_FACE_DIRECTION) & WEST_BIT) != 0;
            case EAST -> (this.getIntValue(MULTI_FACE_DIRECTION) & EAST_BIT) != 0;
        };
    }

    public Set<BlockFace> getSupportedFaces() {
        EnumSet<BlockFace> faces = EnumSet.noneOf(BlockFace.class);
        for (BlockFace face : BlockFace.values()) {
            if (this.hasBlockFace(face)) {
                faces.add(face);
            }
        }
        return faces;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }
}
