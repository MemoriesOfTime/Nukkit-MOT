package cn.nukkit.block;

import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author explorer_3039
 * @since 2024/2/23
 */
public class BlockCopper extends BlockCopperBase {
    public BlockCopper() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Block of Copper";
    }
    @Override
    public int getId() {
        return COPPER_BLOCK;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }
}