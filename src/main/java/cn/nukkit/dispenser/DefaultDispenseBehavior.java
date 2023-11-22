package cn.nukkit.dispenser;

import cn.nukkit.block.BlockDispenser;
import cn.nukkit.item.Item;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.BlockFace.Axis;
import cn.nukkit.math.Vector3;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author CreeperFace
 */
public class DefaultDispenseBehavior implements DispenseBehavior {

    public boolean success = true;

    @Override
    public Item dispense(BlockDispenser block, BlockFace face, Item item) {
        Vector3 dispensePos = block.getDispensePosition();

        if (face.getAxis() == Axis.Y) {
            dispensePos.y -= 0.125;
        } else {
            dispensePos.y -= 0.15625;
        }

        ThreadLocalRandom rand = ThreadLocalRandom.current();
        double offset = rand.nextDouble() * 0.1 + 0.2;
        Vector3 motion = new Vector3(face.getXOffset() * offset, 0.20000000298023224, face.getZOffset() * offset);

        motion.x += rand.nextGaussian() * 0.007499999832361937 * 6;
        motion.y += rand.nextGaussian() * 0.007499999832361937 * 6;
        motion.z += rand.nextGaussian() * 0.007499999832361937 * 6;

        Item i = item.clone();
        i.setCount(1);
        block.level.dropItem(dispensePos, i, motion);
        return null;
    }
}
