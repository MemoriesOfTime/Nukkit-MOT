package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.level.Level;
import cn.nukkit.level.generator.object.tree.ObjectCherryTree;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

public class BlockCherrySapling extends BlockSapling {

    public BlockCherrySapling() {
        this(0);
    }

    public BlockCherrySapling(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return CHERRY_SAPLING;
    }

    @Override
    public String getName() {
        return "Cherry Sapling";
    }

    @Override
    public int onUpdate(int type) {
        if (type == Level.BLOCK_UPDATE_NORMAL) {
            if (isSupportInvalid()) {
                this.getLevel().useBreakOn(this);
                return Level.BLOCK_UPDATE_NORMAL;
            }
        } else if (type == Level.BLOCK_UPDATE_RANDOM) { //Growth
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

    @Override
    public boolean grow() {
        new ObjectCherryTree().generate(this.getLevel(), new NukkitRandom(), this);
        Vector3 vector3 = new Vector3(this.x, this.y - 1, this.z);
        if (this.level.getBlock(vector3).getId() == BlockID.DIRT_WITH_ROOTS) {
            this.level.setBlock(vector3, Block.get(BlockID.DIRT));
        }
        return true;
    }

    @Override
    public boolean isSameType(Vector3 pos, int type) {
        Block block = this.level.getBlock(pos);
        return block.getId() == CHERRY_SAPLING && block.getDamage() == type;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(CHERRY_SAPLING));
    }
}
