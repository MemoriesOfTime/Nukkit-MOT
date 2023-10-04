package cn.nukkit.block;

import cn.nukkit.block.blockproperty.BlockProperties;
import org.jetbrains.annotations.NotNull;

public abstract class BlockMeta extends Block {

    private int meta;

    protected BlockMeta(int meta) {
        this.meta = meta;
        if (meta != 0) {
            getMutableState().setDataStorageFromInt(meta, true);
        }
    }

    @Override
    public int getFullId() {
        return (getId() << DATA_BITS) + meta;
    }

    @Override
    public final int getDamage() {
        return this.meta;
    }

    @Override
    public void setDamage(int meta) {
        this.meta = meta;
        if (meta == 0 && isDefaultState()) {
            return;
        }
        getMutableState().setDataStorageFromInt(meta);
    }

    @NotNull
    @Override
    public abstract BlockProperties getProperties();
}
