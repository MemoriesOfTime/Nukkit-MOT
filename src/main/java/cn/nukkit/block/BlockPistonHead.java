package cn.nukkit.block;

import cn.nukkit.blockentity.BlockEntityPistonArm;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;

/**
 * @author CreeperFace
 */
public class BlockPistonHead extends BlockTransparentMeta implements Faceable {

    public BlockPistonHead() {
        this(0);
    }

    public BlockPistonHead(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PISTON_HEAD;
    }

    @Override
    public String getName() {
        return "Piston Head";
    }

    @Override
    public double getResistance() {
        return 1.5;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public Item[] getDrops(Item item) {
        return Item.EMPTY_ARRAY;
    }

    @Override
    public boolean onBreak(Item item) {
        this.level.setBlock(this, Block.get(BlockID.AIR), true, true);
        Block block = this.getSide(this.getBlockFace().getOpposite());

        if (block instanceof BlockPistonBase piston && piston.getBlockFace() == this.getBlockFace()) {
            piston.onBreak(item);

            BlockEntityPistonArm entity = piston.getBlockEntity();
            if (entity != null) {
                entity.close();
            }
        }
        return true;
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (!(getSide(getBlockFace().getOpposite()) instanceof BlockPistonBase)) {
                level.setBlock(this, new BlockAir(), true, false);
            }
            return type;
        }
        return 0;
    }

    @Override
    public BlockFace getBlockFace() {
        BlockFace face = BlockFace.fromIndex(this.getDamage());
        return face.getHorizontalIndex() >= 0 ? face.getOpposite() : face;
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setDamage(face.getIndex());
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    @Override
    public boolean isSolid() {
        return false;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(BlockID.AIR));
    }
}
