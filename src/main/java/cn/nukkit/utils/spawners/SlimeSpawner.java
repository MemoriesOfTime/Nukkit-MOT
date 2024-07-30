package cn.nukkit.utils.spawners;

import cn.nukkit.Player;
import cn.nukkit.entity.mob.EntitySlime;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.utils.AbstractEntitySpawner;
import cn.nukkit.utils.SpawnerTask;

public class SlimeSpawner extends AbstractEntitySpawner {

    public SlimeSpawner(SpawnerTask spawnTask) {
        super(spawnTask);
    }

    @Override
    public void spawn(Player player, Position pos, Level level) {
        final int biomeId = level.getBiomeId((int) pos.x, (int) pos.z);
        if (pos.y < 70 && (biomeId == 6 || biomeId == 134)) {
            if (level.getBlockLightAt((int) pos.x, (int) pos.y + 1, (int) pos.z) <= 7) {
                if (level.isMobSpawningAllowedByTime()) {
                    this.spawnTask.createEntity("Slime", pos.add(0.5, 1, 0.5));
                }
            }
        }
    }

    @Override
    public final int getEntityNetworkId() {
        return EntitySlime.NETWORK_ID;
    }
}
