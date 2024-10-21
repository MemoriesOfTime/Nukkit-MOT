package cn.nukkit.block;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperOxidized extends BlockCopperBase {
    public BlockCopperOxidized() {
        // Does nothing
    }

    @Override
    public String getName() {
        return "Oxidized Copper";
    }

    @Override
    public int getId() {
        return OXIDIZED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }
}