package cn.nukkit.level.generator.object.end;

import cn.nukkit.block.Block;
import cn.nukkit.level.ChunkManager;
import cn.nukkit.level.generator.object.BasicGenerator;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.NukkitRandom;
import cn.nukkit.math.Vector3;

/**
 * @author GoodLucky777
 */
public class ObjectEndIsland extends BasicGenerator {

    public boolean generate(ChunkManager level, NukkitRandom rand, Vector3 position) {
        float n = (float) (rand.nextBoundedInt(3) + 4);
        for (int y = 0; n > 0.5f; y--) {
            for (int x = NukkitMath.floorFloat(-n); x <= NukkitMath.ceilFloat(n); x++) {
                for (int z = NukkitMath.floorFloat(-n); z <= NukkitMath.ceilFloat(n); z++) {
                    if ((float) (x * x + z * z) <= (n + 1f) * (n + 1f)) {
                        level.setBlockAt(position.getFloorX() + x, position.getFloorY() + y, position.getFloorZ() + z, Block.END_STONE);
                    }
                }
            }
            n -= (float) ((double) rand.nextBoundedInt(2) + 0.5);
        }
        return true;
    }
}
