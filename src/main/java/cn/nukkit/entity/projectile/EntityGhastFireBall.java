package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.event.entity.*;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.WeakExplosion;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

public class EntityGhastFireBall extends EntityProjectile implements EntityExplosive {

    public static final int NETWORK_ID = 85;

    private boolean canExplode;
    private boolean resolvingImpact;
    private boolean reflecting;
    private boolean impactAccepted;

    public Player directionChanged;

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.31f;
    }

    @Override
    public float getHeight() {
        return 0.31f;
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
    public double getBaseDamage() {
        return 6;
    }

    public EntityGhastFireBall(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityGhastFireBall(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        this(chunk, nbt, shootingEntity, false);
    }

    public EntityGhastFireBall(FullChunk chunk, CompoundTag nbt, Entity shootingEntity, boolean critical) {
        super(chunk, nbt, shootingEntity);
    }

    public void setExplode(boolean bool) {
        this.canExplode = bool;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed || this.reflecting || this.resolvingImpact) {
            return false;
        }

        if (this.age > 1200 || this.impactAccepted || this.isCollided || this.hadCollision) {
            if ((this.impactAccepted || this.isCollided) && this.canExplode) {
                this.explode();
            } else {
                this.close();
            }
            return false;
        }

        super.onUpdate(currentTick);
        return !this.closed;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        if (this.closed || this.impactAccepted || this.isCollided || this.hadCollision || this.resolvingImpact
                || this.reflecting || entity == this.directionChanged) {
            return;
        }
        this.resolvingImpact = true;
        try {
            ProjectileHitEvent hitEvent = new ProjectileHitEvent(this, MovingObjectPosition.fromEntity(entity));
            this.server.getPluginManager().callEvent(hitEvent);
            if (hitEvent.isCancelled() || this.closed || this.impactAccepted || this.isCollided || this.hadCollision
                    || entity == this.directionChanged) {
                return;
            }

            // Seal this physical source before damage/combustion listeners can reenter it.
            this.impactAccepted = true;
            this.isCollided = true;
            float damage = this.getResultDamage();
            EntityDamageEvent ev;
            if (this.shootingEntity == null) {
                ev = new EntityDamageByEntityEvent(this, entity, EntityDamageEvent.DamageCause.PROJECTILE, damage);
            } else {
                ev = new EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, EntityDamageEvent.DamageCause.PROJECTILE, damage);
            }

            entity.attack(ev);
            EntityCombustByEntityEvent combustEvent = new EntityCombustByEntityEvent(this, entity, 5);
            this.server.getPluginManager().callEvent(combustEvent);
            if (!combustEvent.isCancelled()) {
                entity.setOnFire(combustEvent.getDuration());
            }
        } finally {
            this.resolvingImpact = false;
        }
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (source.isCancelled() || !canReflect() || !(source instanceof EntityDamageByEntityEvent damage)
                || !(damage.getDamager() instanceof Player player)) {
            return false;
        }
        Level world = this.level;
        if (!validDeflector(player, world)) {
            return false;
        }
        this.reflecting = true;
        try {
            this.server.getPluginManager().callEvent(source);
            if (source.isCancelled() || !availableReflectionSource() || !validDeflector(player, world)) {
                return false;
            }
            if (!Double.isFinite(player.yaw) || !Double.isFinite(player.pitch)) {
                return false;
            }
            Vector3 motion = player.getLocation().getDirectionVector();
            if (!finiteMotion(motion)) {
                return false;
            }
            // Existing custom fireballs retain their virtual motion implementation.
            if (this.getClass() != EntityGhastFireBall.class) {
                if (!this.setMotion(motion) || !availableReflectionSource() || !validDeflector(player, world)
                        || !Double.isFinite(this.motionX) || !Double.isFinite(this.motionY) || !Double.isFinite(this.motionZ)) {
                    return false;
                }
                this.directionChanged = player;
                this.shootingEntity = player;
                return true;
            }
            // Dispatch native setMotion's event before committing motion and attribution together.
            if (this.server.callEntityMotionEv && !this.justCreated) {
                EntityMotionEvent event = new EntityMotionEvent(this, motion);
                this.server.getPluginManager().callEvent(event);
                if (event.isCancelled()) {
                    return false;
                }
            }
            if (!availableReflectionSource() || !validDeflector(player, world) || !finiteMotion(motion)) {
                return false;
            }
            this.motionX = motion.x;
            this.motionY = motion.y;
            this.motionZ = motion.z;
            this.directionChanged = player;
            this.shootingEntity = player;
            if (!this.justCreated) {
                this.updateMovement();
            }
            return true;
        } finally {
            this.reflecting = false;
        }
    }

    private boolean canReflect() {
        return !this.reflecting && availableReflectionSource();
    }

    private boolean availableReflectionSource() {
        return !this.closed && !this.impactAccepted && !this.isCollided && !this.hadCollision && !this.resolvingImpact;
    }

    private boolean validDeflector(Player player, Level world) {
        return this.level == world && world != null && !player.closed && player.isOnline()
                && player.isAlive() && !player.isSpectator() && player.getLevel() == world;
    }

    private static boolean finiteMotion(Vector3 motion) {
        return Double.isFinite(motion.x) && Double.isFinite(motion.y) && Double.isFinite(motion.z);
    }

    @Override
    public void explode() {
        if (this.closed) {
            return;
        }
        this.close();

        if (this.level.getGameRules().getBoolean(GameRule.TNT_EXPLODES)) {
            EntityExplosionPrimeEvent ev = new EntityExplosionPrimeEvent(this, 1.2);
            this.server.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) {
                WeakExplosion explosion = new WeakExplosion(this, (float) ev.getForce(), this.shootingEntity, this);
                if (ev.isBlockBreaking() && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
                    explosion.explodeA();
                }
                explosion.explodeB();
            }
        }
    }
}
