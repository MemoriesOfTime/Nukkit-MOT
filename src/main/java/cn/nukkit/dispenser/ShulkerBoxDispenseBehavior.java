package cn.nukkit.dispenser;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.block.BlockID;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;

public class ShulkerBoxDispenseBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Block target = block.getSide(face);
        if (target.getId() != Block.AIR) {
            // Same answer as the undyed box: nothing dispensed, the item stays in the slot.
            return item;
        }
        Block shulkerBox = Block.get(BlockID.SHULKER_BOX, item.getDamage());
        shulkerBox.position(target);
        this.success = block.level.getCollidingEntities(shulkerBox.getBoundingBox()).length == 0;
        if (!this.success) {
            return item;
        }
        BlockFace shulkerBoxFace = target.down().getId() == BlockID.AIR ? face : BlockFace.UP;
        CompoundTag nbt = BlockEntity.getDefaultCompound(target, BlockEntity.SHULKER_BOX);
        nbt.putByte("facing", shulkerBoxFace.getIndex());
        if (item.hasCustomName()) {
            nbt.putString("CustomName", item.getCustomName());
        }
        CompoundTag tag = item.getNamedTag();
        if (tag != null) {
            if (tag.contains("Items")) {
                nbt.putList(tag.getList("Items"));
            }
        }
        // The block itself was never written before this fix: the block entity with the
        // contents was created on an air cell, the slot was emptied, and the next garbage
        // collection or block placement discarded the entity together with everything inside.
        block.level.setBlock(target, shulkerBox, true);
        BlockEntity.createBlockEntity(BlockEntity.SHULKER_BOX, block.level.getChunk(target.getChunkX(), target.getChunkZ()), nbt);
        block.level.updateComparatorOutputLevel(target);
        return null;
    }
}
