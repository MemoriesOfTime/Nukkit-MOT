package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;

public class EntityWitherSkull extends EntityProjectile {

    public static final int NETWORK_ID = 89;

    public EntityWitherSkull(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public float getLength() {
        return 0.25f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }

    @Override
    public float getGravity() {
        return 0.001f;
    }

    @Override
    public float getDrag() {
        return 0.01f;
    }

    @Override
    protected double getBaseDamage() {
        return switch (server.getDifficulty()) {
            case 2 -> 8; // normal
            case 3 -> 12; // hard
            default -> 5;
        };
    }

    public EntityWitherSkull(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        this(chunk, nbt, shootingEntity, false);
    }

    public EntityWitherSkull(FullChunk chunk, CompoundTag nbt, Entity shootingEntity, boolean critical) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.age > 1200 || this.isCollided || this.hadCollision) {
            this.close();
        } else if (this.age % 4 == 0) {
            this.level.addParticle(new SmokeParticle(this.add(this.getWidth() / 2 + Utils.rand(-100.0, 100.0) / 500, this.getHeight() / 2 + Utils.rand(-100.0, 100.0) / 500, this.getWidth() / 2 + Utils.rand(-100.0, 100.0) / 500)));
        }

        super.onUpdate(currentTick);
        return !this.closed;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        super.onCollideWithEntity(entity);
        entity.addEffect(Effect.getEffect(Effect.WITHER).setDuration(200), EntityPotionEffectEvent.Cause.ATTACK);
    }
}
