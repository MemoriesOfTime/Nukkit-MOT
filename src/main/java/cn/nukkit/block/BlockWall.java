package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.block.custom.properties.EnumBlockProperty;
import cn.nukkit.block.custom.properties.RegisteredBlockProperty;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.LevelDBConstants;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtMapBuilder;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.stream.Collectors;

import static cn.nukkit.block.properties.VanillaProperties.*;
import static cn.nukkit.utils.BlockColor.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public class BlockWall extends BlockTransparentMeta implements BlockPropertiesHelper {

    private static final double MIN_POST_BB = 5.0 / 16;
    private static final double MAX_POST_BB = 11.0 / 16;

    public static BlockProperty<WallType> WALL_TYPE = new EnumBlockProperty<>("wall_type", false, WallType.values()); //not vanilla, only nk

    protected static final BlockProperties PROPERTIES = new BlockProperties(
            WALL_TYPE,
            WALL_CONNECTION_TYPE_EAST,
            VanillaProperties.WALL_CONNECTION_TYPE_NORTH,
            VanillaProperties.WALL_CONNECTION_TYPE_SOUTH,
            WALL_CONNECTION_TYPE_WEST,
            WALL_POST
    );

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    @Deprecated
    public static final int NONE_MOSSY_WALL = 0;
    @Deprecated
    public static final int MOSSY_WALL = 1;
    @Deprecated
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

    protected boolean shouldBeTall(Block above, BlockFace face) {
        return switch (above.getId()) {
            case AIR/*, SKELETON_SKULL_BLOCK*/ -> false;

            // If the bell is standing and follow the path, make it tall
            case BELL -> {
                BlockBell bell = (BlockBell) above;
                yield bell.getAttachmentType() == BlockBell.TYPE_ATTACHMENT_STANDING
                        && bell.getBlockFace().getAxis() != face.getAxis();
            }
            default -> {
                if (above instanceof BlockWall blockWall) {
                    yield blockWall.getConnectionType(face) != WallConnectionType.NONE;
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

    public boolean autoConfigureState() {
        final int previousMeta = this.getDamage();

        setWallPost(true);

        Block above = up(1, 0);

        for (BlockFace blockFace : BlockFace.Plane.HORIZONTAL) {
            Block side = getSideAtLayer(0, blockFace);
            if (canConnect(side)) {
                try {
                    connect(blockFace, above, false);
                } catch (RuntimeException e) {
                    log.error("Failed to connect the block {} at {} to {} which is {} at {}",
                            this, getLocation(), blockFace, side, side.getLocation(), e);
                    throw e;
                }
            } else {
                disconnect(blockFace);
            }
        }

        this.setWallPost(recheckPostConditions(above));
        return this.getDamage() != previousMeta;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (autoConfigureState()) {
                this.level.setBlock(this, this, true);
            }
            return type;
        }

        return 0;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        this.autoConfigureState();
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    public boolean isWallPost() {
        return this.getPropertyValue(WALL_POST);
    }

    public void setWallPost(boolean wallPost) {
        this.setPropertyValue(WALL_POST, wallPost);
    }

    public void clearConnections() {
        this.setPropertyValue(WALL_CONNECTION_TYPE_EAST, WallConnectionType.NONE);
        this.setPropertyValue(WALL_CONNECTION_TYPE_WEST, WallConnectionType.NONE);
        this.setPropertyValue(WALL_CONNECTION_TYPE_NORTH, WallConnectionType.NONE);
        this.setPropertyValue(WALL_CONNECTION_TYPE_SOUTH, WallConnectionType.NONE);
    }

    public Map<BlockFace, WallConnectionType> getWallConnections() {
        EnumMap<BlockFace, WallConnectionType> connections = new EnumMap<>(BlockFace.class);
        for (BlockFace blockFace : BlockFace.Plane.HORIZONTAL) {
            WallConnectionType connectionType = getConnectionType(blockFace);
            if (connectionType != WallConnectionType.NONE) {
                connections.put(blockFace, connectionType);
            }
        }
        return connections;
    }

    public WallConnectionType getConnectionType(BlockFace blockFace) {
        return switch (blockFace) {
            case EAST -> this.getPropertyValue(WALL_CONNECTION_TYPE_EAST);
            case NORTH -> this.getPropertyValue(WALL_CONNECTION_TYPE_NORTH);
            case SOUTH -> this.getPropertyValue(WALL_CONNECTION_TYPE_SOUTH);
            case WEST -> this.getPropertyValue(WALL_CONNECTION_TYPE_WEST);
            default -> WallConnectionType.NONE;
        };
    }

    public boolean setConnection(BlockFace blockFace, WallConnectionType type) {
        return switch (blockFace) {
            case NORTH -> {
                setPropertyValue(WALL_CONNECTION_TYPE_NORTH, type);
                yield true;
            }
            case SOUTH -> {
                setPropertyValue(WALL_CONNECTION_TYPE_SOUTH, type);
                yield true;
            }
            case WEST -> {
                setPropertyValue(WALL_CONNECTION_TYPE_WEST, type);
                yield true;
            }
            case EAST -> {
                setPropertyValue(WALL_CONNECTION_TYPE_EAST, type);
                yield true;
            }
            default -> false;
        };
    }

    /**
     * @return true if it should be a post
     */
    public void autoUpdatePostFlag() {
        setWallPost(recheckPostConditions(up(1, 0)));
    }

    public boolean hasConnections() {
        return getPropertyValue(WALL_CONNECTION_TYPE_EAST) != WallConnectionType.NONE
                || getPropertyValue(WALL_CONNECTION_TYPE_WEST) != WallConnectionType.NONE
                || getPropertyValue(WALL_CONNECTION_TYPE_NORTH) != WallConnectionType.NONE
                || getPropertyValue(WALL_CONNECTION_TYPE_SOUTH) != WallConnectionType.NONE;
    }

    private boolean recheckPostConditions(Block above) {
        // If nothing is connected, it should be a post
        if (!hasConnections()) {
            return true;
        }

        // If it's not straight, it should be a post
        Map<BlockFace, WallConnectionType> connections = getWallConnections();
        if (connections.size() != 2) {
            return true;
        }

        Iterator<Map.Entry<BlockFace, WallConnectionType>> iterator = connections.entrySet().iterator();
        Map.Entry<BlockFace, WallConnectionType> entryA = iterator.next();
        Map.Entry<BlockFace, WallConnectionType> entryB = iterator.next();
        if (entryA.getValue() != entryB.getValue() || entryA.getKey().getOpposite() != entryB.getKey()) {
            return true;
        }

        BlockFace.Axis axis = entryA.getKey().getAxis();

        switch (above.getId()) {
            // These special blocks forces walls to become a post
            case /*FLOWER_POT, SKULL, */CONDUIT, STANDING_BANNER, TURTLE_EGG -> {
                return true;
            }

            // End rods make it become a post if it's placed on the wall
            case END_ROD -> {
                if (((Faceable) above).getBlockFace() == BlockFace.UP) {
                    return true;
                }
            }

            // If the bell is standing and don't follow the path, make it a post
            case BELL -> {
                BlockBell bell = (BlockBell) above;
                if (bell.getAttachmentType() == BlockBell.TYPE_ATTACHMENT_STANDING
                        && bell.getBlockFace().getAxis() == axis) {
                    return true;
                }
            }
            default -> {
                if (above instanceof BlockWall blockWall) {
                    // If the wall above is a post, it should also be a post

                    if (blockWall.isWallPost()) {
                        return true;
                    }

                } else if (above instanceof BlockLantern) {
                    // Lanterns makes this become a post if they are not hanging

                    if (!((BlockLantern) above).isHanging()) {
                        return true;
                    }

                } else if (above.getId() == LEVER || above instanceof BlockTorch || above instanceof BlockButton) {
                    // These blocks make this become a post if they are placed down (facing up)

                    if (((Faceable) above).getBlockFace() == BlockFace.UP) {
                        return true;
                    }

                } else if (above instanceof BlockFenceGate) {
                    // If the gate don't follow the path, make it a post

                    if (((Faceable) above).getBlockFace().getAxis() == axis) {
                        return true;
                    }

                }/* else if (above instanceof BlockConnectable) {
                    // If the connectable block above don't share 2 equal connections, then this should be a post

                    int shared = 0;
                    for (BlockFace connection : ((BlockConnectable) above).getConnections()) {
                        if (connections.containsKey(connection) && ++shared == 2) {
                            break;
                        }
                    }

                    if (shared < 2) {
                        return true;
                    }

                }*/
            }
        }

        // Sign posts always makes the wall become a post
        return above instanceof BlockSignPost;
    }

    public boolean isSameHeightStraight() {
        Map<BlockFace, WallConnectionType> connections = getWallConnections();
        if (connections.size() != 2) {
            return false;
        }

        Iterator<Map.Entry<BlockFace, WallConnectionType>> iterator = connections.entrySet().iterator();
        Map.Entry<BlockFace, WallConnectionType> a = iterator.next();
        Map.Entry<BlockFace, WallConnectionType> b = iterator.next();
        return a.getValue() == b.getValue() && a.getKey().getOpposite() == b.getKey();
    }

    public boolean connect(BlockFace blockFace) {
        return connect(blockFace, true);
    }

    public boolean connect(BlockFace blockFace, boolean recheckPost) {
        if (blockFace.getHorizontalIndex() < 0) {
            return false;
        }

        Block above = getSideAtLayer(0, BlockFace.UP);
        return connect(blockFace, above, recheckPost);
    }

    private boolean connect(BlockFace blockFace, Block above, boolean recheckPost) {
        WallConnectionType type = shouldBeTall(above, blockFace) ? WallConnectionType.TALL : WallConnectionType.SHORT;
        if (setConnection(blockFace, type)) {
            if (recheckPost) {
                this.setWallPost(recheckPostConditions(above));
            }
            return true;
        }
        return false;
    }

    public boolean disconnect(BlockFace blockFace) {
        if (blockFace.getHorizontalIndex() < 0) {
            return false;
        }

        if (setConnection(blockFace, WallConnectionType.NONE)) {
            autoUpdatePostFlag();
            return true;
        }
        return false;
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

    public boolean isConnected(BlockFace face) {
        return getConnectionType(face) != WallConnectionType.NONE;
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

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), this.getWallType().ordinal()), this.getWallType().ordinal(), 1);
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
        NETHER_BRICK(NETHERRACK_BLOCK_COLOR),
        END_BRICK(SAND_BLOCK_COLOR),
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