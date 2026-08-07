package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockCopperGolemStatueWeathered extends BlockCopperGolemStatue {

    public BlockCopperGolemStatueWeathered() {
        this(0);
    }

    public BlockCopperGolemStatueWeathered(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return WEATHERED_COPPER_GOLEM_STATUE;
    }

    @Override
    public String getName() {
        return "Weathered Copper Golem Statue";
    }

    @Override
    public @NotNull BlockColor getColor() {
        return BlockColor.WARPED_STEM_BLOCK_COLOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
