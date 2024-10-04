package cn.nukkit.block;

import cn.nukkit.item.ItemTool;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.BlockColor;
import lombok.Getter;

import java.util.Arrays;
import java.util.stream.Collectors;

import static cn.nukkit.utils.BlockColor.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockWall extends BlockTransparentMeta {

    public static final int NONE_MOSSY_WALL = 0;
    public static final int MOSSY_WALL = 1;

    public static final int WALL_BLOCK_TYPE_BIT = 0b1111;

    public BlockWall() {
        this(0);
    }

    public BlockWall(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return STONE_WALL;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 30;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    public WallType getWallType() {
        return WallType.values()[this.getDamage(WALL_BLOCK_TYPE_BIT)];
    }

    public void setWallType(WallType wallType) {
        this.setDamage(WALL_BLOCK_TYPE_BIT, wallType.ordinal());
    }

    @Override
    public String getName() {
        return this.getWallType().getTypeName();
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {

        boolean north = this.canConnect(this.getSide(BlockFace.NORTH));
        boolean south = this.canConnect(this.getSide(BlockFace.SOUTH));
        boolean west = this.canConnect(this.getSide(BlockFace.WEST));
        boolean east = this.canConnect(this.getSide(BlockFace.EAST));

        double n = north ? 0 : 0.25;
        double s = south ? 1 : 0.75;
        double w = west ? 0 : 0.25;
        double e = east ? 1 : 0.75;

        if (north && south && !west && !east) {
            w = 0.3125;
            e = 0.6875;
        } else if (!north && !south && west && east) {
            n = 0.3125;
            s = 0.6875;
        }

        return new SimpleAxisAlignedBB(
                this.x + w,
                this.y,
                this.z + n,
                this.x + e,
                this.y + 1.5,
                this.z + s
        );
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    public boolean canConnect(Block block) {
        return (!(block.getId() != COBBLE_WALL && block.getId() != FENCE_GATE)) || block.isSolid() && !block.isTransparent();
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Getter
    public enum WallType {
        COBBLESTONE,
        MOSSY_COBBLESTONE,
        GRANITE(DIRT_BLOCK_COLOR),
        DIORITE(QUARTZ_BLOCK_COLOR),
        ANDESITE,
        SANDSTONE(SAND_BLOCK_COLOR),
        BRICK(RED_BLOCK_COLOR),
        STONE_BRICK,
        MOSSY_STONE_BRICK,
        END_BRICK(SAND_BLOCK_COLOR),
        NETHER_BRICK(NETHERRACK_BLOCK_COLOR),
        PRISMARINE(CYAN_BLOCK_COLOR),
        RED_SANDSTONE(ORANGE_BLOCK_COLOR),
        RED_NETHER_BRICK(NETHERRACK_BLOCK_COLOR);

        private final BlockColor color;
        private final String typeName;

        WallType(BlockColor color) {
            this.color = color;
            String name = Arrays.stream(name().split("_"))
                    .map(part-> part.charAt(0) + part.substring(1).toLowerCase())
                    .collect(Collectors.joining(" "));
            typeName = name + " Wall";
        }

        WallType() {
            this(STONE_BLOCK_COLOR);
        }

    }
}
