package cn.nukkit.utils.spawners;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.passive.EntityParrot;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.utils.AbstractEntitySpawner;
import cn.nukkit.utils.SpawnerTask;
import cn.nukkit.utils.Utils;

public class ParrotSpawner extends AbstractEntitySpawner {

    public ParrotSpawner(SpawnerTask spawnTask) {
        super(spawnTask);
    }

    @Override
    public void spawn(Player player, Position pos, Level level) {
        if (pos.y < 70 || Utils.rand(1, 3) != 1) {
            return;
        }
        final int biomeId = level.getBiomeId((int) pos.x, (int) pos.z);
        if (biomeId == 21 || biomeId == 149 || biomeId == 23 || biomeId == 151 || biomeId == 48 || biomeId == 49) {
            final int blockId = level.getBlockIdAt((int) pos.x, (int) pos.y, (int) pos.z);
            if (blockId == Block.GRASS || blockId == Block.LEAVES || blockId == Block.WOOD) {
                if (level.isAnimalSpawningAllowedByTime()) {
                    for (int i = 0; i < Utils.rand(1, 2); i++) {
                        this.spawnTask.createEntity("Parrot", pos.add(0.5, 1, 0.5));
                    }
                }
            }
        }
    }

    @Override
    public final int getEntityNetworkId() {
        return EntityParrot.NETWORK_ID;
    }
}
