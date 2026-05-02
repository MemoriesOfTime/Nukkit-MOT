package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 * and PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperLanternExposed extends BlockCopperLantern {

    public BlockCopperLanternExposed() {
        this(0);
    }

    public BlockCopperLanternExposed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Exposed Copper Lantern";
    }

    @Override
    public int getId() {
        return EXPOSED_COPPER_LANTERN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
