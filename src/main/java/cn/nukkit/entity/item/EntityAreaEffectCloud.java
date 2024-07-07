package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.entity.data.FloatEntityData;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.data.ShortEntityData;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityRegainHealthEvent;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.math.BlockFace;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import org.jetbrains.annotations.Nullable;

public class EntityAreaEffectCloud extends Entity {

    public static final int NETWORK_ID = 95;

    protected Int2ObjectMap<Effect> mobEffects;
    protected int reapplicationDelay;
    protected int durationOnUse;
    protected float initialRadius;
    protected float radiusOnUse;
    protected long ownerId;

    protected boolean affectOwner = true;
    @Nullable
    protected Entity owner;
    protected final Long2LongMap victims = new Long2LongOpenHashMap();

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    public EntityAreaEffectCloud(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public int getWaitTime() {
        return this.getDataPropertyInt(DATA_AREA_EFFECT_CLOUD_WAITING);
    }

    public void setWaitTime(int waitTime) {
        setWaitTime(waitTime, true);
    }

    public void setWaitTime(int waitTime, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_AREA_EFFECT_CLOUD_WAITING, waitTime), send);
    }

    public int getPotionId() {
        return this.getDataPropertyShort(DATA_AUX_VALUE_DATA);
    }

    public void setPotionId(int potionId) {
        setPotionId(potionId, true);
    }

    public void setPotionId(int potionId, boolean send) {
        this.setDataProperty(new ShortEntityData(DATA_AUX_VALUE_DATA, potionId & 0xFFFF), send);
    }

    public void recalculatePotionColor() {
        recalculatePotionColor(true);
    }

    public void recalculatePotionColor(boolean send) {
        if (namedTag.contains("ParticleColor")) {
            this.setPotionColor(namedTag.getInt("ParticleColor"), send);
        } else {
            this.setPotionColor(Effect.calculateColor(this.mobEffects.values().toArray(new Effect[0])), send);
        }
    }

    public int getPotionColor() {
        return this.getDataPropertyInt(DATA_EFFECT_COLOR);
    }

    public void setPotionColor(int argp) {
        setPotionColor(argp, true);
    }

    public void setPotionColor(int alpha, int red, int green, int blue, boolean send) {
        setPotionColor(((alpha & 0xff) << 24) | ((red & 0xff) << 16) | ((green & 0xff) << 8) | (blue & 0xff), send);
    }

    public void setPotionColor(int argp, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_EFFECT_COLOR, argp), send);
    }

    public int getPickupCount() {
        return this.getDataPropertyInt(DATA_AREA_EFFECT_CLOUD_PICKUP_COUNT);
    }

    public void setPickupCount(int pickupCount) {
        setPickupCount(pickupCount, true);
    }

    public void setPickupCount(int pickupCount, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_AREA_EFFECT_CLOUD_PICKUP_COUNT, pickupCount), send);
    }

    public float getRadiusChangeOnPickup() {
        return this.getDataPropertyFloat(DATA_AREA_EFFECT_CLOUD_CHANGE_ON_PICKUP);
    }

    public void setRadiusChangeOnPickup(float radiusChangeOnPickup) {
        setRadiusChangeOnPickup(radiusChangeOnPickup, true);
    }

    public void setRadiusChangeOnPickup(float radiusChangeOnPickup, boolean send) {
        this.setDataProperty(new FloatEntityData(DATA_AREA_EFFECT_CLOUD_CHANGE_ON_PICKUP, radiusChangeOnPickup), send);
    }

    public float getRadiusPerTick() {
        return this.getDataPropertyFloat(DATA_AREA_EFFECT_CLOUD_CHANGE_RATE);
    }

    public void setRadiusPerTick(float radiusPerTick) {
        setRadiusPerTick(radiusPerTick, true);
    }

    public void setRadiusPerTick(float radiusPerTick, boolean send) {
        this.setDataProperty(new FloatEntityData(DATA_AREA_EFFECT_CLOUD_CHANGE_RATE, radiusPerTick), send);
    }

    public long getSpawnTick() {
        return this.getDataPropertyInt(DATA_AREA_EFFECT_CLOUD_SPAWN_TIME);
    }

    public void setSpawnTick(long spawnTick) {
        setSpawnTick(spawnTick, true);
    }

    public void setSpawnTick(long spawnTick, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_AREA_EFFECT_CLOUD_SPAWN_TIME, (int) spawnTick), send);
    }

    private long getTicksAlive() {
        return Math.max(0, level.getCurrentTick() - this.getSpawnTick());
    }

    public int getDuration() {
        return this.getDataPropertyInt(DATA_AREA_EFFECT_CLOUD_DURATION);
    }

    public void setDuration(int duration) {
        setDuration(duration, true);
    }

    public void setDuration(int duration, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_AREA_EFFECT_CLOUD_DURATION, duration), send);
    }

    public float getRadius() {
        return this.getDataPropertyFloat(DATA_AREA_EFFECT_CLOUD_RADIUS);
    }

    public void setRadius(float radius) {
        setRadius(radius, true);
    }

    public void setRadius(float radius, boolean send) {
        this.setDataProperty(new FloatEntityData(DATA_AREA_EFFECT_CLOUD_RADIUS, radius), send);
    }

    public int getParticleId() {
        return this.getDataPropertyInt(DATA_AREA_EFFECT_CLOUD_PARTICLE_ID);
    }

    public void setParticleId(int particleId) {
        setParticleId(particleId, true);
    }

    public void setParticleId(int particleId, boolean send) {
        this.setDataProperty(new IntEntityData(DATA_AREA_EFFECT_CLOUD_PARTICLE_ID, particleId), send);
    }

    public long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(long ownerId) {
        this.ownerId = ownerId;
    }

    @Nullable
    public Entity getOwner() {
        return this.owner;
    }

    public void setOwner(@Nullable Entity owner) {
        this.owner = owner;
        if (owner != null) {
            this.ownerId = owner.getId();
        }
    }

    public void setAffectOwner(boolean shouldAffect) {
        this.affectOwner = shouldAffect;
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        this.invulnerable = true;

        this.setDataFlag(DATA_FLAGS, DATA_FLAG_FIRE_IMMUNE, true);
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_IMMOBILE, true);

        this.setPickupCount(namedTag.getInt("PickupCount"), false);

        if (this.namedTag.contains("Duration")) {
            this.setDuration(this.namedTag.getInt("Duration"), false);
        } else {
            this.setDuration(600, false);
        }

        if (this.namedTag.contains("DurationOnUse")) {
            this.durationOnUse = this.namedTag.getInt("DurationOnUse");
        } else {
            this.durationOnUse = -100;
        }

        if (this.namedTag.contains("ReapplicationDelay")) {
            this.reapplicationDelay = this.namedTag.getInt("ReapplicationDelay");
        } else {
            this.reapplicationDelay = 0;
        }

        if (this.namedTag.contains("InitialRadius")) {
            this.initialRadius = this.namedTag.getFloat("InitialRadius");
        } else {
            this.initialRadius = 3.0F;
        }

        if (this.namedTag.contains("Radius")) {
            this.setRadius(this.namedTag.getFloat("Radius"), false);
        } else {
            this.setRadius(initialRadius, false);
        }

        if (this.namedTag.contains("RadiusOnUse")) {
            this.radiusOnUse = this.namedTag.getFloat("RadiusOnUse");
        } else {
            this.radiusOnUse = -0.5F;
        }

        if (this.namedTag.contains("RadiusChangeOnPickup")) {
            this.setRadiusChangeOnPickup(this.namedTag.getFloat("RadiusChangeOnPickup"), false);
        } else {
            this.setRadiusChangeOnPickup(-0.5F, false);
        }

        if (this.namedTag.contains("RadiusPerTick")) {
            this.setRadiusPerTick(namedTag.getFloat("RadiusPerTick"), false);
        } else {
            this.setRadiusPerTick(-0.005F, false);
        }

        if (this.namedTag.contains("WaitTime")) {
            this.setWaitTime(namedTag.getInt("WaitTime"), false);
        } else {
            this.setWaitTime(10, false);
        }

        this.setPotionId(this.namedTag.getShort("PotionId"), false);

        ListTag<CompoundTag> mobEffects = this.namedTag.getList("mobEffects", CompoundTag.class);
        this.mobEffects = new Int2ObjectOpenHashMap<>(mobEffects.size());
        for (CompoundTag effectTag : mobEffects.getAll()) {
            Effect effect = Effect.getEffect(effectTag.getByte("Id"))
                    .setAmbient(effectTag.getBoolean("Ambient"))
                    .setAmplifier(effectTag.getByte("Amplifier"))
                    .setVisible(effectTag.getBoolean("DisplayOnScreenTextureAnimation"))
                    .setDuration(effectTag.getInt("Duration"));
            this.mobEffects.put(effect.getId(), effect);
        }

        this.recalculatePotionColor(false);

        int particleId = this.namedTag.getInt("ParticleId");
        if (particleId == 0) {
            particleId = Particle.TYPE_MOB_SPELL_AMBIENT;
        }
        this.setParticleId(particleId, false);

        this.setSpawnTick(this.namedTag.getLong("SpawnTick"), false);

        this.ownerId = namedTag.getLong("OwnerId");

        setMaxHealth(1);
        setHealth(1);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return false;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putInt("Duration", this.getDuration());
        this.namedTag.putInt("DurationOnUse", this.durationOnUse);
        this.namedTag.putInt("ReapplicationDelay", this.reapplicationDelay);
        this.namedTag.putFloat("InitialRadius", this.initialRadius);
        this.namedTag.putFloat("RadiusOnUse", this.radiusOnUse);
        this.namedTag.putFloat("RadiusPerTick", this.getRadiusPerTick());
        this.namedTag.putFloat("RadiusChangeOnPickup", this.getRadiusChangeOnPickup());
        this.namedTag.putInt("PickupCount", this.getPickupCount());
        this.namedTag.putInt("WaitTime", this.getWaitTime());
        this.namedTag.putShort("PotionId", this.getPotionId());
        this.namedTag.putInt("ParticleColor", this.getPotionColor());
        this.namedTag.putLong("SpawnTick", this.getSpawnTick());
        this.namedTag.putLong("OwnerId", this.ownerId);
        this.namedTag.putFloat("Radius", this.getRadius());

        ListTag<CompoundTag> list = new ListTag<>("mobEffects");
        for (Effect effect : this.mobEffects.values()) {
            list.add(new CompoundTag().putByte("Id", effect.getId())
                    .putBoolean("Ambient", effect.isAmbient())
                    .putByte("Amplifier", effect.getAmplifier())
                    .putBoolean("DisplayOnScreenTextureAnimation", effect.isVisible())
                    .putInt("Duration", effect.getDuration())
            );
        }
        this.namedTag.putList(list);

        if (this.getParticleId() != Particle.TYPE_MOB_SPELL_AMBIENT) {
            this.namedTag.putInt("ParticleId", this.getParticleId());
        } else {
            this.namedTag.remove("ParticleId");
        }
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (isClosed()) {
            return false;
        }

        int tickDiff = currentTick - lastUpdate;
        if (tickDiff <= 0 && !justCreated) {
            return true;
        }
        lastUpdate = currentTick;

        if (this.getSpawnTick() == 0) {
            this.setSpawnTick(level.getCurrentTick());
            return true;
        }

        long tickCount = this.getTicksAlive();
        if (tickCount >= this.getDuration()) {
            this.close();
            return false;
        }

        float newRadius = this.getRadius() + this.getRadiusPerTick() * tickDiff;
        if (newRadius < 0.5f) {
            this.close();
            return false;
        }
        this.setRadius(newRadius, false);

        if (tickCount % 5 != 0 || this.getSpawnTick() + this.getWaitTime() > level.getCurrentTick()) {
            return true;
        }

        if (mobEffects.isEmpty()) {
            victims.clear();
            return true;
        }

        victims.long2LongEntrySet().removeIf(entry -> tickCount >= entry.getLongValue());

        long nextTick = tickCount + reapplicationDelay;
        float radiusSquare = newRadius * newRadius;

        Entity[] entities = level.getNearbyEntities(getBoundingBox().grow(newRadius, newRadius * 0.5, newRadius), this);
        for (Entity entity : entities) {
            if (!(entity instanceof EntityLiving)) {
                continue;
            }

            if (!entity.isAlive()) {
                continue;
            }

            if (entity instanceof Player && ((Player) entity).isSpectator()) {
                continue;
            }

            long id = entity.getId();
            if (!affectOwner && (id == ownerId || entity == owner)) {
                continue;
            }

            if (radiusSquare >= distanceSquared(entity)) {
                continue;
            }

            if (victims.putIfAbsent(id, nextTick) != 0) {
                continue;
            }

            for (Effect effect : mobEffects.values()) {
                switch (effect.getId()) {
                    case Effect.NO_EFFECT:
                        if (this.getPotionId() == Potion.WATER && entity.isOnFire()) {
                            level.addLevelSoundEvent(entity.getSideVec(BlockFace.UP), LevelSoundEventPacket.SOUND_FIZZ);
                            level.addLevelEvent(entity, LevelEventPacket.EVENT_PARTICLE_FIZZ_EFFECT, 513);
                            entity.extinguish();
                        }
                        break;
                    case Effect.INSTANT_HEALTH:
                        if (!entity.canBeAffected(effect.getId())) {
                            break;
                        }
                        if (entity instanceof EntitySmite) {
                            if (owner != null) {
                                entity.attack(new EntityDamageByEntityEvent(owner, entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
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
                        } else if (owner != null) {
                            entity.attack(new EntityDamageByEntityEvent(owner, entity, EntityDamageEvent.DamageCause.MAGIC, 0.5f * (6 << effect.getAmplifier())));
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
                        entity.addEffect(effect.clone().setDuration(effect.getDuration() / 4));
                        break;
                }
            }

            if (radiusOnUse != 0) {
                newRadius += radiusOnUse;

                if (newRadius < 0.5f) {
                    close();
                    return false;
                }

                this.setRadius(newRadius, false);
            }

            if (durationOnUse != 0) {
                int newDuration = durationOnUse + this.getDuration();

                if (newDuration <= 0) {
                    close();
                    return false;
                }

                this.setDuration(newDuration, false);
            }
        }

        return true;
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return entity instanceof EntityLiving;
    }

    @Override
    public float getHeight() {
        return 0.3F + (getRadius() / 2F);
    }

    @Override
    public float getWidth() {
        return getRadius();
    }

    @Override
    public float getLength() {
        return getRadius();
    }
}
