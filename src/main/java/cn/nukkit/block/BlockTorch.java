package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.blockproperty.ArrayBlockProperty;
import cn.nukkit.block.blockproperty.BlockProperties;
import cn.nukkit.block.blockproperty.BlockProperty;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.BlockColor;
import cn.nukkit.utils.Faceable;
import lombok.RequiredArgsConstructor;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;

/**
 * Created on 2015/12/2 by xtypr.
 * Package cn.nukkit.block in project Nukkit .
 */
public class BlockTorch extends BlockFlowable implements Faceable {

    public static final BlockProperty<TorchAttachment> TORCH_FACING_DIRECTION = new ArrayBlockProperty<>("torch_facing_direction", false, TorchAttachment.class);
    
    public static final BlockProperties PROPERTIES = new BlockProperties(TORCH_FACING_DIRECTION);

    private static final short[] faces = new short[]{
            0, //0, never used
            5, //1
            4, //2
            3, //3
            2, //4
            1, //5
    };

    private static final short[] faces2 = new short[]{
            0, //0
            4, //1
            5, //2
            2, //3
            3, //4
            0, //5
            0  //6
    };

    public BlockTorch() {
        this(0);
    }

    public BlockTorch(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Torch";
    }

    @Override
    public int getId() {
        return TORCH;
    }

    @NotNull
    @Override
    public BlockProperties getProperties() {
        return PROPERTIES;
    }

    @Override
    public int getLightLevel() {
        return 14;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            Block below = this.down();
            int side = this.getDamage();
            Block block = this.getSide(BlockFace.fromIndex(faces2[side]));
            int id = block.getId();

            if ((block.isTransparent() && !(side == 0 && (below instanceof BlockFence || below.getId() == COBBLE_WALL))) && id != GLASS && id != STAINED_GLASS && id != HARD_STAINED_GLASS) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        }

        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        int side = faces[face.getIndex()];
        int bid = this.getSide(BlockFace.fromIndex(faces2[side])).getId();
        if ((!target.isTransparent() || bid == GLASS || bid == STAINED_GLASS || bid == HARD_STAINED_GLASS) && face != BlockFace.DOWN) {
            this.setDamage(side);
            this.getLevel().setBlock(block, this, true, true);
            return true;
        }

        Block below = this.down();
        if (!below.isTransparent() || below instanceof BlockFence || below.getId() == COBBLE_WALL || below.getId() == GLASS || below.getId() == STAINED_GLASS || below.getId() == HARD_STAINED_GLASS) {
            this.setDamage(0);
            this.getLevel().setBlock(block, this, true, true);
            return true;
        }
        return false;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    public BlockFace getBlockFace() {
        return getBlockFace(this.getDamage() & 0x07);
    }

    public BlockFace getBlockFace(int meta) {
        switch (meta) {
            case 1:
                return BlockFace.EAST;
            case 2:
                return BlockFace.WEST;
            case 3:
                return BlockFace.SOUTH;
            case 4:
                return BlockFace.NORTH;
            default:
                return BlockFace.UP;
        }
    }

    @RequiredArgsConstructor
    public enum TorchAttachment {
        UNKNOWN(BlockFace.UP),
        WEST(BlockFace.EAST),
        EAST(BlockFace.WEST),
        NORTH(BlockFace.SOUTH),
        SOUTH(BlockFace.NORTH),
        TOP(BlockFace.UP);
        private final BlockFace torchDirection;

        /**
         * The direction that the flame is pointing.
         */
        public BlockFace getTorchDirection() {
            return torchDirection;
        }
        
        @Nullable
        public static TorchAttachment getByTorchDirection(@NotNull BlockFace face) {
            switch (face) {
                default:
                case DOWN:
                    return null;
                case UP:
                    return TOP;
                case EAST:
                    return WEST;
                case WEST:
                    return EAST;
                case SOUTH:
                    return NORTH;
                case NORTH:
                    return SOUTH;
            }
        }

        /**
         * The direction that is touching the attached block.
         */
        @NotNull
        public BlockFace getAttachedFace() {
            switch (this) {
                default:
                case UNKNOWN:
                case TOP:
                    return BlockFace.DOWN;
                case EAST:
                    return BlockFace.EAST;
                case WEST:
                    return BlockFace.WEST;
                case SOUTH:
                    return BlockFace.SOUTH;
                case NORTH:
                    return BlockFace.NORTH;
            }
        }
        
        @Nullable
        public static TorchAttachment getByAttachedFace(@NotNull BlockFace face) {
            switch (face) {
                default:
                case UP:
                    return null;
                case DOWN:
                    return TorchAttachment.TOP;
                case SOUTH:
                    return TorchAttachment.SOUTH;
                case NORTH:
                    return TorchAttachment.NORTH;
                case EAST:
                    return TorchAttachment.EAST;
                case WEST:
                    return TorchAttachment.WEST;
            }
        }
    }
}
