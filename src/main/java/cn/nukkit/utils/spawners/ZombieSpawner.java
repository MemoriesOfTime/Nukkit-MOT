package cn.nukkit.utils.spawners;

import cn.nukkit.Player;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.mob.EntityZombie;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.utils.AbstractEntitySpawner;
import cn.nukkit.utils.SpawnerTask;
import cn.nukkit.utils.Utils;

public class ZombieSpawner extends AbstractEntitySpawner {

    public ZombieSpawner(SpawnerTask spawnTask) {
        super(spawnTask);
    }

    @Override
    public void spawn(Player player, Position pos, Level level) {
        if (level.getBlockLightAt((int) pos.x, (int) pos.y + 1, (int) pos.z) <= 7) {
            if (level.isMobSpawningAllowedByTime()) {
                if (Utils.rand(1, 40) == 30) {
                    BaseEntity entity = this.spawnTask.createEntity("ZombieVillager", pos.add(0.5, 1, 0.5));
                    if (entity == null) return;
                    if (Utils.rand(1, 20) == 1) {
                        entity.setBaby(true);
                    }
                } else {
                    BaseEntity entity = this.spawnTask.createEntity("Zombie", pos.add(0.5, 1, 0.5));
                    if (entity == null) return;
                    if (Utils.rand(1, 20) == 1) {
                        entity.setBaby(true);
                    }
                }
            }
        }
    }

    @Override
    public final int getEntityNetworkId() {
        return EntityZombie.NETWORK_ID;
    }
}
