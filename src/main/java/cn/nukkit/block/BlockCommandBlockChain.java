package cn.nukkit.block;

public class BlockCommandBlockChain extends BlockCommandBlock {

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
