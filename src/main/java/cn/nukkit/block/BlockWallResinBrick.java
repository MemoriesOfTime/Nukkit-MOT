package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.utils.BlockColor;

public class BlockWallResinBrick extends BlockWallIndependentID {

    public BlockWallResinBrick() {
        this(0);
    }

    public BlockWallResinBrick(int meta) {
        super(meta);
    }

    @Override
    public String getIdentifier() {
        return "minecraft:resin_brick_wall";
    }

    @Override
    public String getName() {
        return "Resin Brick Wall";
    }

    @Override
    public int getId() {
        return RESIN_BRICK_WALL;
    }

    @Override
    public double getHardness() {
        return 1.5;
    }

    @Override
    public double getResistance() {
        return 6;
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
        return BlockColor.ORANGE_TERRACOTA_BLOCK_COLOR;
    }
}
