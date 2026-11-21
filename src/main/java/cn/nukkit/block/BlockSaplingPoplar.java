package cn.nukkit.block;

import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.object.tree.ObjectPoplarTree;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockColor;

public class BlockSaplingPoplar extends BlockSapling {

    private static final int[] LEAF_COLORS = {
            ORANGE_POPLAR_LEAVES,
            RED_POPLAR_LEAVES,
            YELLOW_POPLAR_LEAVES,
    };

    public BlockSaplingPoplar() {
        this(0);
    }

    public BlockSaplingPoplar(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return POPLAR_SAPLING;
    }

    @Override
    public String getName() {
        return "Poplar Sapling";
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (isSupportInvalid()) {
                this.getLevel().useBreakOn(this, null, null, true);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) {
            if (getLevel().getFullLight(add(0, 1, 0)) >= BlockCrops.MINIMUM_LIGHT_LEVEL) {
                if (isAged()) {
                    this.grow();
                } else {
                    setAged(true);
                    this.getLevel().setBlock(this, this, true);
                    return Level.BLOCK_UPDATE_RANDOM;
                }
            } else {
                return Level.BLOCK_UPDATE_RANDOM;
            }
        }
        return Level.BLOCK_UPDATE_NORMAL;
    }

    /** 每次生长随机挑一种叶色，与原版白杨多色树冠一致。 */
    @Override
    public boolean grow() {
        BlockGrowEvent event = new BlockGrowEvent(this, Block.get(POPLAR_LOG));
        event.call();
        if (event.isCancelled()) {
            return false;
        }

        NukkitRandom random = new NukkitRandom();
        int leaves = LEAF_COLORS[random.nextBoundedInt(LEAF_COLORS.length)];
        int meta = this.getDamage();
        this.level.setBlock(this, Block.get(BlockID.AIR), true, false);
        if (new ObjectPoplarTree(leaves).generate(this.getLevel(), random, this)) {
            return true;
        }
        this.level.setBlock(this, Block.get(POPLAR_SAPLING, meta & TYPE_BIT), true, false);
        return false;
    }

    @Override
    public boolean isSameType(Vector3 pos, int type) {
        return this.level.getBlock(pos).getId() == POPLAR_SAPLING;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(POPLAR_SAPLING));
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
