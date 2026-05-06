package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 * and PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLanternCopperOxidized extends BlockLanternCopper {

    public BlockLanternCopperOxidized() {
        this(0);
    }

    public BlockLanternCopperOxidized(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Copper Lantern";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER_LANTERN;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
