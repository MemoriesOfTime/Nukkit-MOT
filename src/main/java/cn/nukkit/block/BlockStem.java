package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.math.BlockFace;
import org.jetbrains.annotations.NotNull;

public abstract class BlockStem extends BlockSolidMeta {

    private static final short[] faces = new short[]{
            0,
            0,
            2,
            2,
            1,
            1
    };

    public BlockStem() {
        this(0);
    }

    protected BlockStem(int meta) {
        super(meta);
    }

    @Override
    public boolean place(@NotNull Item item, @NotNull Block block, @NotNull Block target, @NotNull BlockFace face, double fx, double fy, double fz, Player player) {
        this.setDamage(faces[face.getIndex()]);
        this.getLevel().setBlock(block, this, true, true);
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isAxe()) {
            Block strippedBlock = Block.get(this.getStrippedId());
            strippedBlock.setDamage(this.getDamage());
            item.useOn(this);
            this.level.setBlock(this, strippedBlock, true, true);
            return true;
        }
        return false;
    }

    public abstract int getStrippedId();

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(this, 0);
    }

    @Override
    public double getHardness() {
        return 2;
    }

    @Override
    public double getResistance() {
        return 2;
    }
}
