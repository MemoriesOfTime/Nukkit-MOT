package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 * and PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperLantern extends BlockCopperLanternBase {

    public BlockCopperLantern() {
        this(0);
    }

    public BlockCopperLantern(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Lantern";
    }

    @Override
    public int getId() {
        return COPPER_LANTERN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }
}
