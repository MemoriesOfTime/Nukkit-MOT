package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.utils.BlockColor;

public class BlockDoorBamboo extends BlockDoorWood {

    public BlockDoorBamboo() {
        this(0);
    }

    public BlockDoorBamboo(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Bamboo Door Block";
    }

    @Override
    public int getId() {
        return BAMBOO_DOOR;
    }

    @Override
    public Item toItem() {
        return Item.fromString(ItemNamespaceId.BAMBOO_DOOR_NAMESPACE_ID);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BAMBOO_BLOCK_COLOR;
    }
}
