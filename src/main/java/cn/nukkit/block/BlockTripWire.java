package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemString;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.jetbrains.annotations.NotNull;

/**
 * @author CreeperFace
 */
public class BlockTripWire extends BlockFlowable {

    /**
     * v2193 起绊线连接由 connection_* 方块状态驱动（客户端不再自动计算），
     * 编码进 meta bit4-5 以上的高 4 位；低 4 位保留 powered/attached/disarmed。
     * <p>
     * Since v2193 tripwire connections are driven by connection_* block states (the client no longer
     * derives them locally); encoded into the upper 4 meta bits, leaving powered/attached/disarmed below.
     */
    public static final int FLAG_CONNECTION_NORTH = 0x10;
    public static final int FLAG_CONNECTION_EAST = 0x20;
    public static final int FLAG_CONNECTION_SOUTH = 0x40;
    public static final int FLAG_CONNECTION_WEST = 0x80;
    public static final int CONNECTION_FLAGS = FLAG_CONNECTION_NORTH | FLAG_CONNECTION_EAST
            | FLAG_CONNECTION_SOUTH | FLAG_CONNECTION_WEST;

    public BlockTripWire(int meta) {
        super(meta);
    }

    public BlockTripWire() {
        this(0);
    }

    @Override
    public int getId() {
        return TRIPWIRE;
    }

    @Override
    public String getName() {
        return "Tripwire";
    }

    @Override
    public AxisAlignedBB getBoundingBox() {
        return null;
    }

    @Override
    public Item toItem() {
        return new ItemString();
    }

    public boolean isPowered() {
        return (this.getDamage() & 1) > 0;
    }

    public boolean isAttached() {
        return (this.getDamage() & 4) > 0;
    }

    public boolean isDisarmed() {
        return (this.getDamage() & 8) > 0;
    }

    public void setPowered(boolean value) {
        if (value ^ this.isPowered()) {
            this.setDamage(this.getDamage() ^ 0x01);
        }
    }

    public void setAttached(boolean value) {
        if (value ^ this.isAttached()) {
            this.setDamage(this.getDamage() ^ 0x04);
        }
    }

    public void setDisarmed(boolean value) {
        if (value ^ this.isDisarmed()) {
            this.setDamage(this.getDamage() ^ 0x08);
        }
    }

    public boolean isConnectedTo(BlockFace face) {
        return switch (face) {
            case NORTH -> (this.getDamage() & FLAG_CONNECTION_NORTH) != 0;
            case EAST -> (this.getDamage() & FLAG_CONNECTION_EAST) != 0;
            case SOUTH -> (this.getDamage() & FLAG_CONNECTION_SOUTH) != 0;
            case WEST -> (this.getDamage() & FLAG_CONNECTION_WEST) != 0;
            default -> false;
        };
    }

    /**
     * 按邻居重算四个连接位并写回 meta：相邻绊线或面向本线的钩子即连接。
     * <p>
     * Recomputes the four connection bits from neighbours into meta: connects to an adjacent
     * tripwire or a hook facing this wire.
     *
     * @return meta 是否发生变化 / whether the meta changed
     */
    public boolean updateConnections() {
        int meta = this.getDamage() & ~CONNECTION_FLAGS;
        for (BlockFace face : BlockFace.Plane.HORIZONTAL) {
            if (this.canConnect(this.getSide(face), face)) {
                meta |= flagFor(face);
            }
        }
        if (meta == this.getDamage()) {
            return false;
        }
        this.setDamage(meta);
        return true;
    }

    private boolean canConnect(Block neighbour, BlockFace face) {
        if (neighbour instanceof BlockTripWire) {
            return true;
        }
        return neighbour instanceof BlockTripWireHook && ((BlockTripWireHook) neighbour).getFacing() == face.getOpposite();
    }

    private static int flagFor(BlockFace face) {
        return switch (face) {
            case NORTH -> FLAG_CONNECTION_NORTH;
            case EAST -> FLAG_CONNECTION_EAST;
            case SOUTH -> FLAG_CONNECTION_SOUTH;
            case WEST -> FLAG_CONNECTION_WEST;
            default -> 0;
        };
    }

    @Override
    public void onEntityCollide(Entity entity) {
        if (!entity.doesTriggerPressurePlate()) {
            return;
        }

        boolean powered = this.isPowered();

        if (!powered) {
            this.setPowered(true);
            this.level.setBlock(this, this, true, true);
            this.updateHook(false);

            this.level.scheduleUpdate(this, 10);
        }
    }

    public void updateHook(boolean scheduleUpdate) {
        for (BlockFace side : new BlockFace[]{BlockFace.SOUTH, BlockFace.WEST}) {
            for (int i = 1; i < 42; ++i) {
                Block block = this.getSide(side, i);

                if (block instanceof BlockTripWireHook) {
                    BlockTripWireHook hook = (BlockTripWireHook) block;

                    if (hook.getFacing() == side.getOpposite()) {
                        hook.calculateState(false, true, i, this);
                    }

                    /*if (scheduleUpdate) {
                        this.level.scheduleUpdate(hook, 10);
                    }*/
                    break;
                }

                if (block.getId() != Block.TRIPWIRE) {
                    break;
                }
            }
        }
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.updateConnections()) {
                this.level.setBlock(this, this, true);
            }
            return type;
        }

        if (type == Level.BLOCK_UPDATE_SCHEDULED) {
            if (!isPowered()) {
                return type;
            }

            boolean found = false;
            Entity[] e = this.level.getCollidingEntities(this.getCollisionBoundingBox());
            for (Entity entity : e) {
                if (!entity.doesTriggerPressurePlate()) {
                    continue;
                }

                found = true;
            }

            if (found) {
                this.level.scheduleUpdate(this, 10);
            } else {
                this.setPowered(false);
                this.level.setBlock(this, this, true, true);
                this.updateHook(false);
            }
            return type;
        }

        return 0;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        this.updateConnections();
        this.getLevel().setBlock(this, this, true, true);
        this.updateHook(false);

        return true;
    }

    @Override
    public boolean onBreak(Item item) {
        if (item.getId() == Item.SHEARS) {
            this.setDisarmed(true);
            this.level.setBlock(this, this, true, true);
            this.updateHook(false);
            this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
        } else {
            this.setPowered(true);
            this.getLevel().setBlock(this, Block.get(BlockID.AIR), true, true);
            this.updateHook(true);
        }

        return true;
    }

    @Override
    protected AxisAlignedBB recalculateCollisionBoundingBox() {
        return new SimpleAxisAlignedBB(this.x, this.y, this.z, this.x + 1, this.y + 0.5, this.z + 1);
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.FLOW_INTO_BLOCK;
    }

    @Override
    public boolean canBeFlowedInto() {
        return false;
    }
}
