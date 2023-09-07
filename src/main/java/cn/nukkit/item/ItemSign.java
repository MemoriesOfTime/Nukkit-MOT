package cn.nukkit.item;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockSignPost;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemSign extends Item {

    public ItemSign() {
        this(0, 1);
    }

    public ItemSign(Integer meta) {
        this(meta, 1);
    }

    public ItemSign(Integer meta, int count) {
        super(SIGN, 0, count, "Sign");
        this.block = Block.get(SIGN_POST);
    }

    protected ItemSign(int id, Integer meta, int count, String name, BlockSignPost block) {
        super(id, meta, count, name);
        this.block = block;
    }

    @Override
    public int getMaxStackSize() {
        return 16;
    }
}
