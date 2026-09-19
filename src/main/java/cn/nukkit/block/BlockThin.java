package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.LevelException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created on 2015/12/6 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public abstract class BlockThin extends BlockTransparentMeta {

    /**
     * v2193 起玻璃板/铁栏的连接由 connection_* 方块状态驱动（客户端不再自动计算），
     * 连接位编码进 meta 高 4 位，低 4 位保留给染色等 legacy 数据。
     * <p>
     * Since v2193 pane/bars connections are driven by connection_* block states (the client no longer
     * derives them locally); they are encoded into the upper 4 meta bits, leaving the lower 4 for legacy data.
     */
    public static final int FLAG_CONNECTION_NORTH = 0x10;
    public static final int FLAG_CONNECTION_EAST = 0x20;
    public static final int FLAG_CONNECTION_SOUTH = 0x40;
    public static final int FLAG_CONNECTION_WEST = 0x80;
    public static final int CONNECTION_FLAGS = FLAG_CONNECTION_NORTH | FLAG_CONNECTION_EAST
            | FLAG_CONNECTION_SOUTH | FLAG_CONNECTION_WEST;

    protected BlockThin() {
    }

    protected BlockThin(int meta) {
        super(meta);
    }

    @Override
    public boolean isSolid() {
        return false;
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
     * 按邻居重算四个连接位并写回 meta / Recomputes the four connection bits from neighbours into meta.
     *
     * @return meta 是否发生变化 / whether the meta changed
     */
    public boolean updateConnections() {
        int meta = this.getDamage() & ~CONNECTION_FLAGS;
        if (this.canConnect(this.north())) {
            meta |= FLAG_CONNECTION_NORTH;
        }
        if (this.canConnect(this.east())) {
            meta |= FLAG_CONNECTION_EAST;
        }
        if (this.canConnect(this.south())) {
            meta |= FLAG_CONNECTION_SOUTH;
        }
        if (this.canConnect(this.west())) {
            meta |= FLAG_CONNECTION_WEST;
        }
        if (meta == this.getDamage()) {
            return false;
        }
        this.setDamage(meta);
        return true;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, @Nullable Player player) {
        this.updateConnections();
        return super.place(item, block, target, face, fx, fy, fz, player);
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.updateConnections()) {
                this.getLevel().setBlock(this, this, true);
            }
            return type;
        }
        return 0;
    }

    @Override
    public Item toItem() {
        Item item = super.toItem();
        item.setDamage(this.getDamage() & ~CONNECTION_FLAGS);
        return item;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        double f = 0.4375;
        double f1 = 0.5625;
        double f2 = 0.4375;
        double f3 = 0.5625;
        try {
            boolean flag = this.canConnect(this.north());
            boolean flag1 = this.canConnect(this.south());
            boolean flag2 = this.canConnect(this.west());
            boolean flag3 = this.canConnect(this.east());
            if ((!flag2 || !flag3) && (flag2 || flag3 || flag || flag1)) {
                if (flag2) {
                    f = 0;
                } else if (flag3) {
                    f1 = 1;
                }
            } else {
                f = 0;
                f1 = 1;
            }
            if ((!flag || !flag1) && (flag2 || flag3 || flag || flag1)) {
                if (flag) {
                    f2 = 0;
                } else if (flag1) {
                    f3 = 1;
                }
            } else {
                f2 = 0;
                f3 = 1;
            }
        } catch (LevelException ignore) {}
        return new SimpleAxisAlignedBB(
                this.x + f,
                this.y,
                this.z + f2,
                this.x + f1,
                this.y + 1,
                this.z + f3
        );
    }

    public boolean canConnect(Block block) {
        return block instanceof BlockWall
                || block.isSolid()
                || block.getId() == this.getId()
                || block.getId() == GLASS_PANE
                || block.getId() == GLASS;
    }
}
