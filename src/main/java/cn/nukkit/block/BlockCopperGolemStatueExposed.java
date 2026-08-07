package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.utils.BlockColor;
import org.jetbrains.annotations.NotNull;

public class BlockCopperGolemStatueExposed extends BlockCopperGolemStatue {

    public BlockCopperGolemStatueExposed() {
        this(0);
    }

    public BlockCopperGolemStatueExposed(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_GOLEM_STATUE;
    }

    @Override
    public String getName() {
        return "Exposed Copper Golem Statue";
    }

    @Override
    public @NotNull BlockColor getColor() {
        return BlockColor.LIGHT_GRAY_TERRACOTA_BLOCK_COLOR;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
