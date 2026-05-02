package cn.nukkit.block;

/**
 * Adapted from Lumi (<a href="https://github.com/KoshakMineDEV/Lumi">Lumi</a>)
 * and PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockCopperLanternWaxed extends BlockCopperLantern {

    public BlockCopperLanternWaxed() {
        this(0);
    }

    public BlockCopperLanternWaxed(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Waxed Copper Lantern";
    }

    @Override
    public int getId() {
        return WAXED_COPPER_LANTERN;
    }

    @Override
    public boolean isWaxed() {
        return true;
    }
}
