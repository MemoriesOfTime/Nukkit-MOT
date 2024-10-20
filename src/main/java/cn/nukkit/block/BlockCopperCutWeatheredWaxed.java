package cn.nukkit.block;
import cn.nukkit.Player;
import cn.nukkit.block.properties.enums.OxidizationLevel;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
/**
 * @author LoboMetalurgico
 * @since 11/06/2021
 */
public class BlockCopperCutWeatheredWaxed extends BlockCopperCut {
    public BlockCopperCutWeatheredWaxed() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Waxed Weathered Cut Copper";
    }
    @Override
    public int getId() {
        return WAXED_WEATHERED_CUT_COPPER;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }

    @Override
    public OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}