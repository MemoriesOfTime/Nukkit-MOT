package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.data.NBTEntityData;
import cn.nukkit.entity.data.Vector3fEntityData;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemFirework;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.math.Vector3f;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.BlockIterator;
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
    protected boolean hadCollision;

    public EntityFirework(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public EntityFirework(FullChunk chunk, CompoundTag nbt, boolean projectile) {
        super(chunk, nbt);
        this.isProjectile = projectile;

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        int lifetime = namedTag.contains("FireworkLifeTime") ? namedTag.getInt("FireworkLifeTime") : 30 + rand.nextInt(12);
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

            Vector3 moveVector = new Vector3(this.x + this.motionX, this.y + this.motionY, this.z + this.motionZ);
            Entity collisionEntity = this.findCollisionEntity(moveVector);
            if (collisionEntity != null) {
                this.explode();
                return true;
            }

            this.move(this.motionX, this.motionY, this.motionZ);

            if (this.isCollided && !this.hadCollision) {
                this.hadCollision = true;
                if (this.hasExplosions()) {
                    this.explode();
                    return true;
                }
            } else if (!this.isCollided && this.hadCollision) {
                this.hadCollision = false;
            }

            this.updateMovement();

            float f = (float) Math.sqrt(this.motionX * this.motionX + this.motionZ * this.motionZ);
            this.yaw = (float) (FastMath.atan2(this.motionX, this.motionZ) * (57.29577951308232));

            this.pitch = (float) (FastMath.atan2(this.motionY, f) * (57.29577951308232));

            if (this.age == 0) {
                this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LAUNCH);
            }

            if (this.age >= this.lifetime) {
                this.explode();

                hasUpdate = true;
            }
        }

        return hasUpdate || !this.onGround || Math.abs(this.motionX) > 0.00001 || Math.abs(this.motionY) > 0.00001 || Math.abs(this.motionZ) > 0.00001;
    }

    protected Entity findCollisionEntity(Vector3 moveVector) {
        Entity[] entities = this.getLevel().getCollidingEntities(this.boundingBox.addCoord(this.motionX, this.motionY, this.motionZ).expand(1, 1, 1), this);

        double nearestDistance = Double.MAX_VALUE;
        Entity nearestEntity = null;

        for (Entity entity : entities) {
            if (this.shouldIgnoreCollisionEntity(entity)) {
                continue;
            }

            AxisAlignedBB boundingBox = entity.boundingBox.grow(0.3, 0.3, 0.3);
            MovingObjectPosition intercept = boundingBox.calculateIntercept(this, moveVector);
            if (intercept == null) {
                continue;
            }

            double distance = this.distanceSquared(intercept.hitVector);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearestEntity = entity;
            }
        }

        return nearestEntity;
    }

    protected boolean shouldIgnoreCollisionEntity(Entity entity) {
        return entity == null
                || !entity.isAlive()
                || !entity.canCollideWith(this)
                || entity == this
                || entity instanceof Player player && player.getGamemode() == Player.SPECTATOR;
    }

    protected void explode() {
        EntityEventPacket pk = new EntityEventPacket();
        pk.event = EntityEventPacket.FIREWORK_EXPLOSION;
        pk.eid = this.getId();

        this.level.addChunkPacket(this.getFloorX() >> 4, this.getFloorZ() >> 4, pk);

        level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_LARGE_BLAST, -1, NETWORK_ID);

        this.dealExplosionDamage();

        this.kill(); // Using close() here would remove the firework before the explosion is displayed
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
        this.lifetime = computeLifetime(item);
    }

    protected int computeLifetime(Item item) {
        return computeLifetimeForItem(item);
    }

    public static int computeLifetimeForItem(Item item) {
        int flight = Math.max(1, item instanceof ItemFirework ? ((ItemFirework) item).getFlight() : 1);
        ThreadLocalRandom threadLocalRandom = ThreadLocalRandom.current();
        return 10 * (flight + 1) + threadLocalRandom.nextInt(6) + threadLocalRandom.nextInt(7);
    }

    protected int getExplosionCount() {
        if (!(this.firework instanceof ItemFirework itemFirework)) {
            return 0;
        }

        CompoundTag tag = itemFirework.getNamedTag();
        if (tag == null || !tag.contains("Fireworks")) {
            return 0;
        }

        ListTag<CompoundTag> explosions = tag.getCompound("Fireworks").getList("Explosions", CompoundTag.class);
        return explosions == null ? 0 : explosions.size();
    }

    protected boolean hasExplosions() {
        return this.getExplosionCount() > 0;
    }

    protected void dealExplosionDamage() {
        int explosionCount = this.getExplosionCount();
        if (explosionCount <= 0) {
            return;
        }

        float baseDamage = 5 + explosionCount * 2;
        AxisAlignedBB damageBox = this.getBoundingBox().grow(5, 5, 5);

        for (Entity target : this.level.getNearbyEntities(damageBox, this)) {
            if (!target.isAlive() || target == this || this.shouldSkipExplosionDamageTarget(target)) {
                continue;
            }

            double distance = this.distance(target);
            if (distance > 5) {
                continue;
            }

            if (!canDamage(target)) {
                continue;
            }

            float damage = (float) (baseDamage * Math.sqrt((5 - distance) / 5));
            if (damage <= 0) {
                continue;
            }

            target.attack(new EntityDamageEvent(target, DamageCause.ENTITY_EXPLOSION, damage));
        }
    }

    protected boolean shouldSkipExplosionDamageTarget(Entity target) {
        return false;
    }

    protected boolean canDamage(Entity target) {
        if (target == null) {
            return false;
        }

        Vector3 start = this.add(0, this.getHeight() * 0.5, 0);
        Vector3 lowerTargetPoint = target.add(0, 0, 0);
        Vector3 middleTargetPoint = target.add(0, target.getHeight() * 0.5, 0);
        return this.hasClearDamagePath(start, lowerTargetPoint) || this.hasClearDamagePath(start, middleTargetPoint);
    }

    protected boolean hasClearDamagePath(Vector3 start, Vector3 end) {
        Vector3 direction = end.subtract(start);
        double length = direction.length();
        if (length <= 0.00001) {
            return true;
        }

        BlockIterator iterator = new BlockIterator(this.level, start, direction.normalize(), 0, (int) Math.ceil(length));
        while (iterator.hasNext()) {
            var block = iterator.next();
            if (!block.canPassThrough()) {
                return block.getFloorX() == end.getFloorX()
                        && block.getFloorY() == end.getFloorY()
                        && block.getFloorZ() == end.getFloorZ();
            }
        }
        return true;
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
