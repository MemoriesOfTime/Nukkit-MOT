package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockChainCopperOxidizedWaxed extends BlockChainCopper {

    public BlockChainCopperOxidizedWaxed() {
        this(0);
    }

    public BlockChainCopperOxidizedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Oxidized Copper Chain";
    }

    @Override
    public int getId() {
        return WAXED_OXIDIZED_COPPER_CHAIN;
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
