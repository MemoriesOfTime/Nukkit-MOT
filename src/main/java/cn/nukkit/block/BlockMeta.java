package cn.nukkit.block;

import cn.nukkit.block.blockproperty.BlockProperties;
import org.jetbrains.annotations.NotNull;

public abstract class BlockMeta extends Block {

    private int meta;

    protected BlockMeta(int meta) {
        this.meta = meta;
    }

    @Override
    public int getFullId() {
        return (getId() << DATA_BITS) + getDamage();
    }

    @Override
    public final int getDamage() {
        if (!this.isDefaultState()) {
            return this.getMutableState().getDataStorage().intValue();
        }
        return this.meta;
    }

    @Override
    public void setDamage(int meta) {
        this.meta = meta;
    }

    @NotNull
    @Override
    public abstract BlockProperties getProperties();
}
