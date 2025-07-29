package cn.nukkit.block;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;

public abstract class BlockWallFull extends BlockWall implements BlockPropertiesHelper {

    private static final double MIN_POST_BB = 5.0 / 16;
    private static final double MAX_POST_BB = 11.0 / 16;

    protected static final BlockProperties PROPERTIES = new BlockProperties(
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

    public BlockWallFull() {
        super();
    }

    public BlockWallFull(int meta) {
        super(meta);
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

    @Override
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
                if (above instanceof BlockWallFull blockWallFull) {
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
}
