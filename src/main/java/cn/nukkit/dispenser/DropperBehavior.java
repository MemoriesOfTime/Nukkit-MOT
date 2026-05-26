package cn.nukkit.dispenser;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.inventory.Inventory;
import cn.nukkit.inventory.InventoryHolder;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import it.unimi.dsi.fastutil.objects.ObjectBooleanPair;

public class DropperBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        return drop(block, face, item).left();
    }

    @Override
    public ObjectBooleanPair<Item> drop(BlockDispenser block, BlockFace face, Item item) {
        Boolean interactionResult = pushItem(block, face, item);
        if (interactionResult != null) {
            if (!interactionResult) {
                return ObjectBooleanPair.of(Item.get(Item.AIR), false);
            }
            return ObjectBooleanPair.of(null, false);
        }

        return ObjectBooleanPair.of(super.dispense(block, face, item), true);
    }

    static Boolean pushItem(BlockDispenser block, BlockFace facing, Item item) {
        Block side = block.level.getBlock(block.getSideVec(facing));
        BlockEntity blockEntity = block.level.getBlockEntity(side);
        if (!(blockEntity instanceof InventoryHolder)) {
            return null;
        }

        Inventory inventory = ((InventoryHolder) blockEntity).getInventory();
        Item itemToAdd = item.clone();
        itemToAdd.setCount(1);
        if (!inventory.canAddItem(itemToAdd)) {
            return false;
        }

        if (inventory.addItem(itemToAdd).length != 0) {
            return false;
        }

        item.setCount(item.getCount() - 1);
        return true;
    }
}