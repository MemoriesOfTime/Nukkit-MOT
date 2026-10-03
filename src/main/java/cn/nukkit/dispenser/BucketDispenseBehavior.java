package cn.nukkit.dispenser;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockLiquid;
import cn.nukkit.block.BlockPowderSnow;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBucket;
import cn.nukkit.item.ItemID;
import cn.nukkit.math.BlockFace;

/**
 * @author CreeperFace
 */
public class BucketDispenseBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Block target = block.getSide(face);

        if (item.getDamage() > 0) {
            if (target.canBeFlowedInto() && isPlaceableContent(item.getDamage())) {
                // getDamageByTarget() translates a BLOCK id into a bucket damage, so feeding it a
                // bucket damage answers with whatever bucket happens to carry that number: a powder
                // snow bucket (11) came back as a lava bucket (10) and the dispenser poured lava.
                Block replace = Block.get(ItemBucket.getBlockByDamage(item.getDamage()));

                if (replace instanceof BlockLiquid || replace instanceof BlockPowderSnow) {
                    block.level.setBlock(target, replace);
                    return Item.get(ItemID.BUCKET);
                }
            }
        } else if (target instanceof BlockLiquid && target.getDamage() == 0) {
            target.level.setBlock(target, Block.get(BlockID.AIR));
            return new ItemBucket(ItemBucket.getDamageByTarget(target.getId()));
        }

        return super.dispense(block, face, item);
    }

    private static boolean isPlaceableContent(int damage) {
        // A bucket that carries a creature (fish, axolotl, tadpole) also maps to water, and placing
        // that water here would swallow the creature. Such a bucket keeps falling through to the
        // default behavior, which throws the bucket itself out intact.
        return damage == ItemBucket.WATER_BUCKET
                || damage == ItemBucket.LAVA_BUCKET
                || damage == ItemBucket.POWDER_SNOW_BUCKET;
    }
}
