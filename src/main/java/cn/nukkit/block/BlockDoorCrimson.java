package cn.nukkit.block;

import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Sound;
import cn.nukkit.utils.BlockColor;

public class BlockDoorCrimson extends BlockDoorWood {

    public BlockDoorCrimson() {
        this(0);
    }

    public BlockDoorCrimson(int meta) {
        super(meta);
    }

    @Override
    public String getName() {
        return "Crimson Door Block";
    }

    @Override
    public int getId() {
        return CRIMSON_DOOR_BLOCK;
    }

    @Override
    public Item toItem() {
        return Item.get(ItemID.CRIMSON_DOOR);
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.NETHERRACK_BLOCK_COLOR;
    }

    @Override
    public int getBurnChance() {
        return 0;
    }

    @Override
    public int getBurnAbility() {
        return 0;
    }

    @Override
    public void playOpenSound() {
        this.level.addSound(this, Sound.OPEN_NETHER_WOOD_DOOR);
    }

    @Override
    public void playCloseSound() {
        this.level.addSound(this, Sound.CLOSE_NETHER_WOOD_DOOR);
    }
}
