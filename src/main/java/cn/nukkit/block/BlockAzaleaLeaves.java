package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockAzaleaLeaves extends BlockLeaves {

    @Override
    public int getId() {
        return AZALEA_LEAVES;
    }

    @Override
    public String getName() {
        return "Azalea Leaves";
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    protected Item getSapling() {
        return Block.get(AZALEA).toItem();
    }

    @Override
    protected boolean canDropApple() {
        return false;
    }
}
