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
public class BlockCopperCutOxidizedWaxed extends BlockCopperWaxed {
    public BlockCopperCutOxidizedWaxed() {
        // Does nothing
    }
    @Override
    public String getName() {
        return "Waxed Oxidized Cut Copper";
    }
    @Override
    public int getId() {
        return WAXED_OXIDIZED_CUT_COPPER;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isAxe()) {
            this.getLevel().setBlock(this, Block.get(BlockID.OXIDIZED_CUT_COPPER), true, true);
            return true;
        }
        return false;
    }

    @Override
    public OxidizationLevel getOxidizationLevel() {
        return OxidizationLevel.OXIDIZED;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}