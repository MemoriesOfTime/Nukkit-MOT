package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockFrogSpawn extends BlockFlowable {
    public BlockFrogSpawn() {
        this(0);
    }

    public BlockFrogSpawn(int meta) {
        super(0);
    }

    @Override
    public int getId() {
        return FROG_SPAWN;
    }

    @Override
    public String getName() {
        return "Frogspawn";
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[0];
    }

    @Override
    public int getDropExp() {
        return 1;
    }

    /*@Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        if (!(block instanceof BlockWater) || !(block.up() instanceof BlockAir)) {
            return false;
        }
        System.out.println(block.getClass().getName() + " " + block.up().getClass().getName());
        return this.getLevel().setBlock(this, this, true, true);
    }*/
}
