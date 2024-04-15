package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockSuspiciousSand extends BlockFallable {

    public int getId() {
        return Block.SUSPICIOUS_SAND;
    }

    public String getName() {
        return "Suspicious Sand";
    }

    @Override
    public double getHardness() {
        return 0.25;
    }

    @Override
    public double getResistance() {
        return 1.25;
    }

    @Override
    public Item[] getDrops(Item item) {
        return new Item[]{Item.AIR_ITEM};
    }
}