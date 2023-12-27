package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.SimpleAxisAlignedBB;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class BlockSlab extends BlockTransparentMeta {

    public static final int SLAB_BLOCK_TYPE_BIT = 0x07;
    public static final int SLAB_TOP_BIT = 0x08;

    protected final int doubleSlab;

    public BlockSlab(int meta, int doubleSlab) {
        super(meta);
        this.doubleSlab = doubleSlab;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        if ((this.getDamage() & SLAB_TOP_BIT) > 0) {
            return new SimpleAxisAlignedBB(this.x, this.y + 0.5, this.z, this.x + 1, this.y + 1, this.z + 1);
        } else {
            return new SimpleAxisAlignedBB(this.x, this.y, this.z, this.x + 1, this.y + 0.5, this.z + 1);
        }
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return getToolType() < ItemTool.TYPE_AXE ? 30 : 15;
    }

    @Override
    public int getWaterloggingLevel() {
        return 1;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(this.getDamage() & SLAB_BLOCK_TYPE_BIT);
        if (face == BlockFace.DOWN) {
            if (target instanceof BlockSlab && (target.getDamage() & SLAB_TOP_BIT) == SLAB_TOP_BIT && (target.getDamage() & SLAB_BLOCK_TYPE_BIT) == (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
                this.getLevel().setBlock(target, Block.get(doubleSlab, this.getDamage()), true);

                return true;
            } else if (block instanceof BlockSlab && (block.getDamage() & SLAB_BLOCK_TYPE_BIT) == (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
                this.getLevel().setBlock(block, Block.get(doubleSlab, this.getDamage()), true);

                return true;
            } else {
                this.setDamage(this.getDamage() | SLAB_TOP_BIT);
            }
        } else if (face == BlockFace.UP) {
            if (target instanceof BlockSlab && (target.getDamage() & SLAB_TOP_BIT) == 0 && (target.getDamage() & SLAB_BLOCK_TYPE_BIT) == (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
                this.getLevel().setBlock(target, Block.get(doubleSlab, this.getDamage()), true);

                return true;
            } else if (block instanceof BlockSlab && (block.getDamage() & SLAB_BLOCK_TYPE_BIT) == (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
                this.getLevel().setBlock(block, Block.get(doubleSlab, this.getDamage()), true);

                return true;
            }
            //TODO: check for collision
        } else {
            if (block instanceof BlockSlab) {
                if ((block.getDamage() & SLAB_BLOCK_TYPE_BIT) == (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
                    this.getLevel().setBlock(block, Block.get(doubleSlab, this.getDamage()), true);

                    return true;
                }

                return false;
            } else {
                if (fy > 0.5) {
                    this.setDamage(this.getDamage() | SLAB_TOP_BIT);
                }
            }
        }

        if (block instanceof BlockSlab && (target.getDamage() & SLAB_BLOCK_TYPE_BIT) != (this.getDamage() & SLAB_BLOCK_TYPE_BIT)) {
            return false;
        }
        this.getLevel().setBlock(block, this, true, true);

        return true;
    }

    @Override
    public boolean isTransparent() {
        //HACK: Fix unable to place many blocks on slabs
        return (this.getDamage() & SLAB_TOP_BIT) <= 0;
    }
}
