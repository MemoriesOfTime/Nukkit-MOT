package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.utils.BlockColor;

public class BlockLeavesPaleOak extends BlockLeaves {

    public static final int UPDATE_BIT = 0b1;
    public static final int PERSISTENT_BIT = 0b10;

    public BlockLeavesPaleOak() {
        this(0);
    }

    public BlockLeavesPaleOak(int meta) {
        super(meta);
    }

    @Override
    public int getId() {
        return PALE_OAK_LEAVES;
    }

    @Override
    public String getName() {
        return "Pale Oak Leaves";
    }

    @Override
    protected Item getSapling() {
        return new ItemBlock(Block.get(PALE_OAK_SAPLING));
    }

    @Override
    protected boolean canDropApple() {
        return false;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.GREEN_TERRACOTA_BLOCK_COLOR;
    }
}
