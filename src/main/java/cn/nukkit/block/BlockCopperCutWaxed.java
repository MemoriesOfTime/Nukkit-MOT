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
public class BlockCopperCutWaxed extends BlockCopperCut {
    public BlockCopperCutWaxed() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Waxed Cut Copper";
    }
    @Override
    public int getId() {
        return WAXED_CUT_COPPER;
    }

    @Override
    public OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.UNAFFECTED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}