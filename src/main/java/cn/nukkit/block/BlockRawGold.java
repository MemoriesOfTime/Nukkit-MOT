package cn.nukkit.block;

public class BlockRawGold extends BlockRaw {

    public BlockRawGold() {
        this(0);
    }

    public BlockRawGold(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Block of Raw Gold";
    }

    @Override
    public int getId() {
        return RAW_GOLD_BLOCK;
    }

}