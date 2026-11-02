package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockAzaleaLeavesFlowered extends BlockAzaleaLeaves {

    public BlockAzaleaLeavesFlowered() {
        this(0);
    }

    public BlockAzaleaLeavesFlowered(int meta) {
        super(meta);
    }


    @Override
    public int getId() {
        return AZALEA_LEAVES_FLOWERED;
    }

    @Override
    public String getName() {
        return "Azalea Leaves Flowered";
    }

    @Override
    protected Item getSapling() {
        return Block.get(FLOWERING_AZALEA).toItem();
    }
}
