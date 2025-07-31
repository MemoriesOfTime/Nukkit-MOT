package cn.nukkit.block;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.custom.properties.RegisteredBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.LevelDBConstants;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import lombok.Getter;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

import static cn.nukkit.utils.BlockColor.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class BlockWall extends BlockTransparentMeta implements BlockPropertiesHelper {

    private static final double MIN_POST_BB = 5.0 / 16;
    private static final double MAX_POST_BB = 11.0 / 16;

    public static BlockProperty<WallType> WALL_TYPE = new EnumBlockProperty<>("wall_type", false, WallType.values()); //not vanilla, only nk

    protected static final BlockProperties PROPERTIES = new BlockProperties(
            WALL_TYPE,
            VanillaProperties.WALL_CONNECTION_TYPE_EAST,
            VanillaProperties.WALL_CONNECTION_TYPE_NORTH,
            VanillaProperties.WALL_CONNECTION_TYPE_SOUTH,
            VanillaProperties.WALL_CONNECTION_TYPE_WEST,
            VanillaProperties.WALL_POST
    );

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

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
        return this.getPropertyValue(WALL_TYPE);
    }

    public void setWallType(WallType wallType) {
        this.setPropertyValue(WALL_TYPE, wallType);
    }

    @Override
    public String getName() {
        return this.getWallType().getTypeName();
    }

    @Override
    public String getIdentifier() {
        return this.getWallType().getIdentifier();
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

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    public void recalculateConnections() {
        for (BlockFace blockFace : BlockFace.HORIZONTALS) {
            if (canConnect(getSide(blockFace))) {
                setConnectionType(blockFace, shouldBeTall(this.getSide(BlockFace.UP), blockFace) ? WallConnectionType.TALL : WallConnectionType.SHORT);
            } else {
                setConnectionType(blockFace, WallConnectionType.NONE);
            }
        }
    }

    public boolean canConnect(Block block) {
        switch (block.getId()) {
            case GLASS_PANE, IRON_BARS, GLASS -> {
                return true;
            }
            default -> {
                if (block instanceof BlockGlassStained || block instanceof BlockGlassPaneStained || block instanceof BlockWall) {
                    return true;
                }
                if (block instanceof BlockFenceGate fenceGate) {
                    return fenceGate.getBlockFace().getAxis() != calculateAxis(this, block);
                }
                if (block instanceof BlockStairs) {
                    return ((BlockStairs) block).getBlockFace().getOpposite() == calculateFace(this, block);
                }
                if (block instanceof BlockTrapdoor trapdoor) {
                    return trapdoor.isOpen() && trapdoor.getBlockFace() == calculateFace(this, trapdoor);
                }
                return block.isSolid() && !block.isTransparent();
            }
        }
    }

    protected boolean shouldBeTall(Block above, BlockFace face) {
        return switch (above.getId()) {
            case AIR/*, SKELETON_SKULL_BLOCK*/ -> false;

            // If the bell is standing and follow the path, make it tall
            default -> {
                if (above instanceof BlockWallIndependentID blockWallFull) {
                    yield blockWallFull.getConnectionType(face) != WallConnectionType.NONE;
                } else if (above instanceof BlockPressurePlateBase || above instanceof BlockStairs) {
                    yield true;
                }
                yield above.isSolid() && !above.isTransparent() || shouldBeTallBasedOnBoundingBox(above, face);
            }
        };
    }

    protected boolean shouldBeTallBasedOnBoundingBox(Block above, BlockFace face) {
        AxisAlignedBB boundingBox = above.getBoundingBox();
        if (boundingBox == null) {
            return false;
        }
        boundingBox = boundingBox.getOffsetBoundingBox(-above.x, -above.y, -above.z);
        if (boundingBox.getMinY() > 0) {
            return false;
        }
        int offset = face.getXOffset();
        if (offset < 0) {
            return boundingBox.getMinX() < MIN_POST_BB
                    && boundingBox.getMinZ() < MIN_POST_BB && MAX_POST_BB < boundingBox.getMaxZ();
        } else if (offset > 0) {
            return MAX_POST_BB < boundingBox.getMaxX()
                    && MAX_POST_BB < boundingBox.getMaxZ() && boundingBox.getMinZ() < MAX_POST_BB;
        } else {
            offset = face.getZOffset();
            if (offset < 0) {
                return boundingBox.getMinZ() < MIN_POST_BB
                        && boundingBox.getMinX() < MIN_POST_BB && MIN_POST_BB < boundingBox.getMaxX();
            } else if (offset > 0) {
                return MAX_POST_BB < boundingBox.getMaxZ()
                        && MAX_POST_BB < boundingBox.getMaxX() && boundingBox.getMinX() < MAX_POST_BB;
            }
        }
        return false;
    }

    public static BlockFace.Axis calculateAxis(Vector3 base, Vector3 side) {
        Vector3 vector = side.subtract(base);
        return vector.x != 0 ? BlockFace.Axis.X : vector.z != 0 ? BlockFace.Axis.Z : BlockFace.Axis.Y;
    }

    public static BlockFace calculateFace(Vector3 base, Vector3 side) {
        Vector3 vector = side.subtract(base);
        BlockFace.Axis axis = vector.x != 0 ? BlockFace.Axis.X : vector.z != 0 ? BlockFace.Axis.Z : BlockFace.Axis.Y;
        double direction = vector.getAxis(axis);
        return BlockFace.fromAxis(direction < 0 ? BlockFace.AxisDirection.NEGATIVE : BlockFace.AxisDirection.POSITIVE, axis);
    }

    public boolean isPost() {
        return this.getPropertyValue(VanillaProperties.WALL_POST);
    }

    public void setPost(boolean post) {
        this.setPropertyValue(VanillaProperties.WALL_POST, post);
    }

    public WallConnectionType getConnectionType(BlockFace blockFace) {
        return switch (blockFace) {
            case EAST -> this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_EAST);
            case NORTH -> this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_NORTH);
            case SOUTH -> this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_SOUTH);
            case WEST -> this.getPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_WEST);
            default -> throw new IllegalStateException("Unexpected value: " + blockFace);
        };
    }

    public void setConnectionType(BlockFace blockFace, WallConnectionType type) {
        switch (blockFace) {
            case EAST -> this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_EAST, type);
            case NORTH -> this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_NORTH, type);
            case SOUTH -> this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_SOUTH, type);
            case WEST -> this.setPropertyValue(VanillaProperties.WALL_CONNECTION_TYPE_WEST, type);
        }
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            int oldMeta = getDamage();

            recalculateConnections();

            if (oldMeta != getDamage()) {
                level.setBlock(this, this, true);
            }
            return type;
        }

        return 0;
    }

    @Override
    public NbtMap getStateNbt() {
        NbtMapBuilder states = NbtMap.builder();
        for (RegisteredBlockProperty property : this.getBlockProperties().getAllProperties()) {
            if (property.getProperty().getName().equals("wall_type")) {
                continue; //skip not vanilla
            }
            if (property.getProperty() instanceof EnumBlockProperty<?>) {
                states.put(property.getProperty().getName(), this.getPropertyValue(property.getProperty()).toString());
            } else {
                states.put(property.getProperty().getName(), this.getPropertyValue(property.getProperty()));
            }
        }
        return NbtMap.builder()
                .putString("name", this.getIdentifier())
                .putCompound("states", states.build())
                .putInt("version", LevelDBConstants.STATE_VERSION)
                .build();
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
        private final String identifier;

        WallType(BlockColor color) {
            this.color = color;
            String name = Arrays.stream(name().split("_"))
                    .map(part -> part.charAt(0) + part.substring(1).toLowerCase(Locale.ROOT))
                    .collect(Collectors.joining(" "));
            typeName = name + " Wall";
            identifier = "minecraft:" + name.toLowerCase(Locale.ROOT) + "_wall";
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
}