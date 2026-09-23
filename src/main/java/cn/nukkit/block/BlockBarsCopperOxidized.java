package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockBarsCopperOxidized extends BlockBarsCopperBase {

    public BlockBarsCopperOxidized() {
    }

    public BlockBarsCopperOxidized(int meta) {
        super(meta);
    }


    @Override
    public String getName() {
        return "Oxidized Copper Bars";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_BARS;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
