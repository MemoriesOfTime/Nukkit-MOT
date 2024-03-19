package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.item.EntityBoat;
import cn.nukkit.entity.item.EntityEndCrystal;
import cn.nukkit.entity.item.EntityMinecartAbstract;
import cn.nukkit.entity.mob.EntityBlaze;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import org.apache.commons.math3.util.FastMath;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EntityProjectile extends Entity {

    public static final int DATA_SHOOTER_ID = 17;

    public static final int PICKUP_NONE = 0;
    public static final int PICKUP_ANY = 1;
    public static final int PICKUP_CREATIVE = 2;

    public Entity shootingEntity;

    protected double getDamage() {
        return namedTag.contains("damage") ? namedTag.getDouble("damage") : getBaseDamage();
    }

    protected double getBaseDamage() {
        return 0;
    }

    public boolean hadCollision = false;

    public int piercing;

    public EntityProjectile(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityProjectile(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt);
        this.shootingEntity = shootingEntity;
        /*if (shootingEntity != null) {
            this.setDataProperty(new LongEntityData(DATA_SHOOTER_ID, shootingEntity.getId()));
        }*/
    }

    public int getResultDamage() {
        return NukkitMath.ceilDouble(Math.sqrt(this.motionX * this.motionX + this.motionY * this.motionY + this.motionZ * this.motionZ) * getDamage());
    }

    public boolean attack(EntityDamageEvent source) {
        return source.getCause() == DamageCause.VOID && super.attack(source);
    }

    public void onCollideWithEntity(Entity entity) {
        ProjectileHitEvent hitEvent = new ProjectileHitEvent(this, MovingObjectPosition.fromEntity(entity));
        this.server.getPluginManager().callEvent(hitEvent);
        if (hitEvent.isCancelled()) {
            return;
        }

        float damage = this instanceof EntitySnowball && entity instanceof EntityBlaze ? 3 : this.getResultDamage();

        EntityDamageEvent ev;
        if (this.shootingEntity == null) {
            ev = new EntityDamageByEntityEvent(this, entity, DamageCause.PROJECTILE, damage);
        } else {
            ev = new EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, DamageCause.PROJECTILE, damage);
        }

        if (entity.attack(ev)) {
            this.hadCollision = true;

            this.onHit();

            if (this.fireTicks > 0) {
                EntityCombustByEntityEvent event = new EntityCombustByEntityEvent(this, entity, 5);
                this.server.getPluginManager().callEvent(event);
                if (!event.isCancelled()) {
                    entity.setOnFire(event.getDuration());
                }
            }
        }

        this.close();
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(1);
        super.initEntity();
        this.setHealth(1);
        if (this.namedTag.contains("Age")) {
            this.age = this.namedTag.getShort("Age");
        }

        this.updateRotation();
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return (entity instanceof EntityLiving || entity instanceof EntityEndCrystal || entity instanceof EntityMinecartAbstract || entity instanceof EntityBoat) && !this.onGround;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putShort("Age", this.age);
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        int tickDiff = currentTick - this.lastUpdate;
        if (tickDiff <= 0 && !this.justCreated) {
            return true;
        }
        this.lastUpdate = currentTick;

        boolean hasUpdate = this.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            MovingObjectPosition movingObjectPosition = null;

            if (!this.isCollided) {
                this.updateMotion();
            }

            Vector3 moveVector = new Vector3(this.x + this.motionX, this.y + this.motionY, this.z + this.motionZ);

            Entity[] list = this.getLevel().getCollidingEntities(this.boundingBox.addCoord(this.motionX, this.motionY, this.motionZ).expand(1, 1, 1), this);

            double nearDistance = Integer.MAX_VALUE;
            Entity nearEntity = null;

            for (Entity entity : list) {
                if (/*!entity.canCollideWith(this) || */(entity == this.shootingEntity && this.age < 5) || (entity.isPlayer && ((Player) entity).getGamemode() == Player.SPECTATOR)) {
                    continue;
                }

                AxisAlignedBB axisalignedbb = entity.boundingBox.grow(0.3, 0.3, 0.3);
                MovingObjectPosition ob = axisalignedbb.calculateIntercept(this, moveVector);

                if (ob == null) {
                    continue;
                }

                double distance = this.distanceSquared(ob.hitVector);

                if (distance < nearDistance) {
                    nearDistance = distance;
                    nearEntity = entity;
                }
            }

            if (nearEntity != null) {
                movingObjectPosition = MovingObjectPosition.fromEntity(nearEntity);
            }

            if (movingObjectPosition != null && movingObjectPosition.entityHit != null) {
                onCollideWithEntity(movingObjectPosition.entityHit);
                hasUpdate = true;
                if (this.closed) {
                    return false;
                }
            }

            this.move(this.motionX, this.motionY, this.motionZ);

            if (this.isCollided && !this.hadCollision) { //collide with block
                ProjectileHitEvent hitEvent = new ProjectileHitEvent(this, MovingObjectPosition.fromBlock(this.getFloorX(), this.getFloorY(), this.getFloorZ(), -1, this));
                this.server.getPluginManager().callEvent(hitEvent);
                if (!hitEvent.isCancelled()) {
                    this.hadCollision = true;

                    this.motionX = 0;
                    this.motionY = 0;
                    this.motionZ = 0;

                    this.onHit();
                    this.onHitGround(moveVector);

                    return true;
                }
            } else if (!this.isCollided && this.hadCollision) {
                this.hadCollision = false;
            }

            if (!this.hadCollision || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001) {
                updateRotation();
                hasUpdate = true;
            }

            this.updateMovement();
        }

        return hasUpdate;
    }

    protected void updateMotion() {
        if (this.isInsideOfWater()) {
            this.motionY -= this.getGravity() - (this.getGravity() / 2);
        } else {
            this.motionY -= this.getGravity();
        }
        this.motionX *= 1 - this.getDrag();
        this.motionZ *= 1 - this.getDrag();
    }

    public void updateRotation() {
        double f = Math.sqrt((this.motionX * this.motionX) + (this.motionZ * this.motionZ));
        this.yaw = FastMath.atan2(this.motionX, this.motionZ) * 180 / Math.PI;
        this.pitch = FastMath.atan2(this.motionY, f) * 180 / Math.PI;
    }

    public void inaccurate(float modifier) {
        ThreadLocalRandom rand = ThreadLocalRandom.current();

        this.motionX += rand.nextGaussian() * 0.007499999832361937 * modifier;
        this.motionY += rand.nextGaussian() * 0.007499999832361937 * modifier;
        this.motionZ += rand.nextGaussian() * 0.007499999832361937 * modifier;
    }

    protected void onHit() {

    }

    protected void onHitGround(Vector3 vector3) {
        Block block = this.level.getBlock(this.chunk, vector3.getFloorX(), vector3.getFloorY(), vector3.getFloorZ(), 0, false);
        if (block.hasEntityCollision()) {
            block.onEntityCollide(this);
        }
    }
}
