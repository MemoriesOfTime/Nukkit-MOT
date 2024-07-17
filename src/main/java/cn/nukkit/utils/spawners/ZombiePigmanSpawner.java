package cn.nukkit.utils.spawners;

import cn.nukkit.Player;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.mob.EntityZombiePigman;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.utils.AbstractEntitySpawner;
import cn.nukkit.utils.SpawnerTask;
import cn.nukkit.utils.Utils;

public class ZombiePigmanSpawner extends AbstractEntitySpawner {

    public ZombiePigmanSpawner(SpawnerTask spawnTask) {
        super(spawnTask);
    }

    @Override
    public void spawn(Player player, Position pos, Level level) {
        if (level.getBlockLightAt((int) pos.x, (int) pos.y + 1, (int) pos.z) <= 7) {
            for (int i = 0; i < Utils.rand(2, 4); i++) {
                BaseEntity entity = this.spawnTask.createEntity("ZombiePigman", pos.add(0.5, 1, 0.5));
                if (entity == null) return;
                if (Utils.rand(1, 20) == 1) {
                    entity.setBaby(true);
                }
            }
        }
    }

    @Override
    public final int getEntityNetworkId() {
        return EntityZombiePigman.NETWORK_ID;
    }
}
