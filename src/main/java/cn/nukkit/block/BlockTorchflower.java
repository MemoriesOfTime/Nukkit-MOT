package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

/**
 * Torchflower decorative plant.
 * <p>
 * Adapted from PowerNukkitX (<a href="https://github.com/PowerNukkitX/PowerNukkitX">PowerNukkitX</a>).
 */
public class BlockTorchflower extends BlockFlower {

    public BlockTorchflower() {
        this(0);
    }

    public BlockTorchflower(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return TORCHFLOWER;
    }

    public String getIdentifier() {
        return "minecraft:torchflower";
    }

    @Override
    public String getName() {
        return "Torchflower";
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(TORCHFLOWER), 0, 1);
    }
}
