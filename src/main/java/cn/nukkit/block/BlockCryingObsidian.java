package cn.nukkit.block;

import cn.nukkit.item.ItemTool;

public class BlockCryingObsidian extends BlockSolid {
    @Override
    public String getName() {
        return "Crying Obsidian";
    }

    @Override
    public int getId() {
        return CRYING_OBSIDIAN;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_PICKAXE;
    }

    @Override
    public double getHardness() {
        // Bedrock parity: crying obsidian has hardness 35 on Bedrock (50 in Java), the same as
        // obsidian. With 50 the server expected 9.4 s for a plain diamond pickaxe while every
        // client finished in 6.6 s, so each break was refused as too fast and the block
        // reappeared. Confirmed against PocketMine-MP VanillaBlocksInputs (35.0 /* 50 in Java */).
        return 35;
    }

    @Override
    public int getToolTier() {
        return ItemTool.TIER_DIAMOND;
    }

    @Override
    public double getResistance() {
        return 1200;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    public boolean canBePulled() {
        return false;
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    public int getLightLevel() {
        return 10;
    }
}
