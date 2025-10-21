package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.*;
import cn.nukkit.entity.mob.EntityDrowned;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.entity.weather.EntityWeather;
import cn.nukkit.event.entity.*;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemArmor;
import cn.nukkit.item.ItemTurtleShell;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.lang.TranslationContainer;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.math.AxisAlignedBB;
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
    protected int knockBackTime = 0;

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
    public boolean canBeAffected(int effectId) {
        return !(this instanceof EntitySmite) || effectId != Effect.REGENERATION && effectId != Effect.POISON;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        if (this.noDamageTicks > 0) {
            return false;
        } else if (this.attackTime > 0) {
            EntityDamageEvent lastCause = this.getLastDamageCause();
            if (lastCause != null && lastCause.getDamage() >= source.getDamage()) {
                return false;
            }
        }

        if (this.blockedByShield(source)) {
            return false;
        }

        if (super.attack(source)) {
            if (source instanceof EntityDamageByEntityEvent) {
                Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
                if (source instanceof EntityDamageByChildEntityEvent) {
                    damager = ((EntityDamageByChildEntityEvent) source).getChild();
                }

                // Critical hit
                if (damager instanceof Player && !damager.onGround) {
                    AnimatePacket animate = new AnimatePacket();
                    animate.action = AnimatePacket.Action.CRITICAL_HIT;
                    animate.eid = getId();

                    this.getLevel().addChunkPacket(damager.getChunkX(), damager.getChunkZ(), animate);
                    this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ATTACK_STRONG);

                    source.setDamage(source.getDamage() * 1.5f);
                }

                if (damager.isOnFire() && !(damager instanceof Player)) {
                    this.setOnFire(this.server.getDifficulty() << 1);
                }
                double deltaX = this.x - damager.x;
                double deltaZ = this.z - damager.z;
                this.knockBack(damager, source.getDamage(), deltaX, deltaZ, ((EntityDamageByEntityEvent) source).getKnockBack());
            }

            EntityEventPacket pk = new EntityEventPacket();
            pk.eid = this.getId();
            pk.event = this.getHealth() < 1 ? EntityEventPacket.DEATH_ANIMATION : EntityEventPacket.HURT_ANIMATION;
            Server.broadcastPacket(this.hasSpawned.values(), pk);

            this.attackTime = source.getAttackCooldown();
            this.scheduleUpdate();
            return true;
        } else {
            return false;
        }
    }

    protected boolean blockedByShield(EntityDamageEvent source) {
        if (!this.isBlocking()) {
            return false;
        }

        Entity damager = source instanceof EntityDamageByChildEntityEvent ? ((EntityDamageByChildEntityEvent) source).getChild() : source instanceof EntityDamageByEntityEvent ? ((EntityDamageByEntityEvent) source).getDamager() : null;
        if (damager == null || damager instanceof EntityWeather) {
            return false;
        }

        Vector3 entityPos = damager.getPosition();
        Vector3 direction = this.getDirectionVector();
        Vector3 normalizedVector = this.getPosition().subtract(entityPos).normalize();
        boolean blocked = (normalizedVector.x * direction.x) + (normalizedVector.z * direction.z) < 0.0;
        boolean knockBack = !(damager instanceof EntityProjectile);
        EntityDamageBlockedEvent event = new EntityDamageBlockedEvent(this, source, knockBack, true);
        if (!blocked || !source.canBeReducedByArmor() || damager instanceof EntityProjectile && ((EntityProjectile) damager).piercing > 0) {
            event.setCancelled();
        }

        getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        if (event.getKnockBackAttacker() && damager instanceof EntityLiving) {
            double deltaX = damager.getX() - this.getX();
            double deltaZ = damager.getZ() - this.getZ();
            ((EntityLiving) damager).attackTime = source.getAttackCooldown();
            ((EntityLiving) damager).knockBack(this, 0, deltaX, deltaZ);
        }

        onBlock(damager, source, event.getAnimation());
        return true;
    }

    protected void onBlock(Entity entity, EntityDamageEvent event, boolean animate) {
        if (animate) {
            this.getLevel().addSoundToViewers(this, Sound.ITEM_SHIELD_BLOCK);
        }
    }

    public void knockBack(Entity attacker, double damage, double x, double z) {
        this.knockBack(attacker, damage, x, z, server.knockbackParam.getHorizontal());
    }

    // 此处base保留原始签名，但实际含义已更改为水平击退力度
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
        this.knockback(attacker, damage, x, z, base, server.knockbackParam.getInterval());
    }

    public void knockback(Entity attacker, double damage, double x, double z, double base, int interval) {
        double knockbackResistance = 0d;
        if (this instanceof EntityHumanType victimPlayer) {
            var inventory = victimPlayer.getInventory();
            for (int i = 36; i <= 39; i++) {
                Item itemFast = inventory.getItemFast(i);
                if (itemFast != null && itemFast.getTier() == (ItemArmor.TIER_NETHERITE)) {
                    knockbackResistance += 0.10;
                }
            }
        }

        double f = Math.sqrt(x * x + z * z);
        if (f <= 0) return;

        x /= f;
        z /= f;

        Vector3 motion = new Vector3(this.motionX, this.motionY, this.motionZ);
        motion.x /= 2d;
        motion.y /= 2d;
        motion.z /= 2d;

        motion.x += x * base * (1 - knockbackResistance);
        motion.y += server.knockbackParam.getVertical() + (base > server.knockbackParam.getHorizontal() ? server.knockbackParam.getVerticalIncrementWhenHasEnchant() : 0);
        motion.z += z * base * (1 - knockbackResistance);

//        System.out.println("KnockBack Motion: " + motion + ", Resistance: " + knockbackResistance + ", Base: " + base);
        this.resetFallDistance();
        this.setMotion(motion);
        this.knockBackTime = interval;
    }

    @Override
    public void kill() {
        if (!this.isAlive()) {
            return;
        }
        super.kill();
        EntityDeathEvent ev = new EntityDeathEvent(this, this.getDrops());
        this.server.getPluginManager().callEvent(ev);

        this.checkTameableEntityDeath();

        if (this.level.getGameRules().getBoolean(GameRule.DO_MOB_LOOT) && this.lastDamageCause != null && DamageCause.VOID != this.lastDamageCause.getCause()) {
            if (ev.getEntity() instanceof BaseEntity) {
                BaseEntity baseEntity = (BaseEntity) ev.getEntity();
                if (baseEntity.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                    if (((EntityDamageByEntityEvent) baseEntity.getLastDamageCause()).getDamager() instanceof Player) {
                        this.getLevel().dropExpOrb(this, baseEntity.getKillExperience());

                        if (!this.dropsOnNaturalDeath()) {
                            for (cn.nukkit.item.Item item : ev.getDrops()) {
                                this.getLevel().dropItem(this, item);
                            }
                        }
                    }
                }
            }

            if (this.dropsOnNaturalDeath()) {
                for (cn.nukkit.item.Item item : ev.getDrops()) {
                    this.getLevel().dropItem(this, item);
                }
            }
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean inWater = this.isSubmerged();

        if (this instanceof Player && !this.closed) {
            Player p = (Player) this;
            boolean isBreathing = !inWater;

            PlayerInventory inv = p.getInventory();
            if (isBreathing && inv != null && inv.getHelmetFast() instanceof ItemTurtleShell) {
                turtleTicks = 200;
            } else if (turtleTicks > 0) {
                isBreathing = true;
                turtleTicks--;
            }

            if (p.isCreative() || p.isSpectator()) {
                isBreathing = true;
            }

            // HACK!
            if (p.protocol <= 282) {
                if (p.protocol <= 201) {
                    this.setDataFlagSelfOnly(DATA_FLAGS, 33, isBreathing);
                } else {
                    this.setDataFlagSelfOnly(DATA_FLAGS, 34, isBreathing);
                }
            } else {
                this.setDataFlagSelfOnly(DATA_FLAGS, DATA_FLAG_BREATHING, isBreathing);
            }
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

            if (this.level.getGameRules().getBoolean(GameRule.FIRE_DAMAGE) && !this.hasEffect(Effect.FIRE_RESISTANCE)) {
                if (this.isInsideOfLava()) {
                    this.inLavaTicks++;
                    if ((this.inLavaTicks % 10) == 0) {
                        Block lavaBlock = level.getBlock(this.getFloorX(), this.getFloorY(), this.getFloorZ());
                        if (!(lavaBlock instanceof BlockLava)) {
                            lavaBlock = lavaBlock.getLevelBlockAtLayer(1);
                        }
                        this.attack(new EntityDamageByBlockEvent(lavaBlock, this, DamageCause.LAVA, 4));
                        this.inLavaTicks = 0;
                    }
                }

                if (this.isInsideOfFire()) {
                    this.inFireTicks++;
                    if ((this.inFireTicks % 10) == 0) {
                        Block fireBlock = level.getBlock(this.getFloorX(), this.getFloorY(), this.getFloorZ());
                        int fireDamage = 1;
                        if (fireBlock instanceof BlockSoulFire) {
                            fireDamage = 2;
                        }
                        this.attack(new EntityDamageByBlockEvent(fireBlock, this, DamageCause.FIRE, fireDamage));
                        this.inFireTicks = 0;
                    }
                }
            }


            if (inWater && !this.hasEffect(Effect.WATER_BREATHING)) {
                if (this instanceof EntitySwimming || this.isDrowned || (this instanceof Player && (((Player) this).isCreative() || ((Player) this).isSpectator()))) {
                    this.setAirTicks(400);
                } else {
                    if (turtleTicks == 0) {
                        hasUpdate = true;
                        int airTicks = this.getAirTicks() - tickDiff;

                        if (airTicks <= -20) {
                            airTicks = 0;
                            if (!(this instanceof Player) || level.getGameRules().getBoolean(GameRule.DROWNING_DAMAGE)) {
                                this.attack(new EntityDamageEvent(this, DamageCause.DROWNING, 2));
                            }
                        }

                        this.setAirTicks(airTicks);
                    }
                }
            } else {
                if (this instanceof EntitySwimming) {
                    hasUpdate = true;
                    int airTicks = this.getAirTicks() - tickDiff;

                    if (airTicks <= -20) {
                        airTicks = 0;
                        this.attack(new EntityDamageEvent(this, DamageCause.SUFFOCATION, 2));
                    }

                    this.setAirTicks(airTicks);
                } else {
                    int airTicks = getAirTicks();
                    if (airTicks < 400) {
                        setAirTicks(Math.min(400, airTicks + tickDiff * 5));
                    }
                }
            }

            // Check collisions with blocks
            if (this instanceof Player) {
                if (this.age % 5 == 0) {
                    Block block = this.level.getBlock(getFloorX(), NukkitMath.floorDouble(this.y - 0.25), getFloorZ());
                    if (block instanceof BlockCactus) {
                        block.onEntityCollide(this);
                    } else if (block instanceof BlockMagma) {
                        block.onEntityCollide(this);
                        if (this.isInsideOfWater()) {
                            this.level.addParticle(new BubbleParticle(this));
                            this.setMotion(this.getMotion().add(0, -0.3, 0));
                        }
                    } /*else if (block == Block.SOUL_SAND && this.isInsideOfWater()) {
                        this.level.addParticle(new BubbleParticle(this));
                        this.setMotion(this.getMotion().add(0, 0.3, 0));
                    }*/
                }
            }

            if (this.attackTime > 0) {
                this.attackTime -= tickDiff;
                hasUpdate = true;
            }

            if (this.knockBackTime > 0) {
                this.knockBackTime -= tickDiff;
            }

            if (this.riding == null) {
                Entity[] e = level.getNearbyEntities(this.boundingBox.grow(0.20000000298023224, 0.0D, 0.20000000298023224), this);
                for (Entity entity : e) {
                    if (entity instanceof EntityRideable) {
                        this.collidingWith(entity);
                    }
                }
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
        return this.getLineOfSight(maxDistance, maxLength, (Set<Integer>) null);
    }

    public Block[] getLineOfSight(int maxDistance, int maxLength, Map<Integer, Object> transparent) {
        return this.getLineOfSight(maxDistance, maxLength, transparent.keySet());
    }

    public Block[] getLineOfSight(int maxDistance, int maxLength, Integer[] transparent) {
        return this.getLineOfSight(maxDistance, maxLength, new HashSet<>(Arrays.asList(transparent)));
    }

    /**
     * 获取实体视线范围内的方块数组。
     * Get an array of blocks within the entity's line of sight.
     *
     * @param maxDistance 视线的最大距离，超过 120 会被限制为 120 / The maximum distance of the line of sight. If it exceeds 120, it will be limited to 120.
     * @param maxLength   返回的方块列表的最大长度，若不为 0，列表长度超过该值时会移除最早添加的方块 / The maximum length of the returned block list. If it is not 0, the earliest added block will be removed when the list length exceeds this value.
     * @param transparent 透明方块 ID 的集合，若方块 ID 在该集合中，会停止遍历 / A set of transparent block IDs. If a block ID is in this set, the traversal will stop.
     * @return 视线范围内的方块数组 / An array of blocks within the line of sight.
     */
    public Block[] getLineOfSight(int maxDistance, int maxLength, Set<Integer> transparent) {
        if (maxDistance > 120) {
            maxDistance = 120;
        }

        boolean useTransparent = transparent != null && !transparent.isEmpty();

        LinkedList<Block> blocks = new LinkedList<>();

        BlockIterator itr = new BlockIterator(this.level, this.getPosition(), this.getDirectionVector(), this.getEyeHeight(), maxDistance);

        while (itr.hasNext()) {
            Block block = itr.next();
            blocks.add(block);

            if (maxLength != 0 && blocks.size() > maxLength) {
                blocks.pollFirst();
            }

            int id = block.getId();

            if (useTransparent ? !transparent.contains(id) : (id != 0)) {
                break;
            }
        }

        return blocks.toArray(Block.EMPTY_ARRAY);
    }

    public Block getTargetBlock(int maxDistance) {
        return getTargetBlock(maxDistance, new Integer[]{});
    }

    public Block getTargetBlock(int maxDistance, Map<Integer, Object> transparent) {
        return getTargetBlock(maxDistance, transparent.keySet().toArray(new Integer[0]));
    }

    public Block getTargetBlock(int maxDistance, Integer[] transparent) {
        return getTargetBlock(maxDistance, new HashSet<>(Arrays.asList(transparent)));
    }

    /**
     * 获取实体视线范围内的第一个非透明方块。
     * Get the first non-transparent block within the entity's line of sight.
     *
     * @param maxDistance 视线的最大距离，超过 120 会被限制为 120 / The maximum distance of the line of sight. If it exceeds 120, it will be limited to 120.
     * @param transparent 透明方块 ID 的集合，若方块 ID 在该集合中，会停止遍历 / A set of transparent block IDs. If a block ID is in this set, the traversal will stop.
     */
    public Block getTargetBlock(int maxDistance, Set<Integer> transparent) {
        try {
            Block[] blocks = this.getLineOfSight(maxDistance, 1, transparent);
            Block block = blocks[0];
            if (block != null) {
                if (transparent != null && !transparent.isEmpty()) {
                    if (transparent.contains(block.getId())) {
                        return block;
                    }
                } else {
                    return block;
                }
            }
        } catch (Exception ignored) {
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
        if (this instanceof EntityTameable) {
            if (!((EntityTameable) this).hasOwner()) {
                return;
            }

            if (((EntityTameable) this).getOwner() == null) {
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
            if (this.getLastDamageCause() instanceof EntityDamageByEntityEvent) {
                Entity damageEntity = ((EntityDamageByEntityEvent) this.getLastDamageCause()).getDamager();
                if (damageEntity instanceof Player) {
                    deathMessage = new TranslationContainer("death.attack.player", killedEntity, damageEntity.getName());
                } else {
                    if (damageEntity instanceof EntityWolf) {
                        ((EntityWolf) damageEntity).setAngry(false);
                    }
                    deathMessage = new TranslationContainer("death.attack.mob", killedEntity, damageEntity.getName());
                }
            }

            TextPacket tameDeathMessage = new TextPacket();
            tameDeathMessage.type = TextPacket.TYPE_TRANSLATION;
            tameDeathMessage.message = deathMessage.getText();
            tameDeathMessage.parameters = deathMessage.getParameters();
            tameDeathMessage.isLocalized = true;
            ((EntityTameable) this).getOwner().dataPacket(tameDeathMessage);
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
        AxisAlignedBB bb = this.boundingBox.clone().expand(distance, distance, distance);
        EntityHuman human = null;
        for (Entity collidingEntity : this.level.getCollidingEntities(bb)) {
            if (collidingEntity instanceof EntityHuman) {
                human = (EntityHuman) collidingEntity;
                break;
            }
        }
        return human;
    }

}
