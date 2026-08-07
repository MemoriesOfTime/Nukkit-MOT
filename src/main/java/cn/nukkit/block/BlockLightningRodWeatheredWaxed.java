package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodWeatheredWaxed extends BlockLightningRodBase {

    public BlockLightningRodWeatheredWaxed() {
        this(0);
    }

    public BlockLightningRodWeatheredWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Weathered Lightning Rod";
    }

    @Override
    public int getId() {
        return WAXED_WEATHERED_LIGHTNING_ROD;
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
