package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public abstract class BlockLeavesPoplar extends BlockLeaves {

    /**
     * 调色板状态位为 persistent &lt;&lt; 1 | update（同 pale oak / cherry），不是基类的 0x04/0x08。
     */
    public static final int UPDATE_BIT = 0b1;
    public static final int PERSISTENT_BIT = 0b10;

    protected BlockLeavesPoplar(int meta) {
        super(meta);
    }

    @Override
    public boolean isPersistent() {
        return (this.getDamage() & PERSISTENT_BIT) != 0;
    }

    @Override
    public void setPersistent(boolean persistent) {
        this.setDamage(persistent ? this.getDamage() | PERSISTENT_BIT | UPDATE_BIT
                : this.getDamage() & ~(PERSISTENT_BIT | UPDATE_BIT));
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
    protected Item getSapling() {
        return new ItemBlock(Block.get(POPLAR_SAPLING));
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
