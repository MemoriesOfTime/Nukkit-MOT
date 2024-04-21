package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockCactus;
import cn.nukkit.block.BlockMagma;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.weather.EntityWeather;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemTurtleShell;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.network.protocol.AnimatePacket;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.TextPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.BlockIterator;

import java.util.*;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EntityLiving extends Entity implements EntityDamageable {

    public EntityLiving(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public float getGravity() {
        return 0.08f;
    }

    @Override
    protected float getDrag() {
        return 0.02f;
    }

    protected int attackTime = 0;

    protected float movementSpeed = 0.1f;

    protected int turtleTicks = 0;

    private boolean blocking = false;

    protected final boolean isDrowned = this instanceof EntityDrowned;

    @Override
    protected void initEntity() {
        super.initEntity();

        if (this.namedTag.contains("HealF")) {
            this.namedTag.putFloat("Health", this.namedTag.getShort("HealF"));
            this.namedTag.remove("HealF");
        }

        if (!this.namedTag.contains("Health") || !(this.namedTag.get("Health") instanceof FloatTag)) {
            this.namedTag.putFloat("Health", this.getMaxHealth());
        }

        this.health = this.namedTag.getFloat("Health");
    }

    @Override
    public void setHealth(float health) {
        boolean wasAlive = this.isAlive();
        super.setHealth(health);
        if (this.isAlive() && !wasAlive) {
            EntityEventPacket pk = new EntityEventPacket();
            pk.eid = this.getId();
            pk.event = EntityEventPacket.RESPAWN;
            Server.broadcastPacket(this.hasSpawned.values(), pk);
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putFloat("Health", this.getHealth());
    }

    public boolean hasLineOfSight(Entity entity) {
        return true;
    }

    public void collidingWith(Entity ent) {
        ent.applyEntityCollision(this);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return this.noDamageTicks <= 0 && this.attackTime <= 0 && !this.blockedByShield(source) &&
                Optional.of(super.attack(source))
                        .filter(successful -> successful)
                        .map(success -> {
                            if (source instanceof EntityDamageByEntityEvent event) {
                                Entity damageEntity = event instanceof EntityDamageByChildEntityEvent childDamageEvent ? childDamageEvent.getChild() : event.getDamager();

                                // Critical hit
                                if (damageEntity instanceof Player && !damageEntity.onGround) {
                                    AnimatePacket animate = new AnimatePacket();
                                    animate.action = AnimatePacket.Action.CRITICAL_HIT;
                                    animate.eid = getId();
                                    this.getLevel().addChunkPacket(damageEntity.getChunkX(), damageEntity.getChunkZ(), animate);
                                    this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ATTACK_STRONG);
                                    source.setDamage(source.getDamage() * 1.5f);
                                }

                                if (damageEntity.isOnFire() && !(damageEntity instanceof Player)) {
                                    this.setOnFire(this.server.getDifficulty() << 1);
                                }

                                double deltaX = this.x - damageEntity.x;
                                double deltaZ = this.z - damageEntity.z;
                                this.knockBack(deltaX, deltaZ, event.getKnockBack());
                            }

                            EntityEventPacket pk = new EntityEventPacket();
                            pk.eid = this.getId();
                            pk.event = this.getHealth() < 1 ? EntityEventPacket.DEATH_ANIMATION : EntityEventPacket.HURT_ANIMATION;
                            Server.broadcastPacket(this.hasSpawned.values(), pk);

                            this.attackTime = source.getAttackCooldown();
                            this.scheduleUpdate();
                            return true;
                        })
                        .orElse(false);
    }

    protected boolean blockedByShield(EntityDamageEvent source) {
        return this.isBlocking() && Optional.ofNullable(source)
                .map(src -> src instanceof EntityDamageByChildEntityEvent ? ((EntityDamageByChildEntityEvent) src).getChild()
                        : src instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) src).getDamager() : null)
                .filter(damageEntity -> !(damageEntity instanceof EntityWeather))
                .map(damageEntity -> {
                    Vector3 direction = this.getDirectionVector();
                    Vector3 normalizedVector = this.getPosition().subtract(damageEntity.getPosition()).normalize();
                    boolean blocked = (normalizedVector.x * direction.x) + (normalizedVector.z * direction.z) < 0.0;
                    boolean knockBack = !(damageEntity instanceof EntityProjectile);
                    EntityDamageBlockedEvent event = new EntityDamageBlockedEvent(this, source, knockBack, true);
                    if (!blocked || !source.canBeReducedByArmor() || (damageEntity instanceof EntityProjectile projectile && projectile.piercing > 0)) {
                        event.setCancelled();
                    }

                    getServer().getPluginManager().callEvent(event);
                    if (!event.isCancelled() && event.getKnockBackAttacker() && damageEntity instanceof EntityLiving living) {
                        double deltaX = damageEntity.getX() - this.getX();
                        double deltaZ = damageEntity.getZ() - this.getZ();
                        living.attackTime = source.getAttackCooldown();
                        living.knockBack(deltaX, deltaZ);
                    }

                    onBlock(damageEntity, source, event.getAnimation());
                    return true;
                })
                .orElse(false);
    }

    protected void onBlock(Entity entity, EntityDamageEvent event, boolean animate) {
        if (animate) {
            this.getLevel().addSoundToViewers(this, Sound.ITEM_SHIELD_BLOCK);
        }
    }

    public void knockBack(double x, double z) {
        this.knockBack(x, z, 0.3);
    }

    public void knockBack(double x, double z, double base) {
        Vector3 motion = new Vector3(x, z, 0).normalize().multiply(base).add(0, base, 0);
        motion.y = Math.min(motion.y, base);  // Ensure y-component doesn't exceed 'base'
        this.setMotion(motion);
        this.resetFallDistance();
    }

    @Override
    public void kill() {
        if (this.isAlive()) {
            super.kill();

            EntityDeathEvent source = new EntityDeathEvent(this, this.getDrops());
            this.server.getPluginManager().callEvent(source);
            this.checkTameableEntityDeath();

            boolean doMobLoot = this.level.getGameRules().getBoolean(GameRule.DO_MOB_LOOT);
            DamageCause lastDamageCause = this.lastDamageCause != null ? this.lastDamageCause.getCause() : null;
            if (doMobLoot && lastDamageCause != null && DamageCause.VOID != lastDamageCause) {
                if (source.getEntity() instanceof BaseEntity baseEntity) {
                    EntityDamageEvent lastDamageCauseEvent = baseEntity.getLastDamageCause();
                    if (lastDamageCauseEvent instanceof EntityDamageByEntityEvent event && event.getDamager() instanceof Player) {
                        this.getLevel().dropExpOrb(this, baseEntity.getKillExperience());
                        if (!this.dropsOnNaturalDeath()) {
                            Arrays.stream(source.getDrops()).forEach(item -> this.getLevel().dropItem(this, item));
                        }
                    }
                }

                if (this.dropsOnNaturalDeath()) {
                    Arrays.stream(source.getDrops()).forEach(item -> this.getLevel().dropItem(this, item));
                }
            }
        }
    }

    @Override
    public boolean entityBaseTick() {
        return this.entityBaseTick(1);
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean inWater = this.isSubmerged();

        if (this instanceof Player player && !this.closed) {
            boolean isBreathing = !inWater;

            PlayerInventory inv = player.getInventory();
            if (isBreathing && inv != null && inv.getHelmetFast() instanceof ItemTurtleShell) {
                turtleTicks = 200;
            } else if (turtleTicks > 0) {
                isBreathing = true;
                turtleTicks--;
            }

            if (player.isCreative() || player.isSpectator()) {
                isBreathing = true;
            }

            this.setDataFlagSelfOnly(DATA_FLAGS, player.protocol <= 282 ? (player.protocol <= 201 ? 33 : 34) : DATA_FLAG_BREATHING, isBreathing);
        }

        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (this.isAlive()) {
            if (this.isInsideOfSolid()) {
                hasUpdate = true;
                this.attack(new EntityDamageEvent(this, DamageCause.SUFFOCATION, 1));
            }

            if (this.isOnLadder() || this.hasEffect(Effect.LEVITATION) || this.hasEffect(Effect.SLOW_FALLING)) {
                this.resetFallDistance();
            }

            if (inWater && !this.hasEffect(Effect.WATER_BREATHING)) {
                if (this instanceof EntitySwimming || this.isDrowned || (this instanceof Player && (((Player) this).isCreative() || ((Player) this).isSpectator()))) {
                    this.setAirTicks(400);
                } else if (turtleTicks == 0) {
                    hasUpdate = true;
                    int airTicks = this.getAirTicks() - tickDiff;
                    airTicks = (airTicks <= -20) ? 0 : airTicks;
                    this.setAirTicks(airTicks);
                    if (airTicks == 0 && (!(this instanceof Player) || level.getGameRules().getBoolean(GameRule.DROWNING_DAMAGE))) {
                        this.attack(new EntityDamageEvent(this, DamageCause.DROWNING, 2));
                    }
                }
            } else {
                if (this instanceof EntitySwimming) {
                    hasUpdate = true;
                    int airTicks = this.getAirTicks() - tickDiff;
                    airTicks = (airTicks <= -20) ? 0 : airTicks;
                    this.setAirTicks(airTicks);
                    if (airTicks == 0) {
                        this.attack(new EntityDamageEvent(this, DamageCause.SUFFOCATION, 2));
                    }
                } else if (getAirTicks() < 400) {
                    setAirTicks(Math.min(400, getAirTicks() + tickDiff * 5));
                }
            }

            if (this instanceof Player && this.age % 5 == 0) {
                Block block = this.level.getBlock(getFloorX(), NukkitMath.floorDouble(this.y - 0.25), getFloorZ());
                if (block instanceof BlockCactus || block instanceof BlockMagma) {
                    block.onEntityCollide(this);
                    if (block instanceof BlockMagma && this.isInsideOfWater()) {
                        this.level.addParticle(new BubbleParticle(this));
                        this.setMotion(this.getMotion().add(0, -0.3, 0));
                    }
                }
            }

            if (this.attackTime > 0) {
                this.attackTime -= tickDiff;
                hasUpdate = true;
            }

            if (this.riding == null) {
                Arrays.stream(level.getNearbyEntities(this.boundingBox.grow(0.2, 0.0, 0.2), this))
                        .filter(entity -> entity instanceof EntityRideable)
                        .forEach(this::collidingWith);
            }
        }

        return hasUpdate;
    }

    public Item[] getDrops() {
        return Item.EMPTY_ARRAY;
    }

    public Block[] getLineOfSight(int maxDistance) {
        return this.getLineOfSight(maxDistance, 0);
    }

    public Block[] getLineOfSight(int maxDistance, int maxLength) {
        return this.getLineOfSight(maxDistance, maxLength, new Integer[]{});
    }

    public Block[] getLineOfSight(int maxDistance, int maxLength, Map<Integer, Object> transparent) {
        return this.getLineOfSight(maxDistance, maxLength, transparent.keySet().toArray(new Integer[0]));
    }

    public Block[] getLineOfSight(int maxDistance, int maxLength, Integer[] transparent) {
        maxDistance = Math.min(maxDistance, 120);
        transparent = (transparent != null && transparent.length == 0) ? null : transparent;

        List<Block> blocks = new ArrayList<>();
        BlockIterator itr = new BlockIterator(this.level, this.getPosition(), this.getDirectionVector(), this.getEyeHeight(), maxDistance);

        while (itr.hasNext() && (maxLength == 0 || blocks.size() <= maxLength)) {
            Block block = itr.next();
            if ((transparent == null && block.getId() != 0) || (transparent != null && Arrays.binarySearch(transparent, block.getId()) < 0)) {
                break;
            }
            blocks.add(block);
            if (maxLength != 0) blocks.remove(0);
        }

        return blocks.toArray(new Block[0]);
    }

    public Block getTargetBlock(int maxDistance) {
        return getTargetBlock(maxDistance, new Integer[]{});
    }

    public Block getTargetBlock(int maxDistance, Map<Integer, Object> transparent) {
        return getTargetBlock(maxDistance, transparent.keySet().toArray(new Integer[0]));
    }

    public Block getTargetBlock(int maxDistance, Integer[] transparent) {
        Block[] blocks = this.getLineOfSight(maxDistance, 1, transparent);
        if (blocks.length > 0 && (transparent == null || transparent.length == 0 || Arrays.binarySearch(transparent, blocks[0].getId()) < 0)) {
            return blocks[0];
        }

        return null;
    }

    public void setMovementSpeed(float speed) {
        this.movementSpeed = speed;
    }

    public float getMovementSpeed() {
        return this.movementSpeed;
    }

    public int getAirTicks() {
        return this.airTicks;
    }

    public void setAirTicks(int ticks) {
        this.airTicks = ticks;
    }

    public boolean isBlocking() {
        return this.blocking;
    }

    public void setBlocking(boolean value) {
        this.blocking = value;
        this.setDataFlag(DATA_FLAGS_EXTENDED, DATA_FLAG_BLOCKING, value);
    }

    public boolean dropsOnNaturalDeath() {
        return true;
    }

    public boolean isSpinAttack() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_SPIN_ATTACK);
    }

    public void setSpinAttack(boolean value) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SPIN_ATTACK, value);
    }

    private void checkTameableEntityDeath() {
        if (this instanceof EntityTameable entity) {
            if (!entity.hasOwner()) {
                return;
            }

            if (entity.getOwner() == null) {
                return;
            }

            // TODO: More detailed death messages
            String killedEntity;
            if (this instanceof EntityWolf) {
                killedEntity = "%entity.wolf.name";
            } else {
                killedEntity = this.getName();
            }

            TranslationContainer deathMessage = new TranslationContainer("death.attack.generic", killedEntity);
            if (this.getLastDamageCause() instanceof EntityDamageByEntityEvent event) {
                Entity damageEntity = event.getDamager();
                if (damageEntity instanceof Player) {
                    deathMessage = new TranslationContainer("death.attack.player", killedEntity, damageEntity.getName());
                } else {
                    if (damageEntity instanceof EntityWolf wolf)
                        wolf.setAngry(false);
                    deathMessage = new TranslationContainer("death.attack.mob", killedEntity, damageEntity.getName());
                }
            }

            TextPacket tameDeathMessage = new TextPacket();
            tameDeathMessage.type = TextPacket.TYPE_TRANSLATION;
            tameDeathMessage.message = deathMessage.getText();
            tameDeathMessage.parameters = deathMessage.getParameters();
            tameDeathMessage.isLocalized = true;
            entity.getOwner().dataPacket(tameDeathMessage);
        }
    }

    public void lookAt(Vector3 target) {
        double dx = this.x - target.x;
        double dy = this.y - target.y;
        double dz = this.z - target.z;
        double yaw = Math.asin(dx / Math.sqrt(dx * dx + dz * dz)) / Math.PI * 180.0d;
        double asin = Math.asin(dy / Math.sqrt(dx * dx + dz * dz + dy * dy)) / Math.PI * 180.0d;
        long pitch = Math.round(asin);
        if (dz > 0.0d) {
            yaw = -yaw + 180.0d;
        }
        this.setRotation(yaw, pitch);
    }

    public EntityHuman getNearbyHuman() {
        return this.getNearbyHuman(2.5);
    }

    public EntityHuman getNearbyHuman(double distance) {
        return Arrays.stream(this.level.getCollidingEntities(this.boundingBox.clone().expand(distance, distance, distance)))
                .filter(EntityHuman.class::isInstance)
                .map(EntityHuman.class::cast)
                .findFirst()
                .orElse(null);
    }

}
