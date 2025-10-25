package cn.nukkit.inventory.special;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.inventory.MultiRecipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.ItemPotterySherd;
import cn.nukkit.network.protocol.ProtocolInfo;

import java.util.List;
import java.util.UUID;

public class DecoratedPotRecipe extends MultiRecipe {

    public DecoratedPotRecipe(){
        super(UUID.fromString(TYPE_DECORATED_POT_RECIPE));
    }

    @Override
    public boolean canExecute(Player player, Item outputItem, List<Item> inputs) {
        if (outputItem.getBlockId() != Block.DECORATED_POT) {
            return false;
        }
        int brickCount = 0;
        int sherdCount = 0;
        for (Item input : inputs) {
            if (input.getId() == ItemID.BRICK) {
                brickCount += input.getCount();
            }
            if (input instanceof ItemPotterySherd) {
                sherdCount += input.getCount();
            }
        }
        if (sherdCount + brickCount < 4) {
            return false;
        }
        return true;
    }

    @Override
    public boolean isSupportedOn(int protocol) {
        return protocol >= ProtocolInfo.v1_20_0;
    }
}
