package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperChainExposed extends BlockCopperChain {

    public BlockCopperChainExposed() {
        this(0);
    }

    public BlockCopperChainExposed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Exposed Copper Chain";
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_CHAIN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
