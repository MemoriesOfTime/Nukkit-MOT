package cn.nukkit.block;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperTorch extends BlockTorch {

    public BlockCopperTorch() {
        this(0);
    }

    public BlockCopperTorch(int meta) {
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
