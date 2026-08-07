package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodExposed extends BlockLightningRodBase {

    public BlockLightningRodExposed() {
        this(0);
    }

    public BlockLightningRodExposed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Exposed Lightning Rod";
    }

    @Override
    public int getId() {
        return EXPOSED_LIGHTNING_ROD;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.EXPOSED;
    }
}
