package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemNamespaceId;
import cn.nukkit.utils.BlockColor;

public class BlockDoorPoplar extends BlockDoorWood {

    public BlockDoorPoplar() {
        this(0);
    }

    public BlockDoorPoplar(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Poplar Door Block";
    }

    @Override
    public int getId() {
        return POPLAR_DOOR;
    }

    @Override
    public Item toItem() {
        return Item.fromString(ItemNamespaceId.POPLAR_DOOR_NAMESPACE_ID);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SPRUCE_BLOCK_COLOR;
    }
}
