package cn.nukkit.dispenser;

import cn.nukkit.block.Block;
import cn.nukkit.block.BlockDispenser;
import cn.nukkit.block.BlockID;
import cn.nukkit.block.BlockWater;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.item.EntityBoat;
import cn.nukkit.item.Item;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;

public class BoatDispenseBehavior extends DefaultDispenseBehavior {

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Vector3 pos = block.getSide(face).multiply(1.125);

        Block target = block.getSide(face);

        if (target instanceof BlockWater) {
            pos.y += 1;
        } else if (target.getId() != BlockID.AIR || !(target.down() instanceof BlockWater)) {
            return super.dispense(block, face, item);
        }

        pos = target.getLocation().setYaw(face.getHorizontalAngle());

        this.spawnBoatEntity(block.level, pos, item);

        return null;
    }

    protected void spawnBoatEntity(Level level, Vector3 pos, Item item) {
        EntityBoat boat = new EntityBoat(level.getChunk(pos.getChunkX(), pos.getChunkZ()),
                Entity.getDefaultNBT(pos)
                        .putInt("woodID", item.getDamage())
                        .putInt("Variant", item.getDamage())
        );
        boat.spawnToAll();
    }
}
