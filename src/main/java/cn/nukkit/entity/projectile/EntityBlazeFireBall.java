package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

@Deprecated
public class EntityBlazeFireBall extends EntitySmallFireBall {
    public EntityBlazeFireBall(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public EntityBlazeFireBall(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }
}
