package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodExposedWaxed extends BlockLightningRodBase {

    public BlockLightningRodExposedWaxed() {
        this(0);
    }

    public BlockLightningRodExposedWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Exposed Lightning Rod";
    }

    @Override
    public int getId() {
        return WAXED_EXPOSED_LIGHTNING_ROD;
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
