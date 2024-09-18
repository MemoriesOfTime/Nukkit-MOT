package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.utils.BlockColor;

public class BlockStructureVoid extends BlockTransparentMeta {

    public static final int TYPE_VOID = 0;
    public static final int TYPE_AIR = 1;

    public BlockStructureVoid() {
        this(0);
    }

    public BlockStructureVoid(int meta) {
        super(meta & 0x1);
    }

    @Override
    public int getId() {
        return STRUCTURE_VOID;
    }

    @Override
    public String getName() {
        return "Structure Void";
    }

    @Override
    public double getHardness() {
        return -1;
    }

    @Override
    public double getResistance() {
        return 18000000;
    }

    @Override
    public boolean isBreakable(Item item) {
        return false;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.AIR_BLOCK_COLOR;
    }

    @Override
    protected AxisAlignedBB recalculateBoundingBox() {
        return null;
    }

    @Override
    public boolean canPassThrough() {
        return true;
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
    public boolean canHarvestWithHand() {
        return false;
    }
}
