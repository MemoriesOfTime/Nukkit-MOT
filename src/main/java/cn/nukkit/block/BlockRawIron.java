package cn.nukkit.block;

public class BlockRawIron extends BlockRaw {

    public BlockRawIron() {
        this(0);
    }

    public BlockRawIron(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Block of Raw Iron";
    }

    @Override
    public int getId() {
        return RAW_IRON_BLOCK;
    }

}