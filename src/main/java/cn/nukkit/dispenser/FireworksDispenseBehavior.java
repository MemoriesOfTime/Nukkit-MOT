package cn.nukkit.dispenser;

import cn.nukkit.block.BlockDispenser;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityFirework;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;

public class FireworksDispenseBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Vector3 pos = block.getDispensePosition();
        Vector3 motion = new Vector3(face.getXOffset(), face.getYOffset(), face.getZOffset());

        CompoundTag nbt = Entity.getDefaultNBT(pos, motion,
                        (float) motion.yRotFromDirection(),
                        (float) motion.xRotFromDirection())
                .putCompound("FireworkItem", NBTIO.putItemHelper(item));

        EntityFirework firework = new EntityFirework(
                block.level.getChunk(pos.getChunkX(), pos.getChunkZ()), nbt, true, null);
        firework.spawnToAll();
        return null;
    }
}
