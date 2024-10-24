package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperCutOxidizedWaxed extends BlockCopperCut {
    public BlockCopperCutOxidizedWaxed() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Cut Copper";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_CUT_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}