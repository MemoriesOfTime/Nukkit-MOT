package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;

public class BlockCactusFlower extends BlockFlowable {
    public BlockCactusFlower() {
        this(0);
    }

    public BlockCactusFlower(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Cactus Flower";
    }

    @Override
    public int getId() {
        return CACTUS_FLOWER;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (down() instanceof BlockCactus || down() instanceof BlockFarmland || down().isSolid()) {
            this.getLevel().setBlock(block, this, true);
            return true;
        }
        return false;
    }
}
