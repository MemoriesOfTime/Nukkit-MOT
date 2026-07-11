package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodWeathered extends BlockLightningRodBase {

    public BlockLightningRodWeathered() {
        this(0);
    }

    public BlockLightningRodWeathered(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Weathered Lightning Rod";
    }

    @Override
    public int getId() {
        return WEATHERED_LIGHTNING_ROD;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}
