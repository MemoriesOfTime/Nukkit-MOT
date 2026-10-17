package cn.nukkit.entity.projectile;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.weather.LightningStrikeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.LevelSoundEventPacket;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Created by PetteriM1
 */
public class EntityThrownTrident extends EntitySlenderProjectile {

    public static final int NETWORK_ID = 73;
    private static final String TAG_PICKUP = "pickup";
    private static final String TAG_TRIDENT = "Trident";
    private static final String TAG_FAVORED_SLOT = "favoredSlot";
    private static final String TAG_CREATIVE = "isCreative";
    private static final String TAG_PLAYER = "player";
    private static final String NAME_TRIDENT = "Trident";
    private static final Vector3 defaultCollisionPos = new Vector3(0, 0, 0);
    private static final BlockVector3 defaultStuckToBlockPos = new BlockVector3(0, 0, 0);
    protected Item trident;
    // Default Values
    protected float gravity = 0.04f;
    protected float drag = 0.01f;
    protected int pickupMode;
    private Vector3 collisionPos;
    private BlockVector3 stuckToBlockPos;
    private int favoredSlot;
    private boolean player;
    // Enchantment
    private int loyaltyLevel;
    private boolean hasChanneling;
    private int riptideLevel;
    private int impalingLevel;
    private boolean didHit;
    private int loyaltyReturnDelay = -1;

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
        return 0.25f;
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

    public EntityThrownTrident(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityThrownTrident(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    protected void initEntity() {
        super.initEntity();

        this.pickupMode = namedTag.contains(TAG_PICKUP) ? namedTag.getByte(TAG_PICKUP) : PICKUP_ANY;
        this.favoredSlot = namedTag.contains(TAG_FAVORED_SLOT) ? namedTag.getInt(TAG_FAVORED_SLOT) : -1;
        this.player = !namedTag.contains(TAG_PLAYER) || namedTag.getBoolean(TAG_PLAYER);

        if (namedTag.contains(TAG_CREATIVE)) {
            if (pickupMode == PICKUP_ANY && namedTag.getBoolean(TAG_CREATIVE)) {
                pickupMode = PICKUP_CREATIVE;
            }
            namedTag.remove(TAG_CREATIVE);
        }

        if (namedTag.contains(TAG_TRIDENT)) {
            this.trident = NBTIO.getItemHelper(namedTag.getCompound(TAG_TRIDENT));
            this.loyaltyLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_LOYALTY);
            this.hasChanneling = this.trident.hasEnchantment(Enchantment.ID_TRIDENT_CHANNELING);
            this.riptideLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_RIPTIDE);
            this.impalingLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_IMPALING);
        } else {
            this.trident = Item.get(0);
            this.loyaltyLevel = 0;
            this.hasChanneling = false;
            this.riptideLevel = 0;
            this.impalingLevel = 0;
        }

        if (namedTag.contains("CollisionPos")) {
            ListTag<?> collisionPosList = this.namedTag.getList("CollisionPos");
            collisionPos = new Vector3(
                    ((NumberTag<?>) collisionPosList.get(0)).getData().doubleValue(),
                    ((NumberTag<?>) collisionPosList.get(1)).getData().doubleValue(),
                    ((NumberTag<?>) collisionPosList.get(2)).getData().doubleValue()
            );
        } else {
            collisionPos = defaultCollisionPos.clone();
        }

        if (namedTag.contains("StuckToBlockPos")) {
            ListTag<IntTag> stuckToBlockPosList = this.namedTag.getList("StuckToBlockPos", IntTag.class);
            stuckToBlockPos = new BlockVector3(stuckToBlockPosList.get(0).data, stuckToBlockPosList.get(1).data, stuckToBlockPosList.get(2).data);
        } else {
            stuckToBlockPos = defaultStuckToBlockPos.clone();
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        this.namedTag.put(TAG_TRIDENT, NBTIO.putItemHelper(this.trident));
        this.namedTag.putByte(TAG_PICKUP, this.pickupMode);
        this.namedTag.putList(new ListTag<DoubleTag>("CollisionPos")
                .add(new DoubleTag("0", this.collisionPos.x))
                .add(new DoubleTag("1", this.collisionPos.y))
                .add(new DoubleTag("2", this.collisionPos.z))
        );
        this.namedTag.putList(new ListTag<IntTag>("StuckToBlockPos")
                .add(new IntTag("0", this.stuckToBlockPos.x))
                .add(new IntTag("1", this.stuckToBlockPos.y))
                .add(new IntTag("2", this.stuckToBlockPos.z))
        );
        this.namedTag.putInt(TAG_FAVORED_SLOT, this.favoredSlot);
        this.namedTag.putBoolean(TAG_PLAYER, this.player);
    }

