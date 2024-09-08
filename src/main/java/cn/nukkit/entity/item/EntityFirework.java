package cn.nukkit.entity.item;

import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.NBTEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.math3.util.FastMath;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author CreeperFace
 */
public class EntityFirework extends Entity {

    public static final int NETWORK_ID = 72;

    private static final Vector3f DEFAULT_DIRECTION = new Vector3f(0, 1, 0);

    @Getter
    @Setter
    protected int lifetime;

    protected Item firework;

    protected boolean isProjectile;

    public EntityFirework(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public EntityFirework(FullChunk chunk, CompoundTag nbt, boolean projectile) {
        super(chunk, nbt);
        this.isProjectile = projectile;

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        int lifetime;
        boolean contains = namedTag.contains("FireworkLifeTime");
        if (contains) {
            lifetime = namedTag.getInt("FireworkLifeTime");
        } else {
            lifetime = 30 + rand.nextInt(12);
        }
        this.setLifetime(lifetime);

        if (namedTag.contains("FireworkItem")) {
            this.setFirework(NBTIO.getItemHelper(this.namedTag.getCompound("FireworkItem")));
            this.setDataProperty(new Vector3fEntityData(Entity.DATA_FIREWORK_DIRECTION, this.isProjectile ? new Vector3f((float) motionX, (float) motionY, (float) motionZ) : DEFAULT_DIRECTION), false);
            this.setDataProperty(new LongEntityData(Entity.DATA_HAS_DISPLAY, -1), false);
        }

        if (this.isProjectile) {
            this.motionX = rand.nextGaussian() * 0.001 + this.motionX * 0.02;
            this.motionY = rand.nextGaussian() * 0.001 + this.motionY * 0.02;
            this.motionZ = rand.nextGaussian() * 0.001 + this.motionZ * 0.02;
        } else {
            this.motionX = rand.nextGaussian() * 0.001;
            this.motionY = rand.nextGaussian() * 0.001 + 1 * 0.02;
            this.motionZ = rand.nextGaussian() * 0.001;
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        if (this.firework != null) {
            this.namedTag.putCompound("FireworkItem", NBTIO.putItemHelper(this.firework));
            this.namedTag.putInt("FireworkLifeTime", this.lifetime);
        }
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
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

            Vector3f dir = getDataPropertyVector3f(DATA_FIREWORK_DIRECTION);
            this.motionX = this.motionX * 1.05 + dir.x * 0.03;
            this.motionY = this.motionY * 1.05 + dir.y * 0.03;
            this.motionZ = this.motionZ * 1.05 + dir.z * 0.03;

            this.move(this.motionX, this.motionY, this.motionZ);

            this.updateMovement();

            float f = (float) Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
            this.yaw = (float) (FastMath.atan2(this.motionX, this.motionZ) * (57.29577951308232));

            this.pitch = (float) (FastMath.atan2(this.motionY, f) * (57.29577951308232));

            if (this.age == 0) {
                this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LAUNCH);
            }

            if (this.age >= this.lifetime) {
                EntityEventPacket pk = new EntityEventPacket();
                pk.event = EntityEventPacket.FIREWORK_EXPLOSION;
                pk.eid = this.getId();

                this.level.addChunkPacket(this.getFloorX() >> 4, this.getFloorZ() >> 4, pk);

                level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LARGE_BLAST, -1, NETWORK_ID);

                this.kill(); // Using close() here would remove the firework before the explosion is displayed

                hasUpdate = true;
            }
        }

        return hasUpdate || !this.onGround || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return (source.getCause() == DamageCause.VOID ||
                source.getCause() == DamageCause.FIRE_TICK ||
                source.getCause() == DamageCause.ENTITY_EXPLOSION ||
                source.getCause() == DamageCause.BLOCK_EXPLOSION)
                && super.attack(source);
    }

    public void setFirework(Item item) {
        this.firework = item;
        this.setDataProperty(new NBTEntityData(Entity.DATA_DISPLAY_ITEM, firework));
        int flight = Math.max(1, this.firework instanceof ItemFirework ? ((ItemFirework) this.firework).getFlight() : 1);
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        this.lifetime = 10 * (flight + 1) + threadLocalRandom.nextInt(5) + threadLocalRandom.nextInt(6);
    }

    @Override
    public float getWidth() {
        return 0.25f;
    }

    @Override
    public float getHeight() {
        return 0.25f;
    }
}