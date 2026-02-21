package cn.nukkit.entity;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.mob.EntityFlyingMob;
import cn.nukkit.entity.mob.EntityMob;
import cn.nukkit.entity.mob.EntityRavager;
import cn.nukkit.entity.mob.EntityWolf;
import cn.nukkit.entity.passive.EntityAnimal;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemID;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.HeartParticle;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.utils.CollisionHelper;
import cn.nukkit.utils.Utils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.math3.util.FastMath;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * The base class of all entities that have an AI
 */
public abstract class BaseEntity extends EntityCreature implements EntityAgeable {

    private static final Int2ObjectMap<Float> ARMOR_POINTS = new Int2ObjectOpenHashMap<>() {
        {
            put(Item.LEATHER_CAP, Float.valueOf(1));
            put(Item.LEATHER_TUNIC, Float.valueOf(3));
            put(Item.LEATHER_PANTS, Float.valueOf(2));
            put(Item.LEATHER_BOOTS, Float.valueOf(1));
            put(Item.CHAIN_HELMET, Float.valueOf(2));
            put(Item.CHAIN_CHESTPLATE, Float.valueOf(5));
            put(Item.CHAIN_LEGGINGS, Float.valueOf(4));
            put(Item.CHAIN_BOOTS, Float.valueOf(1));
            put(Item.GOLD_HELMET, Float.valueOf(2));
            put(Item.GOLD_CHESTPLATE, Float.valueOf(5));
            put(Item.GOLD_LEGGINGS, Float.valueOf(3));
            put(Item.GOLD_BOOTS, Float.valueOf(1));
            put(Item.IRON_HELMET, Float.valueOf(2));
            put(Item.IRON_CHESTPLATE, Float.valueOf(6));
            put(Item.IRON_LEGGINGS, Float.valueOf(5));
            put(Item.IRON_BOOTS, Float.valueOf(2));
            put(Item.DIAMOND_HELMET, Float.valueOf(3));
            put(Item.DIAMOND_CHESTPLATE, Float.valueOf(8));
            put(Item.DIAMOND_LEGGINGS, Float.valueOf(6));
            put(Item.DIAMOND_BOOTS, Float.valueOf(3));
            put(Item.NETHERITE_HELMET, Float.valueOf(3));
            put(Item.NETHERITE_CHESTPLATE, Float.valueOf(8));
            put(Item.NETHERITE_LEGGINGS, Float.valueOf(6));
            put(Item.NETHERITE_BOOTS, Float.valueOf(3));
            put(Item.TURTLE_SHELL, Float.valueOf(2));
        }
    };

    /**
     * Empty inventory
     * Used to fix the problem of getting the player's hand-held item null pointer
     */
    protected static PlayerInventory EMPTY_INVENTORY;

    public int stayTime = 0;
    protected int moveTime = 0;
    protected int noRotateTicks;

    protected float moveMultiplier = 1.0f;

    protected Vector3 target = null;
    protected Entity followTarget = null;
    protected boolean lookupForTarget = true;
    protected int attackDelay = 0;
    private long leadHolder = -1L;
    protected Player lastInteract;
    private short inLoveTicks = 0;
    private short inLoveCooldown = 0;

    private boolean baby = false;
    private boolean friendly = false;
    private int lastDamageTick;
    @Setter
    @Getter
    private boolean persistent;

    public Item[] armor;

    public BaseEntity(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);

        if (EMPTY_INVENTORY == null) {
            EMPTY_INVENTORY = new PlayerInventory(null);
        }

