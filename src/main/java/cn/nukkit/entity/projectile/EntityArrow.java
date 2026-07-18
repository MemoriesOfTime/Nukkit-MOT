package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityPotionEffectEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class EntityArrow extends EntitySlenderProjectile {

    public static final int NETWORK_ID = 80;

    public static final int DATA_SOURCE_ID = 17;

    protected int pickupMode;
    protected boolean critical;
    protected boolean isFullEffect;

    protected Int2ObjectMap<Effect> mobEffects;
    protected int auxValue;

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.05f;
    }

    @Override
    public float getLength() {
        return 0.5f;
    }

    @Override
    public float getHeight() {
        return 0.05f;
    }

    @Override
    public float getGravity() {
        return 0.05f;
    }

    @Override
    public float getDrag() {
        return 0.01f;
    }

    public EntityArrow(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityArrow(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        this(chunk, nbt, shootingEntity, false);
    }

    public EntityArrow(FullChunk chunk, CompoundTag nbt, Entity shootingEntity, boolean critical) {
        super(chunk, nbt, shootingEntity);
        this.setCritical(critical);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.pickupMode = this.namedTag.contains("pickup") ? this.namedTag.getByte("pickup") : PICKUP_ANY;
        this.isFullEffect = this.namedTag.getBoolean("isFullEffect");

        ListTag<CompoundTag> mobEffects = this.namedTag.getList("mobEffects", CompoundTag.class);
        this.mobEffects = new Int2ObjectOpenHashMap<>(mobEffects.size());
        for (CompoundTag tag : mobEffects.getAll()) {
            Effect effect = Effect.load(tag);
            if (effect == null) {
                continue;
            }
            this.mobEffects.put(effect.getId(), effect);
        }

        this.auxValue = this.namedTag.getByte("auxValue");
        this.getDataProperties().putByte(DATA_ARROW_AUX_VALUE, this.auxValue);
    }

    /**
     * Override spawn-to so the tipped-arrow {@code auxValue} is downgraded for
     * viewers whose clients predate the special arrows introduced in 1.21.0
     * (Weaving/Oozing/Infested use arrow meta >= 44). Sending those unknown
     * values to older clients (e.g. 1.20.12) crashes the client instantly.
     * <p>
     * {@code DATA_ARROW_AUX_VALUE} is only written once in {@link #initEntity()}
     * and never broadcast again, so covering the initial spawn is sufficient.
     */
    @Override
    public void spawnTo(Player player) {
        boolean needsDowngrade = player.protocol < ProtocolInfo.v1_21_0 && this.auxValue >= 44;
        if (!needsDowngrade) {
            super.spawnTo(player);
            return;
        }

        // Build a viewer-specific AddEntityPacket with auxValue zeroed out so
        // the older client sees a plain arrow instead of an unknown tipped one.
        if (this.hasSpawned.containsKey(player.getLoaderId())
                || !player.usedChunks.containsKey(Level.chunkHash(this.chunk.getX(), this.chunk.getZ()))) {
            return;
        }

        AddEntityPacket addEntity = new AddEntityPacket();
        addEntity.type = this.getNetworkId();
        addEntity.entityUniqueId = this.id;
        addEntity.entityRuntimeId = this.id;
        addEntity.yaw = (float) this.yaw;
        addEntity.headYaw = (float) this.yaw;
        addEntity.pitch = (float) this.pitch;
        addEntity.x = (float) this.x;
        addEntity.y = (float) this.y + this.getBaseOffset();
        addEntity.z = (float) this.z;
        addEntity.speedX = (float) this.motionX;
        addEntity.speedY = (float) this.motionY;
        addEntity.speedZ = (float) this.motionZ;
        addEntity.metadata = this.dataProperties.clone().putByte(DATA_ARROW_AUX_VALUE, 0);
        addEntity.properties = this.propertySyncData();

        player.dataPacket(addEntity);
        this.hasSpawned.put(player.getLoaderId(), player);
    }

    public void setCritical() {
        this.setCritical(true);
    }

    public void setCritical(boolean value) {
        //this.setDataFlag(DATA_FLAGS, DATA_FLAG_CRITICAL, value);
        this.critical = value;
    }

    public boolean isCritical() {
        //return this.getDataFlag(DATA_FLAGS, DATA_FLAG_CRITICAL);
        return this.critical;
    }

    @Override
    public int getResultDamage() {
        int base = super.getResultDamage();

        if (this.isCritical()) {
            base += Utils.random.nextInt((base >> 1) + 2);
        }

        return base;
    }

    @Override
    protected double getBaseDamage() {
        return 2;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }

        if (this.age > 1200) {
            this.close();
            return false;
        }

        if (this.onGround || this.hadCollision) {
            this.setCritical(false);
        }

        if (this.fireTicks > 0 && this.level.isRaining() && this.canSeeSky()) {
            this.extinguish();
        }

        return super.onUpdate(currentTick);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putByte("pickup", this.pickupMode);
        this.namedTag.putBoolean("isFullEffect", this.isFullEffect);

        ListTag<CompoundTag> list = new ListTag<>("mobEffects");
        for (Effect effect : this.mobEffects.values()) {
            list.add(effect.save());
        }
        this.namedTag.putList(list);

        this.namedTag.putByte("auxValue", this.auxValue);
    }

    public Int2ObjectMap<Effect> getMobEffects() {
        return mobEffects;
    }

    public int getPickupMode() {
        return this.pickupMode;
    }

    public void setPickupMode(int pickupMode) {
        this.pickupMode = pickupMode;
    }

    public void setFullEffect(boolean fullEffect) {
        this.isFullEffect = fullEffect;
    }

    public boolean isFullEffect() {
        return this.isFullEffect;
    }

    @Override
    public void onHit() {
        this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_BOW_HIT);
    }

    @Override
    protected void onHitGround(Vector3 vector3) {
        super.onHitGround(vector3);

        EntityEventPacket pk = new EntityEventPacket();
        pk.eid = this.getId();
        pk.event = EntityEventPacket.ARROW_SHAKE;
        pk.data = 7;
        Server.broadcastPacket(this.getViewers().values(), pk);
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        super.onCollideWithEntity(entity);

        for (Effect effect : mobEffects.values()) {
            switch (effect.getId()) {
                case Effect.NO_EFFECT:
                    break;
                case Effect.INSTANT_HEALTH:
                    if (!entity.canBeAffected(effect.getId())) {
                        break;
                    }
                    if (entity instanceof EntitySmite) {
                        if (shootingEntity != null) {
                            entity.attack(new EntityDamageByEntityEvent(shootingEntity, entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
                        } else {
                            entity.attack(new EntityDamageEvent(entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
                        }
                    } else {
                        entity.heal(new EntityRegainHealthEvent(entity, 0.5f * (4 << effect.getAmplifier()), EntityRegainHealthEvent.CAUSE_MAGIC));
                    }
                    break;
                case Effect.INSTANT_DAMAGE:
                    if (!entity.canBeAffected(effect.getId())) {
                        break;
                    }
                    if (entity instanceof EntitySmite) {
                        entity.heal(new EntityRegainHealthEvent(entity, 0.5f * (4 << effect.getAmplifier()), EntityRegainHealthEvent.CAUSE_MAGIC));
                    } else if (shootingEntity != null) {
                        entity.attack(new EntityDamageByEntityEvent(shootingEntity, entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
                    } else {
                        entity.attack(new EntityDamageEvent(entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
                    }
                    break;
                case Effect.SATURATION:
                    if (entity instanceof Player player) {
                        int level = 1 + effect.getAmplifier();
                        player.getFoodData().addFoodLevel(level, level * 2);
                    }
                    break;
                default:
                    Effect clone = effect.clone();
                    if (!this.isFullEffect) {
                        clone.setDuration(effect.getDuration() / 8);
                    }
                    entity.addEffect(clone, EntityPotionEffectEvent.Cause.ARROW);
                    break;
            }
        }
    }
}
