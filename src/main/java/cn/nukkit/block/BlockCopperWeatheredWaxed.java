package cn.nukkit.block;
import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperWeatheredWaxed extends BlockCopperBase {
    public BlockCopperWeatheredWaxed() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Waxed Weathered Copper";
    }
    @Override
    public int getId() {
        return WAXED_WEATHERED_COPPER;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}