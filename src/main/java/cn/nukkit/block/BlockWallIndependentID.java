package cn.nukkit.block;

import cn.nukkit.block.custom.properties.BlockProperties;
import cn.nukkit.block.properties.BlockPropertiesHelper;
import cn.nukkit.block.properties.VanillaProperties;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;

public abstract class BlockWallIndependentID extends BlockWall implements BlockPropertiesHelper {

    protected static final BlockProperties PROPERTIES = new BlockProperties(
            VanillaProperties.WALL_CONNECTION_TYPE_EAST,
            VanillaProperties.WALL_CONNECTION_TYPE_NORTH,
            VanillaProperties.WALL_CONNECTION_TYPE_SOUTH,
            VanillaProperties.WALL_CONNECTION_TYPE_WEST,
            VanillaProperties.WALL_POST
    );

    @Override
    public BlockProperties getBlockProperties() {
        return PROPERTIES;
    }

    public BlockWallIndependentID() {
        super();
    }

    public BlockWallIndependentID(int meta) {
        super(meta);
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId()), 0, 1);
    }
}
