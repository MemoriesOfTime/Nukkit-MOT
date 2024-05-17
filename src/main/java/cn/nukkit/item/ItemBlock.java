package cn.nukkit.item;

import cn.nukkit.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemBlock extends Item {

    public ItemBlock(Block block) {
        this(block, 0, 1);
    }

    public ItemBlock(Block block, Integer meta) {
        this(block, meta, 1);
    }

    public ItemBlock(Block block, Integer meta, int count) {
        super(block.getItemId(), meta, count, block.getName());
        this.block = block;
    }

    @Override
    public void setDamage(Integer meta) {
        if (meta != null) {
            this.meta = meta & 0xffff;
        } else {
            this.hasMeta = false;
        }
        this.block.setDamage(meta);
    }

    @Override
    public ItemBlock clone() {
        ItemBlock block = (ItemBlock) super.clone();
        block.block = this.block.clone();
        return block;
    }

    @NotNull
    @Override
    public Block getBlock() {
        return this.block.clone();
    }

    @Override
    public int getMaxStackSize() {
        if (this.block.getId() == Block.SHULKER_BOX || this.block.getId() == Block.UNDYED_SHULKER_BOX) {
            return 1;
        }

        return super.getMaxStackSize();
    }
}
