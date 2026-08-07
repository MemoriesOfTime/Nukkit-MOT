package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockChainCopperExposed extends BlockChainCopper {

    public BlockChainCopperExposed() {
        this(0);
    }

    public BlockChainCopperExposed(int meta) {
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
