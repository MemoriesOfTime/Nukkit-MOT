package cn.nukkit.block;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockTorchCopper extends BlockTorch {

    public BlockTorchCopper() {
        this(0);
    }

    public BlockTorchCopper(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Copper Torch";
    }

    @Override
    public int getId() {
        return COPPER_TORCH;
    }
}
