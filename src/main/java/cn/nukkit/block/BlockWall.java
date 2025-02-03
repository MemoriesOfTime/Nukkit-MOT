package cn.nukkit.block;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.BlockColor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import static cn.nukkit.utils.BlockColor.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockWall extends BlockTransparentMeta implements BlockPropertiesHelper {

    private static final BlockProperties PROPERTIES = new BlockProperties(VanillaProperties.WALL_TYPE,
            VanillaProperties.WALL_CONNECTION_TYPE_EAST,
            VanillaProperties.WALL_CONNECTION_TYPE_NORTH,
            VanillaProperties.WALL_CONNECTION_TYPE_SOUTH,
            VanillaProperties.WALL_CONNECTION_TYPE_WEST,
            VanillaProperties.WALL_POST);

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
        return this.getPropertyValue(VanillaProperties.WALL_TYPE);
    }

    public void setWallType(WallType wallType) {
        this.setPropertyValue(VanillaProperties.WALL_TYPE, wallType);
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

    public void recalculateConnections() {
        //TODO: post and short wall

        if (canConnect(getSide(BlockFace.NORTH))) {
            setNorthConnectionType(WallConnectionType.TALL);
        } else {
            setNorthConnectionType(WallConnectionType.NONE);
        }
        if (canConnect(getSide(BlockFace.SOUTH))) {
            setSouthConnectionType(WallConnectionType.TALL);
        } else {
            setSouthConnectionType(WallConnectionType.NONE);
        }
        if (canConnect(getSide(BlockFace.WEST))) {
            setWestConnectionType(WallConnectionType.TALL);
        } else {
            setWestConnectionType(WallConnectionType.NONE);
        }
        if (canConnect(getSide(BlockFace.EAST))) {
            setEastConnectionType(WallConnectionType.TALL);
        } else {
            setEastConnectionType(WallConnectionType.NONE);
        }
    }

    public boolean isPost() {
        return this.getPropertyValue(VanillaProperties.WALL_POST);
    }

    public void setPost(boolean post) {
        this.setPropertyValue(VanillaProperties.WALL_POST, post);
    }

    public WallConnectionType getNorthConnectionType() {
        return this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_NORTH);
    }

    public void setNorthConnectionType(WallConnectionType type) {
        this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_NORTH, type);
    }

    public WallConnectionType getEastConnectionType() {
        return this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_EAST);
    }

    public void setEastConnectionType(WallConnectionType type) {
        this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_EAST, type);
    }

    public WallConnectionType getSouthConnectionType() {
        return this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_SOUTH);
    }

    public void setSouthConnectionType(WallConnectionType type) {
        this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_SOUTH, type);
    }

    public WallConnectionType getWestConnectionType() {
        return this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_WEST);
    }

    public void setWestConnectionType(WallConnectionType type) {
        this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_WEST, type);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            level.scheduleUpdate(this, 1);
            return Level.BLOCK_UPDATE_NORMAL;
        }

        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            int oldMeta = getDamage();

            recalculateConnections();

            if (oldMeta != getDamage()) {
                level.setBlock(this, this, true);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        return 0;
    }

    @Override
    public String getIdentifier() {
        return this.getWallType().name().toLowerCase(Locale.ROOT) + "_wall";
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

    public enum WallConnectionType {
        NONE("none"),
        SHORT("short"),
        TALL("tall");

        private final String name;

        WallConnectionType(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }
}