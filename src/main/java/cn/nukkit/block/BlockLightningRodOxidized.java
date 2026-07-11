package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodOxidized extends BlockLightningRodBase {

    public BlockLightningRodOxidized() {
        this(0);
    }

    public BlockLightningRodOxidized(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Oxidized Lightning Rod";
    }

    @Override
    public int getId() {
        return OXIDIZED_LIGHTNING_ROD;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}