    public Item getItem() {
        return this.trident != null ? this.trident.clone() : Item.get(0);
    }

    public void setItem(Item item) {
        this.trident = item.clone();
        this.loyaltyLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_LOYALTY);
        this.hasChanneling = this.trident.hasEnchantment(Enchantment.ID_TRIDENT_CHANNELING);
        this.riptideLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_RIPTIDE);
        this.impalingLevel = this.trident.getEnchantmentLevel(Enchantment.ID_TRIDENT_IMPALING);
    }

    public void setCritical() {
        this.setCritical(true);
    }

    public boolean isCritical() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_CRITICAL);
    }

    public void setCritical(boolean value) {
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CRITICAL, value);
    }

    @Override
    public int getResultDamage() {
        int base = super.getResultDamage();

        if (this.isCritical()) {
            base += ThreadLocalRandom.current().nextInt(base / 2 + 2);
        }

        return base;
    }

    @Override
    protected double getBaseDamage() {
        return 8;
    }

    /**
     * Loyalty return trajectory adapted from EaseCation/Nukkit 4ae1f9b2 (GPL-3.0).
     * https://github.com/EaseCation/Nukkit/blob/4ae1f9b2f76724fd7a6c331e4c0bf99c1abff9d0/src/main/java/cn/nukkit/entity/projectile/EntityThrownTrident.java
     */
    @Override
    public boolean onUpdate(int currentTick) {
        if (this.closed) {
            return false;
        }
        if (currentTick <= this.lastUpdate && !this.justCreated) {
            return true;
        }
        if (this.age > 1200 && this.pickupMode < 1) {
            this.close();
            return false;
        }

        int tickDiff = Math.max(1, currentTick - this.lastUpdate);
        if (this.loyaltyReturnDelay >= 0) {
            if (this.loyaltyLevel <= 0) {
                this.loyaltyReturnDelay = -1;
            } else if (!this.isReturnOwnerValid()) {
                this.retireLoyaltyReturn();
                return false;
            }
        }
        if (!this.noClip && this.loyaltyLevel > 0 && this.isReturnOwnerValid()
                && this.shootingEntity.level == this.level
                && (this.y <= this.level.getMinBlockY() - 15
                    || this.loyaltyReturnDelay >= 0 && (this.loyaltyReturnDelay -= tickDiff) <= 0)) {
            this.beginLoyaltyReturn();
        }
        if (this.noClip) {
            this.lastUpdate = currentTick;
            if (!this.isReturnOwnerValid()) {
                this.retireLoyaltyReturn();
                return false;
            }
            // Loyalty cannot pursue its owner through a dimension change.
            if (this.shootingEntity.level != this.level) {
                return true;
            }
            Player owner = (Player) this.shootingEntity;
            this.entityBaseTick(tickDiff);
            if (this.closed) {
                return false;
            }
            if (this.shootingEntity != owner || !this.isReturnOwnerValid() || owner.level != this.level) {
                return true;
            }
            Vector3 previous = new Vector3(this.x, this.y, this.z);
            Vector3 direction = owner.getEyePosition().subtract(this);
            Vector3 velocity = this.getMotion().multiply(0.95)
                    .add(direction.normalize().multiply(0.05 * this.loyaltyLevel));
            Vector3 next = this.add(velocity).add(0, direction.y * 0.015 * this.loyaltyLevel, 0);
            if (!this.setPosition(next)) {
                return !this.closed;
            }
            Vector3 actual = new Vector3(this.x, this.y, this.z);
            this.motionX = velocity.x * 0.99;
            this.motionY = velocity.y * 0.99;
            this.motionZ = velocity.z * 0.99;
            this.updateRotation();
            this.yaw = (this.yaw + 180) % 360;
            this.updateMovement();

            if (this.closed || this.shootingEntity != owner || !this.isReturnOwnerValid() || owner.level != this.level) {
                return !this.closed;
            }
            AxisAlignedBB pickupBox = owner.getBoundingBox().grow(1.0, 0.5, 1.0);
            if (pickupBox.isVectorInside(previous) || pickupBox.isVectorInside(actual)
                    || pickupBox.calculateIntercept(previous, actual) != null) {
                // Keep the native world-source claim and accepted inventory receipt.
                owner.pickupEntity(this, true);
            }
            return !this.closed;
        }

        if (this.isCollided && !this.hadCollision) {
            this.getLevel().addSound(this, Sound.ITEM_TRIDENT_HIT_GROUND);
        }
        boolean hasUpdate = super.onUpdate(currentTick);
        if (this.onGround || this.hadCollision) {
            this.setCritical(false);
        }
        // A resting trident must remain scheduled while its block-impact delay elapses.
        return hasUpdate || this.loyaltyReturnDelay >= 0;
    }

    private void retireLoyaltyReturn() {
        // Only ordinary recoverable projectiles may become an item on owner loss.
        boolean recoverable = this.pickupMode == PICKUP_ANY;
        Item item = this.trident;
        this.close();
        if (recoverable && item != null && !item.isNull()
                && level.getGameRules().getBoolean(GameRule.DO_ENTITY_DROPS)) {
            this.level.dropItem(this, item);
        }
    }

    private boolean isReturnOwnerValid() {
        return this.shootingEntity instanceof Player shooter && !shooter.closed
                && shooter.isAlive() && shooter.isOnline() && !shooter.isSpectator();
    }

    private void beginLoyaltyReturn() {
        this.noClip = true;
        this.onGround = false;
        this.isCollided = false;
        this.hadCollision = false;
        this.didHit = true;
        this.loyaltyReturnDelay = -1;
        this.getLevel().addSound(this, Sound.ITEM_TRIDENT_RETURN);
        this.setTridentRope(true);
    }

    @Override
    public boolean canCollideWith(Entity entity) {
        return !this.noClip && !this.didHit && super.canCollideWith(entity);
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        return !this.noClip && super.attack(source);
    }

    @Override
    protected void checkBlockCollision() {
        if (!this.noClip) {
            super.checkBlockCollision();
        }
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        if (this.noClip) {
            return;
        }

        if (this.didHit) {
            return;
        }

        ProjectileHitEvent hitEvent = new ProjectileHitEvent(this, MovingObjectPosition.fromEntity(entity));
        this.server.getPluginManager().callEvent(hitEvent);
        if (hitEvent.isCancelled()) {
            return;
        }
        float damage = this.getResultDamage();
        if (this.impalingLevel > 0 && (entity.isInsideOfWater() || (entity.getLevel().isRaining() && entity.getLevel().canBlockSeeSky(entity)))) {
            damage = damage + (2.5f * (float) this.impalingLevel);
        }

        EntityDamageEvent ev;
        if (this.shootingEntity == null) {
            ev = new EntityDamageByEntityEvent(this, entity, DamageCause.PROJECTILE, damage);
        } else {
            ev = new EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, DamageCause.PROJECTILE, damage);
        }
        entity.attack(ev);
        this.hadCollision = true;
        this.onHit();
        this.setCollisionPos(this);
        this.setMotion(new Vector3(this.getMotion().getX() * -0.01, this.getMotion().getY() * -0.1, this.getMotion().getZ() * -0.01));
        this.didHit = true;

        if (trident != null && level.isThundering() && this.hasChanneling && this.canSeeSky()) {
            EntityLightning bolt = new EntityLightning(this.getChunk(), getDefaultNBT(this));
            LightningStrikeEvent strikeEvent = new LightningStrikeEvent(level, bolt);
            server.getPluginManager().callEvent(strikeEvent);
            if (!strikeEvent.isCancelled()) {
                bolt.spawnToAll();
                level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ITEM_TRIDENT_THUNDER);
            } else {
                bolt.setEffect(false);
            }
        }

        if (this.canReturnToShooter()) {
            this.beginLoyaltyReturn();
        }
    }

    @Override
    public void onHit() {
        this.getLevel().addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_ITEM_TRIDENT_HIT);
    }

    @Override
    public void onHitGround(Vector3 vector3) {
        if (this.noClip) {
            return;
        }
        super.onHitGround(vector3);
        this.setStuckToBlockPos(new BlockVector3(vector3.getFloorX(), vector3.getFloorY(), vector3.getFloorZ()));
        if (this.canReturnToShooter()) {
            this.loyaltyReturnDelay = 5;
        }
    }

    public int getPickupMode() {
        return this.pickupMode;
    }

    public void setPickupMode(int pickupMode) {
        this.pickupMode = pickupMode;
    }

    public Vector3 getCollisionPos() {
        return collisionPos;
    }

    public void setCollisionPos(Vector3 collisionPos) {
        this.collisionPos = collisionPos;
    }

    public BlockVector3 getStuckToBlockPos() {
        return stuckToBlockPos;
    }

    public void setStuckToBlockPos(BlockVector3 stuckToBlockPos) {
        this.stuckToBlockPos = stuckToBlockPos;
    }

    public int getFavoredSlot() {
        return favoredSlot;
    }

    public void setFavoredSlot(int favoredSlot) {
        this.favoredSlot = favoredSlot;
    }

    public boolean isCreative() {
        return getPickupMode() == EntityProjectile.PICKUP_CREATIVE;
    }

    public boolean isPlayer() {
        return player;
    }

    public void setPlayer(boolean player) {
        this.player = player;
    }

    public int getLoyaltyLevel() {
        return loyaltyLevel;
    }

    public void setLoyaltyLevel(int loyaltyLevel) {
        this.loyaltyLevel = loyaltyLevel;
        if (loyaltyLevel > 0) {
            this.trident.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_LOYALTY).setLevel(loyaltyLevel));
        } else {
            // TODO: this.trident.removeEnchantment(Enchantment.ID_TRIDENT_LOYALTY);
        }
    }

    public boolean hasChanneling() {
        return hasChanneling;
    }

    public void setChanneling(boolean hasChanneling) {
        this.hasChanneling = hasChanneling;
        if (hasChanneling) {
            this.trident.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_CHANNELING));
        } else {
            // TODO: this.trident.removeEnchantment(Enchantment.ID_TRIDENT_CHANNELING);
        }
    }

    public int getRiptideLevel() {
        return riptideLevel;
    }

    public void setRiptideLevel(int riptideLevel) {
        this.riptideLevel = riptideLevel;
        if (riptideLevel > 0) {
            this.trident.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_RIPTIDE).setLevel(riptideLevel));
        } else {
            // TODO: this.trident.removeEnchantment(Enchantment.ID_TRIDENT_RIPTIDE);
        }
    }

    public int getImpalingLevel() {
        return impalingLevel;
    }

    public void setImpalingLevel(int impalingLevel) {
        this.impalingLevel = impalingLevel;
        if (impalingLevel > 0) {
            this.trident.addEnchantment(Enchantment.getEnchantment(Enchantment.ID_TRIDENT_IMPALING).setLevel(impalingLevel));
        } else {
            // TODO: this.trident.removeEnchantment(Enchantment.ID_TRIDENT_IMPALING);
        }
    }

    public boolean getTridentRope() {
        return this.getDataFlag(DATA_FLAGS, DATA_FLAG_SHOW_TRIDENT_ROPE);
    }

    public void setRope(boolean tridentRope) { //兼容PM1E
        this.setTridentRope(tridentRope);
    }

    public void setTridentRope(boolean tridentRope) {
        if (tridentRope) {
            this.setDataProperty(new LongEntityData(DATA_OWNER_EID, this.shootingEntity.getId()));
        } else {
            this.setDataProperty(new LongEntityData(DATA_OWNER_EID, -1));
        }
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHOW_TRIDENT_ROPE, tridentRope);
    }

    public boolean canReturnToShooter() {
        if (this.loyaltyLevel <= 0) {
            return false;
        }

        if (this.getCollisionPos().equals(defaultCollisionPos) && this.getStuckToBlockPos().equals(defaultStuckToBlockPos)) {
            return false;
        }

        Entity shooter = this.shootingEntity;
        if (shooter != null) {
            return this.isReturnOwnerValid() && shooter.level == this.level;
        }
        return false;
    }
}
