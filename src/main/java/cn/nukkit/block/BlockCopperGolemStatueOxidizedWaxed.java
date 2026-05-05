package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockCopperGolemStatueOxidizedWaxed extends BlockCopperGolemStatueWaxed {

    public BlockCopperGolemStatueOxidizedWaxed() {
        this(0);
    }

    public BlockCopperGolemStatueOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_GOLEM_STATUE;
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Golem Statue";
    }

    @Override
    public @NotNull BlockColor getColor() {
        return BlockColor.WARPED_NYLIUM_BLOCK_COLOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
