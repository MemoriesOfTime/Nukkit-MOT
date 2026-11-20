package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.StrongExplosion;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.SmokeParticle;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;

public class EntityBlueWitherSkull extends EntityWitherSkull implements EntityExplosive {

    public static final int NETWORK_ID = 91;

    private boolean canExplode;
    private boolean entityImpact;
    private cn.nukkit.math.Vector3 entityImpactPosition;

    public EntityBlueWitherSkull(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    public EntityBlueWitherSkull(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        this(chunk, nbt, shootingEntity, false);
    }

    public EntityBlueWitherSkull(FullChunk chunk, CompoundTag nbt, Entity shootingEntity, boolean critical) {
        super(chunk, nbt, shootingEntity);
    }

    public void setExplode(boolean bool) {
        this.canExplode = bool;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        boolean previousImpact = this.entityImpact;
        cn.nukkit.math.Vector3 previousPosition = this.entityImpactPosition;
        this.entityImpact = true;
        this.entityImpactPosition = this.getEntityImpactPosition(entity);
        try {
            super.onCollideWithEntity(entity);
        } finally {
            this.entityImpact = previousImpact;
            this.entityImpactPosition = previousPosition;
        }
    }

    @Override
    protected void onHit() {
        // Accepted entity hit only: keep the existing block-impact update order.
        if (this.entityImpact && this.canExplode
                && this.level.getGameRules().getBoolean(GameRule.PROJECTILES_CAN_BREAK_BLOCKS)) {
            if (this.entityImpactPosition != null) {
                this.setPosition(this.entityImpactPosition);
            }
            this.explode();
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.age > 1200 || this.isCollided || this.hadCollision) {
            if (this.level.getGameRules().getBoolean(GameRule.PROJECTILES_CAN_BREAK_BLOCKS) && this.canExplode) {
                this.explode();
            }else {
                this.close();
            }
        } else if (this.age % 4 == 0) {
            this.level.addParticle(new SmokeParticle(this.add(this.getWidth() / 2 + Utils.rand(-100.0, 100.0) / 500, this.getHeight() / 2 + Utils.rand(-100.0, 100.0) / 500, this.getWidth() / 2 + Utils.rand(-100.0, 100.0) / 500)));
        }

        super.onUpdate(currentTick);
        return !this.closed;
    }

    @Override
    public void explode() {
        if (this.closed) {
            return;
        }
        this.close();

        EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, 1.2);
        this.server.getPluginManager().callEvent(ev);

        if (!ev.isCancelled()) {
            Explosion explosion = new StrongExplosion(this, (float) ev.getForce(), this.shootingEntity);
            if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
                explosion.explodeA();
            }

            explosion.explodeB();
        }
    }
}
