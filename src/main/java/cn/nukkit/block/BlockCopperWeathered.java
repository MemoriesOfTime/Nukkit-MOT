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
public class BlockCopperWeathered extends BlockCopperBase {
    public BlockCopperWeathered() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Weathered Copper";
    }
    @Override
    public int getId() {
        return WEATHERED_COPPER;
    }

    @Override
    public @NotNull OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}