package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.utils.BlockColor;

public class BlockStairsCopperCutOxidized extends BlockStairsCopperCut {

    public BlockStairsCopperCutOxidized() {
        this(0);
    }

    public BlockStairsCopperCutOxidized(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return OXIDIZED_CUT_COPPER_STAIRS;
    }

    @Override
    public String getName() {
        return "Oxidized Cut Copper Stairs";
    }

    @Override
    public int onUpdate(int type) {
        // 已完全氧化，但非 RANDOM 更新仍需走父类维护 corner 位
        // Fully oxidized, but non-RANDOM updates must still reach the parent to maintain corner bits
        return type == Level.BLOCK_UPDATE_RANDOM ? 0 : super.onUpdate(type);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WARPED_NYLIUM_BLOCK_COLOR;
    }

    @Override
    public int getCopperAge() {
        return 3;
    }

    @Override
    public int getWaxedBlockId() {
        return WAXED_OXIDIZED_CUT_COPPER_STAIRS;
    }

    @Override
    public int getIncrementAgeBlockId() {
        throw new UnsupportedOperationException();
    }

    @Override
    public int getDecrementAgeBlockId() {
        return WEATHERED_CUT_COPPER_STAIRS;
    }
}