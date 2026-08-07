package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.math.BlockFace;
import cn.nukkit.utils.Faceable;

/**
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockDriedGhast extends BlockTransparentMeta implements Faceable {

    private static final int CARDINAL_DIRECTION_MASK = 0b0011;
    private static final int REHYDRATION_LEVEL_MASK = 0b1100;

    public BlockDriedGhast() {
        this(0);
    }

    public BlockDriedGhast(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Dried Ghast";
    }

    @Override
    public int getId() {
        return DRIED_GHAST;
    }

    @Override
    public double getHardness() {
        return 0;
    }

    @Override
    public double getResistance() {
        return 0;
    }

    @Override
    public boolean place(Item item, Block block, Block target, BlockFace face, double fx, double fy, double fz, Player player) {
        this.setBlockFace(player == null ? BlockFace.SOUTH : player.getHorizontalFacing().getOpposite());
        this.setRehydrationLevel(0);
        return this.getLevel().setBlock(this, this, true);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }

    public void setRehydrationLevel(int level) {
        this.setProperty(REHYDRATION_LEVEL_MASK, level);
    }

    public int getRehydrationLevel() {
        return this.getProperty(REHYDRATION_LEVEL_MASK);
    }

    @Override
    public void setBlockFace(BlockFace face) {
        this.setProperty(CARDINAL_DIRECTION_MASK, face.getHorizontalIndex());
    }

    @Override
    public BlockFace getBlockFace() {
        return BlockFace.fromHorizontalIndex(this.getProperty(CARDINAL_DIRECTION_MASK));
    }

    private void setProperty(int mask, int value) {
        int shift = Integer.numberOfTrailingZeros(mask);
        int maxValue = mask >>> shift;
        int clampedValue = value & maxValue;
        this.setDamage((this.getDamage() & ~mask) | (clampedValue << shift));
    }

    private int getProperty(int mask) {
        int shift = Integer.numberOfTrailingZeros(mask);
        return (this.getDamage() & mask) >>> shift;
    }
}
