package cn.nukkit.block;

public class BlockCommandBlockChain extends BlockCommandBlock {

    public BlockCommandBlockChain() {
        this(0);
    }

    public BlockCommandBlockChain(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHAIN_COMMAND_BLOCK;
    }

    @Override
    public String getName() {
        return "Chain Command Block";
    }

    @Override
    public String getIdentifier() {
        return "minecraft:chain_command_block";
    }
}
