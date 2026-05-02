package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperChain extends BlockCopperChainBase {

    public BlockCopperChain() {
        this(0);
    }

    public BlockCopperChain(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Chain";
    }

    @Override
    public int getId() {
        return COPPER_CHAIN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }
}
