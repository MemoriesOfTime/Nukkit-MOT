package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemSnowball;
import cn.nukkit.item.ItemTool;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.utils.BlockColor;

public class BlockSnow extends BlockSolid {

    @Override
    public String getName() {
        return "Snow";
    }

    @Override
    public int getId() {
        return SNOW_BLOCK;
    }

    @Override
    public double getHardness() {
        return 0.6;
    }

    @Override
    public int getToolType() {
        return ItemTool.TYPE_SHOVEL;
    }

    @Override
    public Item[] getDrops(Item item) {
        if (item.isShovel() && item.getTier() >= ItemTool.TIER_WOODEN) {
            if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
                return new Item[]{this.toItem()};
            }
            return new Item[]{
                    new ItemSnowball(0, 4)
            };
        } else {
            return Item.EMPTY_ARRAY;
        }
    }

    @Override
    public BlockColor getColor() {
        return BlockColor.SNOW_BLOCK_COLOR;
    }


    @Override
    public boolean canHarvestWithHand() {
        return false;
    }
    
    @Override
    public boolean canSilkTouch() {
        return true;
    }

    @Override
    public boolean canBeActivated() {
        return true;
    }

    @Override
    public boolean onActivate(Item item, Player player) {
        if (item.isShovel()) {
            Item[] drops = this.getDrops(item.clone().clearNamedTag());
            if (this.level.useBreakOnFromBlockInteraction(this, item.clone(), player, drops) == null) {
                return false;
            }
            item.useOn(this);
            return true;
        }
        return false;
    }
}
