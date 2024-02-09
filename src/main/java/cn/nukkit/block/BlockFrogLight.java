package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;

public abstract class BlockFrogLight extends BlockSolidMeta {

    private static final short[] faces = new short[]{
            0,
            0,
            2,
            2,
            1,
            1
    };

    public BlockFrogLight() {
        this(0);
    }

    protected BlockFrogLight(int meta) {
        super(meta);
    }

    @Override
    public int getLightLevel() {
        return 15;
    }

    @Override
    public double getResistance() {
        return 0.3;
    }

    @Override
    public double getHardness() {
        return 0.3;
    }

    @Override
    public int getToolTier() {
        return 0;
    }

    @Override
    public boolean canHarvestWithHand() {
        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(faces[face.getIndex()]);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }
}