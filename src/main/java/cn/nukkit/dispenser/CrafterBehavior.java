package cn.nukkit.dispenser;

import cn.nukkit.block.BlockDispenser;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import it.unimi.dsi.fastutil.objects.ObjectBooleanPair;

public class CrafterBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        return drop(block, face, item).left();
    }

    @Override
    public ObjectBooleanPair<Item> drop(BlockDispenser block, BlockFace face, Item item) {
        Boolean interactionResult = DropperBehavior.pushItem(block, face, item);
        if (interactionResult != null && interactionResult) {
            return ObjectBooleanPair.of(null, false);
        }

        return ObjectBooleanPair.of(super.dispense(block, face, item), true);
    }
}