package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Created on 2015/12/7 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockFence extends BlockTransparentMeta {

    /**
     * v2193 起栅栏连接由 connection_* 方块状态驱动（客户端不再自动计算），
     * 连接位编码进 meta 高 4 位，低 4 位保留给木种等 legacy 数据。
     * <p>
     * Since v2193 fence connections are driven by connection_* block states (the client no longer
     * derives them locally); they are encoded into the upper 4 meta bits, leaving the lower 4 for legacy data.
     */
    public static final int FLAG_CONNECTION_NORTH = 0x10;
    public static final int FLAG_CONNECTION_EAST = 0x20;
    public static final int FLAG_CONNECTION_SOUTH = 0x40;
    public static final int FLAG_CONNECTION_WEST = 0x80;
    public static final int CONNECTION_FLAGS = FLAG_CONNECTION_NORTH | FLAG_CONNECTION_EAST
            | FLAG_CONNECTION_SOUTH | FLAG_CONNECTION_WEST;

    public static final int FENCE_OAK = 0;
    public static final int FENCE_SPRUCE = 1;
    public static final int FENCE_BIRCH = 2;
    public static final int FENCE_JUNGLE = 3;
    public static final int FENCE_ACACIA = 4;
    public static final int FENCE_DARK_OAK = 5;

    public BlockFence() {
        this(0);
    }

    public BlockFence(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return FENCE;
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public double getResistance() {
        return 15;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public String getName() {
        String[] names = new String[]{
                "Oak Fence",
                "Spruce Fence",
                "Birch Fence",
                "Jungle Fence",
                "Acacia Fence",
                "Dark Oak Fence",
                "",
                ""
        };
        return names[this.getDamage() & 0x07];
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        boolean north = this.canConnect(this.north());
        boolean south = this.canConnect(this.south());
        boolean west = this.canConnect(this.west());
        boolean east = this.canConnect(this.east());
        double n = north ? 0 : 0.375;
        double s = south ? 1 : 0.625;
        double w = west ? 0 : 0.375;
        double e = east ? 1 : 0.625;
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
    public int getBurnChance() {
        return 5;
    }

    @Override
    public int getBurnAbility() {
        return 20;
    }

    public boolean canConnect(Block block) {
        return (block instanceof BlockFence || block instanceof BlockFenceGate) || block.isSolid() && !block.isTransparent();
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
    public BlockColor getColor() {
        switch (this.getDamage() & 0x07) {
            default:
            case FENCE_OAK: //OAK
                return BlockColor.WOOD_BLOCK_COLOR;
            case FENCE_SPRUCE: //SPRUCE
                return BlockColor.SPRUCE_BLOCK_COLOR;
            case FENCE_BIRCH: //BIRCH
                return BlockColor.SAND_BLOCK_COLOR;
            case FENCE_JUNGLE: //JUNGLE
                return BlockColor.DIRT_BLOCK_COLOR;
            case FENCE_ACACIA: //ACACIA
                return BlockColor.ORANGE_BLOCK_COLOR;
            case FENCE_DARK_OAK: //DARK OAK
                return BlockColor.BROWN_BLOCK_COLOR;
        }
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, this.getDamage() & ~CONNECTION_FLAGS);
    }
}
