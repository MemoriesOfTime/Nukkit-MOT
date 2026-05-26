package cn.nukkit.dispenser;

import cn.nukkit.block.BlockDispenser;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import it.unimi.dsi.fastutil.objects.ObjectBooleanPair;

/**
 * @author CreeperFace
 */
public interface DispenseBehavior {

    Item dispense(BlockDispenser block, BlockFace face, Item item);

    default ObjectBooleanPair<Item> drop(BlockDispenser block, BlockFace face, Item item) {
        return ObjectBooleanPair.of(dispense(block, face, item), true);
    }
}
