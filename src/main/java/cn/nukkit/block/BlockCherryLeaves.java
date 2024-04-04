package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockCherryLeaves extends BlockLeaves {

    public static final int UPDATE_BIT = 0b1;
    public static final int PERSISTENT_BIT = 0b10;

    @Override
    public String getName() {
        return "Cherry Leaves";
    }

    @Override
    public int getId() {
        return CHERRY_LEAVES;
    }

    @Override
    protected Item getSapling() {
        return new ItemBlock(Block.get(CHERRY_SAPLING));
    }

    @Override
    protected boolean canDropApple() {
        return false;
    }
}
