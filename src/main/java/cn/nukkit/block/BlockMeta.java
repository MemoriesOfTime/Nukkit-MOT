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
        if (useBlockProperty()) {
            return isDefaultState() ? 0 : this.getMutableState().getDataStorage().intValue();
        }
        return this.getOriginalDamage();
    }

    @Override
    public int getOriginalDamage() {
        return this.meta;
    }

    @Override
    public void setDamage(int meta) {
        if (useBlockProperty()) {
            if (meta == 0 && isDefaultState()) {
                return;
            }
            getMutableState().setDataStorageFromInt(meta);
        } else {
            this.setOriginalDamage(meta);
        }
    }

    @Override
    public void setOriginalDamage(int meta) {
        this.meta = meta;
    }

    @NotNull
    @Override
    public abstract BlockProperties getProperties();
}
