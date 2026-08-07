package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockChainCopperExposedWaxed extends BlockChainCopper {

    public BlockChainCopperExposedWaxed() {
        this(0);
    }

    public BlockChainCopperExposedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Copper Chain";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_COPPER_CHAIN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
