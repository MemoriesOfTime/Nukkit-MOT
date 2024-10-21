package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperCutOxidizedWaxed extends BlockCopperWaxed {
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
    public boolean onActivate(@NotNull Item item, Player player) {
        if (item.isAxe()) {
            this.getLevel().setBlock(this, Block.get(BlockID.OXIDIZED_CUT_COPPER), true, true);
            return true;
        }
        return false;
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