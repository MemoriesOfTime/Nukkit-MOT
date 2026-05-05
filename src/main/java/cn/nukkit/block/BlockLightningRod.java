package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockLightningRod extends BlockLightningRodBase {

    public BlockLightningRod() {
        this(0);
    }

    public BlockLightningRod(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Lightning Rod";
    }

    @Override
    public int getId() {
        return LIGHTNING_ROD;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }
}
