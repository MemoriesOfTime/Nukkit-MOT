package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.inventory.CartographyTableInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemTool;
import cn.nukkit.utils.BlockColor;

public class BlockCartographyTable extends BlockSolid {

    @Override
    public String getName() {
        return "Cartography Table";
    }

    @Override
    public int getId() {
        return CARTOGRAPHY_TABLE;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_AXE;
    }

    @Override
    public double getResistance() {
        return 12.5;
    }

    @Override
    public double getHardness() {
        return 2.5;
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.WOOD_BLOCK_COLOR;
    }

    @Override
    public int getBurnChance() {
        return 5;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (player != null) {
            player.addWindow(new CartographyTableInventory(player.getUIInventory(), this), Player.CARTOGRAPHY_WINDOW_ID);
        }
        return true;
    }

    @Override
    public Item toItem() {
        return new ItemBlock(Block.get(this.getId(), 0), 0);
    }
}
