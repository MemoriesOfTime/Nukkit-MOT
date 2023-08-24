package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTool;

public class BlockTrapdoorIron extends BlockTrapdoor {
    public BlockTrapdoorIron() {
        super(0);
    }

    public BlockTrapdoorIron(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return IRON_TRAPDOOR;
    }

    @Override
    public String getName() {
        return "Iron Trapdoor";
    }

    @Override
    public double getHardness() {
        return 5;
    }

    @Override
    public double getResistance() {
        return 25;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_WOODEN;
    }

    @Override
    public boolean onActivate(final Item item, final Player player) {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
}
