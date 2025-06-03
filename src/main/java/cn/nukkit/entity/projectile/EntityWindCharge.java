package cn.nukkit.entity.projectile;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.GenericParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

/**
 * @author glorydark
 */
public class EntityWindCharge extends EntityProjectile {

    public static final int NETWORK_ID = 143;

    public Entity directionChanged;

    public EntityWindCharge(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityWindCharge(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        if (directionChanged != null) {
            if(directionChanged == entity) return;
        }
        entity.attack(new EntityDamageByEntityEvent(this, entity, EntityDamageEvent.DamageCause.PROJECTILE, 1f));
        level.addLevelSoundEvent(entity.getPosition().add(0, 1), LevelSoundEventPacket.SOUND_WIND_CHARGE_BURST);
        knockBack(entity);
        this.kill();
    }

    @Override
    public void onHit() {
        for (Entity entity : level.getEntities()) {
            if (entity instanceof EntityLiving entityLiving) {
                if (entityLiving.distance(this) < getBurstRadius()) {
                    this.knockBack(entityLiving);
                }
            }
        }
        level.addLevelSoundEvent(this.add(0, 1), LevelSoundEventPacket.SOUND_WIND_CHARGE_BURST);
        this.kill();

        this.level.addParticle(new GenericParticle(this, Particle.TYPE_WIND_EXPLOSION));
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (this.directionChanged == null && source instanceof EntityDamageByEntityEvent event) {
            this.directionChanged = event.getDamager();
            this.setMotion(event.getDamager().getDirectionVector());
            this.level.addParticle(new GenericParticle(event.getDamager(), Particle.TYPE_WIND_EXPLOSION));
        }
        return true;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        boolean hasUpdate = super.onUpdate(currentTick);

        if (this.age > 1200 || this.isCollided) {
            this.kill();
            hasUpdate = true;
        }

        return hasUpdate;
    }


    @Override
    public float getWidth() {
        return 0.3125f;
    }

    @Override
    public float getLength() {
        return 0.3125f;
    }

    @Override
    public float getHeight() {
        return 0.3125f;
    }

    @Override
    protected float getGravity() {
        return 0.00f;
    }

    @Override
    protected float getDrag() {
        return 0.01f;
    }

    public double getBurstRadius() {
        return 2f;
    }

    public double getKnockbackStrength() {
        return 0.2f;
    }

    protected void knockBack(Entity entity) {
        Vector3 knockback = new Vector3(entity.motionX, entity.motionY, entity.motionZ);
        knockback.x /= 2d;
        knockback.y /= 2d;
        knockback.z /= 2d;
        knockback.x -= (this.getX() - entity.getX()) * getKnockbackStrength();
        knockback.y += 1.0f;
        knockback.z -= (this.getZ() - entity.getZ()) * getKnockbackStrength();

        entity.setMotion(knockback);
    }

    @Override
    public String getName() {
        return "Wind Charge Projectile";
    }
}