        this.setHealth(this.getMaxHealth());
        this.setAirTicks(400);
    }

    public abstract Vector3 updateMove(int tickDiff);

    public abstract int getKillExperience();

    public boolean isFriendly() {
        return this.friendly;
    }

    @Deprecated
    public boolean isMovement() {
        return !this.isImmobile();
    }

    public boolean isKnockback() {
        return this.knockBackTime > 0;
    }

    public boolean isLeashed() {
        return this.leadHolder != -1L;
    }

    public void leash(Entity leadHolder) {
        this.leadHolder = leadHolder.getId();
        this.setDataProperty(new LongEntityData(DATA_LEAD_HOLDER_EID, this.leadHolder));
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_LEASHED, true);
    }

    public void unleash() {
        this.leadHolder = -1L;
        this.setDataProperty(new LongEntityData(DATA_LEAD_HOLDER_EID, this.leadHolder));
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_LEASHED, false);
        this.level.dropItem(this.add(0, 0.5, 0), Item.get(ItemID.LEAD));

        EntityEventPacket pk = new EntityEventPacket();
        pk.eid = this.getId();
        pk.event = EntityEventPacket.REMOVE_LEASH;
        Server.broadcastPacket(this.hasSpawned.values(), pk);
    }

    public boolean isLookupForTarget() {
        return this.lookupForTarget;
    }

    public void setLookupForTarget(boolean lookupForTarget) {
        this.lookupForTarget = lookupForTarget;
    }

    public void setFriendly(boolean bool) {
        this.friendly = bool;
    }

    public double getSpeed() {
        if (this.baby) {
            return 1.2;
        }
        return 1;
    }

    public int getAge() {
        return this.age;
    }

    public Entity getTarget() {
        return this.followTarget != null ? this.followTarget : (this.target instanceof Entity ? (Entity) this.target : null);
    }

    public void setTarget(Entity target) {
        this.followTarget = target;
        this.moveTime = 0;
        this.stayTime = 0;
        this.target = null;
    }

    @Override
    public boolean isBaby() {
        return this.baby;
    }

    @Override
    public void setBaby(boolean baby) {
        this.baby = baby;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_BABY, baby);
        if (baby) {
            this.setScale(0.5f);
            this.age = Utils.rand(-2400, -1800);
        } else {
            this.setScale(1.0f);
        }
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.persistent = this.namedTag.getBoolean("Persistent");

        if (this.namedTag.getBoolean("Immobile")) {
            this.setImmobile();
        }

        if (this.namedTag.contains("Age")) {
            this.age = this.namedTag.getShort("Age");
        }

        if (this.namedTag.getBoolean("Baby")) {
            this.setBaby(true);
        }

        if (this.namedTag.contains("InLoveTicks")) {
            this.inLoveTicks = (short) this.namedTag.getShort("InLoveTicks");
        }

        if (this.namedTag.contains("InLoveCooldown")) {
            this.inLoveCooldown = (short) this.namedTag.getShort("InLoveCooldown");
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.putBoolean("Baby", this.baby);
        this.namedTag.putBoolean("Persistent", this.isPersistent());
        this.namedTag.putBoolean("Immobile", this.isImmobile());
        this.namedTag.putShort("Age", this.age);
        this.namedTag.putShort("InLoveTicks", this.inLoveTicks);
        this.namedTag.putShort("InLoveCooldown", this.inLoveCooldown);
    }

    public boolean targetOption(EntityCreature creature, double distance) {
        if (this instanceof EntityMob) {
            if (creature instanceof Player player) {
                return !player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure()) && distance <= 100;
            }
            return creature.isAlive() && !creature.closed && distance <= 100;
        } else if (this instanceof EntityAnimal && this.isInLove()) {
            return creature instanceof BaseEntity && ((BaseEntity) creature).isInLove() && creature.isAlive() && !creature.closed && creature.getNetworkId() == this.getNetworkId() && distance <= 100;
        }
        return false;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.canDespawn() && this.age > Server.getInstance().mobDespawnTicks && !this.hasCustomName() && !(this instanceof EntityBoss)) {
            this.close();
            return true;
        }

        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (this instanceof EntityMob && this.attackDelay < 200) {
            this.attackDelay++;
        }

        if (this.moveTime > 0) {
            this.moveTime -= tickDiff;
        }

        if (this.noRotateTicks > 0) {
            this.noRotateTicks -= tickDiff;
        }

        if (this.isBaby() && this.age > 0) {
            this.setBaby(false);
        }

        if (this.isInLove()) {
            this.inLoveTicks -= tickDiff;
            if (!this.isBaby() && this.age > 0 && this.age % 20 == 0) {
                for (int i = 0; i < 3; i++) {
                    this.level.addParticle(new HeartParticle(this.add(Utils.rand(-1.0, 1.0), this.getMountedYOffset() + Utils.rand(-1.0, 1.0), Utils.rand(-1.0, 1.0))));
                }
                List<Entity> collidingEntities = CollisionHelper.getCollidingEntities(this.level, this.boundingBox.grow(0.5d, 0.5d, 0.5d));
                for (Entity entity : collidingEntities) {
                    if (this.checkSpawnBaby(entity)) {
                        break;
                    }
                }
            }
        } else if (this.isInLoveCooldown()) {
            this.inLoveCooldown -= tickDiff;
        }

        return hasUpdate;
    }

    protected boolean checkSpawnBaby(Entity entity) {
        if (!(entity instanceof BaseEntity baseEntity) || entity == this || entity.getNetworkId() != this.getNetworkId()) {
            return false;
        }
        if (!baseEntity.isInLove() || baseEntity.isBaby() || baseEntity.age <= 0) {
            return false;
        }

        Player player = baseEntity.lastInteract;
        baseEntity.lastInteract = null;

        this.setInLove(false);
        baseEntity.setInLove(false);

        this.setInLoveCooldown((short) 1200);
        baseEntity.setInLoveCooldown((short) 1200);

        this.stayTime = 60;
        baseEntity.stayTime = 60;

        int i = 0;
        for (Entity entity2 : this.chunk.getEntities().values()) {
            if (entity2.getNetworkId() == getNetworkId()) {
                i++;
                if (i > 10) {
                    return true;
                }
            }
        }

        BaseEntity baby = (BaseEntity) Entity.createEntity(this.getNetworkId(), this);
        baby.setBaby(true);
        baby.setPersistent(true); // TODO: different flag for this?
        baby.spawnToAll();
        if (baby instanceof EntityCow) {
            if (player != null) {
                player.awardAchievement("breedCow");
            }
        }
        this.level.dropExpOrb(this, Utils.rand(1, 7));
        return true;
    }


    @Override
    public boolean attack(EntityDamageEvent source) {
        if (this.isKnockback() && source instanceof EntityDamageByEntityEvent && ((EntityDamageByEntityEvent) source).getDamager() instanceof Player) {
            return false;
        }

        if (this.fireProof && (source.getCause() == EntityDamageEvent.DamageCause.FIRE || source.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK || source.getCause() == EntityDamageEvent.DamageCause.LAVA || source.getCause() == EntityDamageEvent.DamageCause.MAGMA)) {
            return false;
        }

        if (source instanceof EntityDamageByEntityEvent) {
            if (this instanceof EntityRavager && Utils.rand()) {
                ((EntityDamageByEntityEvent) source).setKnockBack(0f);
            } else {
                ((EntityDamageByEntityEvent) source).setKnockBack(0.25f);
            }
        }

        super.attack(source);

        if (!source.isCancelled()) {
            this.target = null;
            this.stayTime = 0;
            this.lastDamageTick = server.getTick();
        }

        return true;
    }

    @Override
    public boolean move(double dx, double dy, double dz) {
        if (dy < -10 || dy > 10) {
            if (!(this instanceof EntityFlyingMob)) {
                this.kill();
            }
            return false;
        }

        if (this.leadHolder != -1L) {
            Entity leadHolder = level.getEntity(this.leadHolder);

            if (leadHolder == null) {
                this.unleash();
            } else {
                double distance = this.distanceSquared(leadHolder);

                if (distance > 100) {
                    this.unleash();
                } else if (distance > 49) {
                    Vector3 toTarget = leadHolder.subtract(this).normalize();
                    toTarget.x *= 0.5;
                    toTarget.y *= 0.5;
                    toTarget.z *= 0.5;

                    this.setMotion(toTarget);

                    dx = toTarget.x;
                    dy = toTarget.y;
                    dz = toTarget.z;
                }
            }
        }

        if (dx == 0 && dz == 0 && dy == 0) {
            return false;
        }

        this.blocksAround = null;

        double movX = dx * moveMultiplier;
        double movY = dy;
        double movZ = dz * moveMultiplier;

        List<AxisAlignedBB> collisions = CollisionHelper.getCollisionCubes(
                this.level,
                this,
                this.boundingBox.addCoord(dx, dy, dz),
                false,
                false
        );

        dx = collisions.stream().reduce(dx, (motion, bb) -> bb.calculateXOffset(this.boundingBox, motion), Double::min);
        dz = collisions.stream().reduce(dz, (motion, bb) -> bb.calculateZOffset(this.boundingBox, motion), Double::min);
        dy = collisions.stream().reduce(dy, (motion, bb) -> bb.calculateYOffset(this.boundingBox, motion), Double::min);

        this.boundingBox.offset(dx, dy, dz);

        this.setComponents(this.x + dx, this.y + dy, this.z + dz);
        this.checkChunks();

        this.checkGroundState(movX, movY, movZ, dx, dy, dz);
        this.updateFallState(this.onGround);

        return true;
    }

    @Override
    protected boolean applyNameTag(Player player, Item nameTag) {
        String name = nameTag.getCustomName();

        if (!name.isEmpty()) {
            this.namedTag.putString("CustomName", name);
            this.namedTag.putBoolean("CustomNameVisible", true);
            this.setNameTag(name);
            this.setNameTagVisible(true);
            return true; // onInteract: true = decrease count
        }

        return false;
    }

    public void setInLove() {
        this.setInLove(true);
    }

    public void setInLove(boolean inLove) {
        if (inLove) {
            if (!this.isBaby() && (this instanceof EntityAnimal || this instanceof EntityWolf)) {
                this.inLoveTicks = 600;
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_INLOVE, true);
            }
            this.setPersistent(true); // TODO: different flag for this?
        } else {
            this.inLoveTicks = 0;
            this.setDataFlag(DATA_FLAGS, DATA_FLAG_INLOVE, false);
        }
    }

    public boolean isInLove() {
        return inLoveTicks > 0;
    }

    public void setInLoveCooldown(short inLoveCooldown) {
        this.inLoveCooldown = inLoveCooldown;
    }

    public boolean isInLoveCooldown() {
        return this.inLoveCooldown > 0;
    }

    /**
     * Check if the entity can swim in the block
     *
     * @param block block id
     * @return can swim
     */
    protected boolean canSwimIn(int block) {
        return block == BlockID.WATER || block == BlockID.STILL_WATER;
    }

    /**
     * Get a random set of armor
     *
     * @return armor items
     */
    public Item[] getRandomArmor() {
        Item[] slots = new Item[4];
        Item helmet = Item.get(0);
        Item chestplate = Item.get(0);
        Item leggings = Item.get(0);
        Item boots = Item.get(0);

        switch (Utils.rand(1, 5)) {
            case 1 -> {
                if (Utils.rand(1, 100) < 39 && Utils.rand(0, 1) == 0) {
                    helmet = Item.get(Item.LEATHER_HELMET, Utils.rand(30, 48), 1);
                }
            }
            case 2 -> {
                if (Utils.rand(1, 100) < 50 && Utils.rand(0, 1) == 0) {
                    helmet = Item.get(Item.GOLD_HELMET, Utils.rand(40, 70), 1);
                }
            }
            case 3 -> {
                if (Utils.rand(1, 100) < 14 && Utils.rand(0, 1) == 0) {
                    helmet = Item.get(Item.CHAIN_HELMET, Utils.rand(100, 160), 1);
                }
            }
            case 4 -> {
                if (Utils.rand(1, 100) < 3 && Utils.rand(0, 1) == 0) {
                    helmet = Item.get(Item.IRON_HELMET, Utils.rand(100, 160), 1);
                }
            }
            case 5 -> {
                if (Utils.rand(1, 100) == 100 && Utils.rand(0, 1) == 0) {
                    helmet = Item.get(Item.DIAMOND_HELMET, Utils.rand(190, 256), 1);
                }
            }
        }

        slots[0] = helmet;

        if (Utils.rand(1, 4) != 1) {
            switch (Utils.rand(1, 5)) {
                case 1 -> {
                    if (Utils.rand(1, 100) < 39 && Utils.rand(0, 1) == 0) {
                        chestplate = Item.get(Item.LEATHER_CHESTPLATE, Utils.rand(60, 73), 1);
                    }
                }
                case 2 -> {
                    if (Utils.rand(1, 100) < 50 && Utils.rand(0, 1) == 0) {
                        chestplate = Item.get(Item.GOLD_CHESTPLATE, Utils.rand(65, 105), 1);
                    }
                }
                case 3 -> {
                    if (Utils.rand(1, 100) < 14 && Utils.rand(0, 1) == 0) {
                        chestplate = Item.get(Item.CHAIN_CHESTPLATE, Utils.rand(170, 233), 1);
                    }
                }
                case 4 -> {
                    if (Utils.rand(1, 100) < 3 && Utils.rand(0, 1) == 0) {
                        chestplate = Item.get(Item.IRON_CHESTPLATE, Utils.rand(170, 233), 1);
                    }
                }
                case 5 -> {
                    if (Utils.rand(1, 100) == 100 && Utils.rand(0, 1) == 0) {
                        chestplate = Item.get(Item.DIAMOND_CHESTPLATE, Utils.rand(421, 521), 1);
                    }
                }
            }
        }

        slots[1] = chestplate;

        if (Utils.rand(1, 2) == 2) {
            switch (Utils.rand(1, 5)) {
                case 1 -> {
                    if (Utils.rand(1, 100) < 39 && Utils.rand(0, 1) == 0) {
                        leggings = Item.get(Item.LEATHER_LEGGINGS, Utils.rand(35, 68), 1);
                    }
                }
                case 2 -> {
                    if (Utils.rand(1, 100) < 50 && Utils.rand(0, 1) == 0) {
                        leggings = Item.get(Item.GOLD_LEGGINGS, Utils.rand(50, 98), 1);
                    }
                }
                case 3 -> {
                    if (Utils.rand(1, 100) < 14 && Utils.rand(0, 1) == 0) {
                        leggings = Item.get(Item.CHAIN_LEGGINGS, Utils.rand(170, 218), 1);
                    }
                }
                case 4 -> {
                    if (Utils.rand(1, 100) < 3 && Utils.rand(0, 1) == 0) {
                        leggings = Item.get(Item.IRON_LEGGINGS, Utils.rand(170, 218), 1);
                    }
                }
                case 5 -> {
                    if (Utils.rand(1, 100) == 100 && Utils.rand(0, 1) == 0) {
                        leggings = Item.get(Item.DIAMOND_LEGGINGS, Utils.rand(388, 488), 1);
                    }
                }
            }
        }

        slots[2] = leggings;

        if (Utils.rand(1, 5) < 3) {
            switch (Utils.rand(1, 5)) {
                case 1 -> {
                    if (Utils.rand(1, 100) < 39 && Utils.rand(0, 1) == 0) {
                        boots = Item.get(Item.LEATHER_BOOTS, Utils.rand(35, 58), 1);
                    }
                }
                case 2 -> {
                    if (Utils.rand(1, 100) < 50 && Utils.rand(0, 1) == 0) {
                        boots = Item.get(Item.GOLD_BOOTS, Utils.rand(50, 86), 1);
                    }
                }
                case 3 -> {
                    if (Utils.rand(1, 100) < 14 && Utils.rand(0, 1) == 0) {
                        boots = Item.get(Item.CHAIN_BOOTS, Utils.rand(100, 188), 1);
                    }
                }
                case 4 -> {
                    if (Utils.rand(1, 100) < 3 && Utils.rand(0, 1) == 0) {
                        boots = Item.get(Item.IRON_BOOTS, Utils.rand(100, 188), 1);
                    }
                }
                case 5 -> {
                    if (Utils.rand(1, 100) == 100 && Utils.rand(0, 1) == 0) {
                        boots = Item.get(Item.DIAMOND_BOOTS, Utils.rand(350, 428), 1);
                    }
                }
            }
        }

        slots[3] = boots;

        return slots;
    }

    /**
     * Increases mob's health according to armor the mob has (temporary workaround until armor damage modifiers are implemented for mobs)
     */
    protected void addArmorExtraHealth() {
        if (this.armor != null && this.armor.length == 4) {
            switch (armor[0].getId()) {
                case Item.LEATHER_CAP -> this.addHealth(1);
                case Item.GOLD_HELMET, Item.CHAIN_HELMET, Item.IRON_HELMET -> this.addHealth(2);
                case Item.DIAMOND_HELMET -> this.addHealth(3);
            }
            switch (armor[1].getId()) {
                case Item.LEATHER_TUNIC -> this.addHealth(2);
                case Item.GOLD_CHESTPLATE, Item.CHAIN_CHESTPLATE, Item.IRON_CHESTPLATE -> this.addHealth(3);
                case Item.DIAMOND_CHESTPLATE -> this.addHealth(4);
            }
            switch (armor[2].getId()) {
                case Item.LEATHER_PANTS -> this.addHealth(1);
                case Item.GOLD_LEGGINGS, Item.CHAIN_LEGGINGS, Item.IRON_LEGGINGS -> this.addHealth(2);
                case Item.DIAMOND_LEGGINGS -> this.addHealth(3);
            }
            switch (armor[3].getId()) {
                case Item.LEATHER_BOOTS -> this.addHealth(1);
                case Item.GOLD_BOOTS, Item.CHAIN_BOOTS, Item.IRON_BOOTS -> this.addHealth(2);
                case Item.DIAMOND_BOOTS -> this.addHealth(3);
            }
        }
    }

    /**
     * Increase the maximum health and health. Used for armored mobs.
     *
     * @param health amount of health to add
     */
    private void addHealth(int health) {
        boolean wasMaxHealth = this.getHealth() == this.getMaxHealth();
        this.setMaxHealth(this.getMaxHealth() + health);
        if (wasMaxHealth) {
            this.setHealth(this.getHealth() + health);
        }
    }

    /**
     * Check whether a mob is allowed to despawn
     *
     * @return can despawn
     */
    public boolean canDespawn() {
        return this.y < -128 || (this.server.despawnMobs &&
                !this.persistent && this.age % 100 == 0 && this.riding == null && this.inLoveTicks <= 0 && this.inLoveCooldown <= 0 &&
                !this.isLeashed() && !this.hasCustomName() && server.getTick() - this.lastDamageTick > 600 && // no damage in 30 seconds
                !this.isInTickingRange(9216) // 96 blocks
        );
    }


    protected boolean canSetTemporalTarget() {
        return this.followTarget == null;
    }

    /**
     * How near a player the mob should get before it starts attacking
     *
     * @return distance
     */
    public int nearbyDistanceMultiplier() {
        return 1;
    }

    @Override
    protected void checkGroundState(double movX, double movY, double movZ, double dx, double dy, double dz) {
        if (onGround && movX == 0 && movY == 0 && movZ == 0 && dx == 0 && dy == 0 && dz == 0) {
            return;
        }
        this.isCollidedVertically = movY != dy;
        this.isCollidedHorizontally = (movX != dx || movZ != dz);
        this.isCollided = (this.isCollidedHorizontally || this.isCollidedVertically);
        this.onGround = (movY != dy && movY < 0);
    }

    public static void setProjectileMotion(EntityProjectile projectile, double pitch, double yawR, double pitchR, double speed) {
        double verticalMultiplier = Math.cos(pitchR);
        double x = verticalMultiplier * Math.sin(-yawR);
        double z = verticalMultiplier * Math.cos(yawR);
        double y = Math.sin(-(FastMath.toRadians(pitch)));
        double magnitude = Math.sqrt(x * x + y * y + z * z);
        if (magnitude > 0) {
            x += (x * (speed - magnitude)) / magnitude;
            y += (y * (speed - magnitude)) / magnitude;
            z += (z * (speed - magnitude)) / magnitude;
        }
        ThreadLocalRandom rand = ThreadLocalRandom.current();
        x += rand.nextGaussian() * 0.007499999832361937 * 6;
        y += rand.nextGaussian() * 0.007499999832361937 * 6;
        z += rand.nextGaussian() * 0.007499999832361937 * 6;
        projectile.setMotion(new Vector3(x, y, z));
    }

    public boolean canTarget(Entity entity) {
        return entity instanceof Player && entity.canBeFollowed();
    }

    @Override
    protected void checkBlockCollision() {
        for (Block block : getCollisionHelper().getCollisionBlocks()) {
            block.onEntityCollide(this);
        }

        // TODO: portals
    }

    /**
     * Get armor defense points for item
     *
     * @param item item id
     * @return defense points
     */
    protected float getArmorPoints(int item) {
        Float points = ARMOR_POINTS.get(item);
        if (points == null) {
            return 0;
        }
        return points;
    }

    /**
     * Play attack animation to viewers
     */
    protected void playAttack() {
        EntityEventPacket pk = new EntityEventPacket();
        pk.eid = this.getId();
        pk.event = EntityEventPacket.ARM_SWING;
        Server.broadcastPacket(this.getViewers().values(), pk);
    }

    /**
     * 满足攻击目标条件
     *
     * @return 是否满足
     */
    public boolean isMeetAttackConditions(Vector3 target) {
        return this.getServer().getMobAiEnabled() && target instanceof Entity;
    }

    /**
     * 获取攻击目标
     *
     * @param target 目标
     * @return 有可能为空指针
     */
    protected Entity getAttackTarget(Vector3 target) {
        if (isMeetAttackConditions(target)) {
            Entity entity = (Entity) target;
            if (!entity.isClosed() && target != this.followTarget) {
                return entity;
            }
        }
        return null;
    }

    protected boolean isInTickingRange() {
        return this.isInTickingRange(6400); // 80 blocks
    }

    protected boolean isInTickingRange(double rangeSquared) {
        for (Player player : this.level.getPlayers().values()) {
            // Ignore y so mobs won't stop falling into void unless movement behavior is tweaked for this
            if (Math.pow(player.x - this.x, 2.0) + Math.pow(player.z - this.z, 2.0) < rangeSquared) {
                return true;
            }
        }
        return false;
    }
}
