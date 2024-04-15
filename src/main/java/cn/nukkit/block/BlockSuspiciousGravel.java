package cn.nukkit.block;

import cn.nukkit.item.Item;

public class BlockSuspiciousGravel extends BlockFallableMeta {

    public BlockSuspiciousGravel() {
        this(0);
    }

    protected BlockSuspiciousGravel(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return Block.SUSPICIOUS_GRAVEL;
    }

    @Override
    public String getName() {
        return "Suspicious Gravel";
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