package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockChainCopperWeatheredWaxed extends BlockChainCopper {

    public BlockChainCopperWeatheredWaxed() {
        this(0);
    }

    public BlockChainCopperWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Copper Chain";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER_CHAIN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
