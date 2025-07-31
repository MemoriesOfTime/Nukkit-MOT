package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.utils.BlockColor;

public class BlockWallMudBrick extends BlockWallIndependentID {

    public BlockWallMudBrick() {
        this(0);
    }

    public BlockWallMudBrick(int meta) {
        super(meta);
    }

    @Override
    public String getIdentifier() {
        return "minecraft:mud_brick_wall";
    }

    @Override
    public String getName() {
        return "Mud Brick Wall";
    }

    @Override
    public int getId() {
        return MUD_BRICK_WALL;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 3;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isPickaxe()) {
            return new Item[]{
                    toItem()
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.BROWN_BLOCK_COLOR;
    }
}