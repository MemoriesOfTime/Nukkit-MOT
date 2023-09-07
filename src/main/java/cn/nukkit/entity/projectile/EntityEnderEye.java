package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.generator.populator.overworld.PopulatorStronghold;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;

import java.util.concurrent.ThreadLocalRandom;

public class EntityEnderEye extends EntityProjectile {
    public static final int NETWORK_ID = 70;
    public Vector3 strongHold;

    public EntityEnderEye(FullChunk fullChunk, CompoundTag compoundTag) {
        this(fullChunk, compoundTag, null);
    }

    public EntityEnderEye(FullChunk fullChunk, CompoundTag compoundTag, Entity entity) {
        super(fullChunk, compoundTag, entity);

        double distance = Integer.MAX_VALUE;

        for (long pos : PopulatorStronghold.strongholdPos) {
            Vector3 strong = new Vector3(Level.getHashX(pos) << 4, 0, Level.getHashZ(pos) << 4);
            double dis = this.distanceSquared(strong);
            if (dis < distance) {
                distance = dis;
                strongHold = strong;
            }
        }

        if (strongHold == null) {
            if (entity != null) {
                strongHold = entity.getPosition();
            } else {
                strongHold = new Vector3(0, 0, 0);
            }
        }
    }

    @Override
    public int getNetworkId() {
        return 70;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        boolean update = super.onUpdate(currentTick);

        if (!this.isAlive()) {
            return update;
        }

        if (age >= 60) {
            if ((double) ThreadLocalRandom.current().nextFloat() > 0.2) {
                level.dropItem(this, Item.get(ItemID.ENDER_EYE));
            } else {
                level.addLevelEvent(new Vector3(x, y, z), LevelEventPacket.EVENT_PARTICLE_EYE_DESPAWN);
            }
            this.close();
            update = true;
        } else if (age >= 45) {
            this.setMotion(new Vector3(0.0, 0.15, 0.0));
            update = true;
        } else {
            this.setMotion(new Vector3(Math.max(Math.min(strongHold.x - x, 0.3), -0.3), y < 100 ? 0.2 : 0, Math.max(Math.min(strongHold.z - z, 0.3), -0.3)));
        }

        return update;
    }
}
