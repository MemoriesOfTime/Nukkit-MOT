package cn.nukkit.block;

public abstract class BlockMeta extends Block {

    private int meta;

    protected BlockMeta(int meta) {
        this.meta = meta;
    }

    @Override
    public int getFullId() {
        return (getId() << DATA_BITS) + meta;
    }

    @Override
    public final int getDamage() {
        return this.meta;
    }

    public int getDamage(int flagBit) {
        return this.getDamage() & flagBit;
    }

    @Override
    public void setDamage(int meta) {
        this.meta = meta;
    }

    public void setDamage(int flagBit, int data) {
        this.setDamage(this.getDamage() & ~flagBit | data << getFlagBitLength(flagBit) & flagBit);
    }

    private int getFlagBitLength(int flagBit) {
        int count = 0;
        while(flagBit != 1) {
            if (((flagBit & 1) == 1)) {
                break;
            }
            count++;
            flagBit >>>= 1;
        }
        return count;
    }
}
