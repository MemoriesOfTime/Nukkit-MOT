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
public class BlockCopperCutWeathered extends BlockCopperCut {
    public BlockCopperCutWeathered() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Weathered Cut Copper";
    }
    @Override
    public int getId() {
        return WEATHERED_CUT_COPPER;
    }

    @Override
    public OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.WEATHERED;
    }
}