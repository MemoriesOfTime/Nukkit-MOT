package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.Faceable;
import org.jetbrains.annotations.NotNull;

import java.util.List;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class BlockStairs extends BlockSolidMeta implements Faceable {

    private static final short[] faces = new short[]{2, 1, 3, 0};

    /**
     * weirdo_direction 值对应的楼梯高侧（台阶背面）方向，与 JE 的 FACING 语义一致。
     * <p>
     * Full-height side (back face) direction per weirdo_direction value, matching JE's FACING semantics.
     */
    private static final BlockFace[] FACING_BY_DIRECTION = {BlockFace.EAST, BlockFace.WEST, BlockFace.SOUTH, BlockFace.NORTH};

    /**
     * v2193 起楼梯 corner 形状由 minecraft:corner 方块状态驱动（客户端不再自动计算），
     * 编码进 meta bit3-5；bit0-1 为 weirdo_direction、bit2 为 upside_down。
     * <p>
     * Since v2193 stair corner shapes are driven by the minecraft:corner block state (the client no
     * longer derives them locally); encoded into meta bits 3-5, alongside weirdo_direction (bits 0-1)
     * and upside_down_bit (bit 2).
     */
    public static final int CORNER_NONE = 0;
    public static final int CORNER_INNER_LEFT = 1;
    public static final int CORNER_INNER_RIGHT = 2;
    public static final int CORNER_OUTER_LEFT = 3;
    public static final int CORNER_OUTER_RIGHT = 4;
    public static final int CORNER_SHIFT = 3;
    public static final int CORNER_MASK = 0x07 << CORNER_SHIFT;

    protected BlockStairs(int meta) {
        super(meta);
    }

    public int getCorner() {
        return (this.getDamage() & CORNER_MASK) >> CORNER_SHIFT;
    }

    /**
     * 楼梯高侧方向（JE FACING 语义）/ The full-height side direction (JE FACING semantics).
     */
    public BlockFace getStairsFacing() {
        return FACING_BY_DIRECTION[this.getDamage() & 0x03];
    }

    public boolean isUpsideDown() {
        return (this.getDamage() & 0x04) != 0;
    }

    /**
     * 按邻居楼梯重算 corner 形状并写回 meta / Recomputes the corner shape from neighbouring stairs into meta.
     * <p>
     * 算法翻译自 JE StairBlock.getStairsShape：身后邻居决定 OUTER、前方邻居决定 INNER，
     * left/right 由朝向逆时针判定；两侧同朝向同半层的楼梯会阻止取形。
     * <p>
     * Translated from JE StairBlock.getStairsShape: the neighbour behind decides OUTER, the one in
     * front decides INNER, left/right via counter-clockwise rotation; a neighbour with the same
     * facing and half blocks taking the shape.
     *
     * @return meta 是否发生变化 / whether the meta changed
     */
    public boolean updateCorner() {
        int corner = this.computeCorner();
        int meta = (this.getDamage() & ~CORNER_MASK) | (corner << CORNER_SHIFT);
        if (meta == this.getDamage()) {
            return false;
        }
        this.setDamage(meta);
        return true;
    }

    private int computeCorner() {
        BlockFace facing = this.getStairsFacing();
        boolean upsideDown = this.isUpsideDown();

        Block behind = this.getSide(facing);
        if (behind instanceof BlockStairs && ((BlockStairs) behind).isUpsideDown() == upsideDown) {
            BlockFace behindFacing = ((BlockStairs) behind).getStairsFacing();
            if (behindFacing.getAxis() != facing.getAxis() && this.canTakeShape(behindFacing.getOpposite())) {
                return behindFacing == facing.rotateYCCW() ? CORNER_OUTER_LEFT : CORNER_OUTER_RIGHT;
            }
        }

        Block front = this.getSide(facing.getOpposite());
        if (front instanceof BlockStairs && ((BlockStairs) front).isUpsideDown() == upsideDown) {
            BlockFace frontFacing = ((BlockStairs) front).getStairsFacing();
            if (frontFacing.getAxis() != facing.getAxis() && this.canTakeShape(frontFacing)) {
                return frontFacing == facing.rotateYCCW() ? CORNER_INNER_LEFT : CORNER_INNER_RIGHT;
            }
        }

        return CORNER_NONE;
    }

    private boolean canTakeShape(BlockFace face) {
        Block side = this.getSide(face);
        if (side instanceof BlockStairs) {
            BlockStairs stairs = (BlockStairs) side;
            return stairs.getStairsFacing() != this.getStairsFacing() || stairs.isUpsideDown() != this.isUpsideDown();
        }
        return true;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        if ((this.getDamage() & 0x04) > 0) {
            return new SimpleAxisAlignedBB(
                    this.x,
                    this.y + 0.5,
                    this.z,
                    this.x + 1,
                    this.y + 1,
                    this.z + 1
            );
        } else {
            return new SimpleAxisAlignedBB(
                    this.x,
                    this.y,
                    this.z,
                    this.x + 1,
                    this.y + 0.5,
                    this.z + 1
            );
        }
    }

    @Override
    public WaterloggingType getWaterloggingType() {
        return WaterloggingType.WHEN_PLACED_IN_WATER;
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(faces[player != null ? player.getDirection().getHorizontalIndex() : 0]);
        if ((fy > 0.5 && face != BlockFace.UP) || face == BlockFace.DOWN) {
            this.setDamage(this.getDamage() | 0x04); //Upside-down stairs
        }
        this.updateCorner();
        this.getLevel().setBlock(block, this, true, true);

        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (this.updateCorner()) {
                this.getLevel().setBlock(this, this, true);
            }
            return type;
        }
        return 0;
    }

    @Override
    public Item toItem() {
        Item item = super.toItem();
        item.setDamage(0);
        return item;
    }

    @Override
    public void addCollisionBoxesToList(AxisAlignedBB bb, List<AxisAlignedBB> collidingBoxes) {
        for (AxisAlignedBB part : this.collisionParts()) {
            if (bb.intersectsWith(part)) {
                collidingBoxes.add(part);
            }
        }
    }

    /** Both halves of a stair: the slab below (or above) and the step itself. */
    private AxisAlignedBB[] collisionParts() {
        int damage = this.getDamage();
        int side = damage & 0x03;
        double f = 0;
        double f1 = 0.5;
        double f2 = 0.5;
        double f3 = 1;
        if ((damage & 0x04) > 0) {
            f = 0.5;
            f1 = 1;
            f2 = 0;
            f3 = 0.5;
        }

        AxisAlignedBB slab = new SimpleAxisAlignedBB(
                this.x, this.y + f, this.z, this.x + 1, this.y + f1, this.z + 1);
        AxisAlignedBB step = switch (side) {
            case 0 -> new SimpleAxisAlignedBB(
                    this.x + 0.5, this.y + f2, this.z, this.x + 1, this.y + f3, this.z + 1);
            case 1 -> new SimpleAxisAlignedBB(
                    this.x, this.y + f2, this.z, this.x + 0.5, this.y + f3, this.z + 1);
            case 2 -> new SimpleAxisAlignedBB(
                    this.x, this.y + f2, this.z + 0.5, this.x + 1, this.y + f3, this.z + 1);
            default -> new SimpleAxisAlignedBB(
                    this.x, this.y + f2, this.z, this.x + 1, this.y + f3, this.z + 0.5);
        };
        return new AxisAlignedBB[] {slab, step};
    }

    @Override
    public boolean collidesWithBB(AxisAlignedBB bb) {
        int damage = this.getDamage();
        int side = damage & 0x03;
        double f = 0;
        double f1 = 0.5;
        double f2 = 0.5;
        double f3 = 1;
        if ((damage & 0x04) > 0) {
            f = 0.5;
            f1 = 1;
            f2 = 0;
            f3 = 0.5;
        }

        if (bb.intersectsWith(new SimpleAxisAlignedBB(
                this.x,
                this.y + f,
                this.z,
                this.x + 1,
                this.y + f1,
                this.z + 1
        ))) {
            return true;
        }


        if (side == 0) {
            return bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.x + 0.5,
                    this.y + f2,
                    this.z,
                    this.x + 1,
                    this.y + f3,
                    this.z + 1
            ));
        } else if (side == 1) {
            return bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.x,
                    this.y + f2,
                    this.z,
                    this.x + 0.5,
                    this.y + f3,
                    this.z + 1
            ));
        } else if (side == 2) {
            return bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.x,
                    this.y + f2,
                    this.z + 0.5,
                    this.x + 1,
                    this.y + f3,
                    this.z + 1
            ));
        } else if (side == 3) {
            return bb.intersectsWith(new SimpleAxisAlignedBB(
                    this.x,
                    this.y + f2,
                    this.z,
                    this.x + 1,
                    this.y + f3,
                    this.z + 0.5
            ));
        }

        return false;
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getDamage() & 0x7);
    }
}
