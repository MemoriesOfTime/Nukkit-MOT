package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.utils.BlockColor;

public class BlockDoorMangrove extends BlockDoorWood {

    public BlockDoorMangrove() {
        this(0);
    }

    public BlockDoorMangrove(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Mangrove Door Block";
    }

    @Override
    public int getId() {
        return MANGROVE_DOOR_BLOCK;
    }

    @Override
    public Item toItem() {
        return Item.fromString(ItemNamespaceId.MANGROVE_DOOR);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.MANGROVE_BLOCK_COLOR;
    }
}