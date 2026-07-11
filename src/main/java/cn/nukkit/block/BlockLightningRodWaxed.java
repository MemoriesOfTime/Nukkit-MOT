package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRodWaxed extends BlockLightningRodBase {

    public BlockLightningRodWaxed() {
        this(0);
    }

    public BlockLightningRodWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Lightning Rod";
    }

    @Override
    public int getId() {
        return WAXED_LIGHTNING_ROD;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
