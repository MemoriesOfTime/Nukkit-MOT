package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public class BlockAzaleaLeaves extends BlockLeaves {

    public static final int UPDATE_BIT = 0b1;
    public static final int PERSISTENT_BIT = 0b10;

    public BlockAzaleaLeaves() {
        this(0);
    }

    public BlockAzaleaLeaves(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return AZALEA_LEAVES;
    }

    @Override
    public boolean isPersistent() {
        return (this.getDamage() & PERSISTENT_BIT) != 0;
    }

    @Override
    public void setPersistent(boolean persistent) {
        this.setDamage(persistent ? this.getDamage() | PERSISTENT_BIT : this.getDamage() & ~PERSISTENT_BIT);
    }

    @Override
    public boolean isCheckDecay() {
        return (this.getDamage() & UPDATE_BIT) != 0;
    }

    @Override
    public void setCheckDecay(boolean checkDecay) {
        this.setDamage(checkDecay ? this.getDamage() | UPDATE_BIT : this.getDamage() & ~UPDATE_BIT);
    }

    @Override
    public String getName() {
        return "Azalea Leaves";
    }

    @Override
    public boolean canHarvestWithHand() {
        return false;
    }

    @Override
    protected Item getSapling() {
        return Block.get(AZALEA).toItem();
    }

    @Override
    protected boolean canDropApple() {
        return false;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}
