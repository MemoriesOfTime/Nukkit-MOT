package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.projectile.EntitySlenderProjectile;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityDamageEvent.DamageCause;
import cn.nukkit.event.entity.ProjectileHitEvent;
import cn.nukkit.event.player.PlayerFishEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBookEnchanted;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.item.randomitem.Fishing;
import cn.nukkit.level.MovingObjectPosition;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.particle.BubbleParticle;
import cn.nukkit.level.particle.WaterParticle;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.AddEntityPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.EntityEventPacket;
import cn.nukkit.utils.Utils;

import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;


/**
 * Created by PetteriM1
 */
public class EntityFishingHook extends EntitySlenderProjectile {

    public static final int NETWORK_ID = 77;

    public int waitChance = 120;
    public int waitTimer = 240;
    public boolean attracted = false;
    public int attractTimer = 0;
    public boolean caught = false;
    public int caughtTimer = 0;
    public boolean canCollide = true;
    public Entity caughtEntity = null;
    private long target = 0;

    public Vector3 fish = null;

    public Item rod = null;

    public EntityFishingHook(FullChunk chunk, CompoundTag nbt) {
        this(chunk, nbt, null);
    }

    public EntityFishingHook(FullChunk chunk, CompoundTag nbt, Entity shootingEntity) {
        super(chunk, nbt, shootingEntity);
    }

    @Override
    protected void initEntity() {
        super.initEntity();
        if (this.age > 0) {
            this.close();
        }
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.2f;
    }

    @Override
    public float getLength() {
        return 0.2f;
    }

    @Override
    public float getHeight() {
        return 0.2f;
    }

    @Override
    public float getGravity() {
        return 0.07f;
    }

    @Override
    public float getDrag() {
        return 0.05f;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (this.target != 0) {
            Entity ent = this.level.getEntity(this.target);
            if (ent == null || !ent.isAlive()) {
                this.caughtEntity = null;
                this.setTarget(0);
            } else {
                this.setPosition(new Vector3(ent.x, ent.y + (getHeight() * 0.75f), ent.z));
            }
            return false;
        }

        boolean hasUpdate = super.onUpdate(currentTick);

        boolean inWater = this.isInsideOfWater();
        if (inWater) {
            this.motionX = 0;
            this.motionY -= getGravity() * -0.04;
            this.motionZ = 0;
            hasUpdate = true;
        } else if (this.isCollided && this.keepMovement) {
            this.motionX = 0;
            this.motionY = 0;
            this.motionZ = 0;
            this.keepMovement = false;
            hasUpdate = true;
        }

        if (inWater) {
            if (this.waitTimer == 240) {
                this.waitTimer = this.waitChance << 1;
            } else if (this.waitTimer == 360) {
                this.waitTimer = this.waitChance * 3;
            }
            if (!this.attracted) {
                if (this.waitTimer > 0) {
                    --this.waitTimer;
                }
                if (this.waitTimer == 0) {
                    if (Utils.random.nextInt(100) < 90) {
                        this.attractTimer = (Utils.random.nextInt(40) + 20);
                        this.spawnFish();
                        this.caught = false;
                        this.attracted = true;
                    } else {
                        this.waitTimer = this.waitChance;
                    }
                }
            } else if (!this.caught) {
                if (this.attractFish()) {
                    this.caughtTimer = (Utils.random.nextInt(20) + 30);
                    this.fishBites();
                    this.caught = true;
                }
            } else {
                if (this.caughtTimer > 0) {
                    --this.caughtTimer;
                }
                if (this.caughtTimer == 0) {
                    this.attracted = false;
                    this.caught = false;
                    this.waitTimer = this.waitChance * 3;
                }
            }
        }

        return hasUpdate;
    }

    @Override
    protected void updateMotion() {
        //正确的浮力
        if (this.isInsideOfWater() && this.getY() < this.getWaterHeight() - 2) {
            this.motionX = 0;
            this.motionY += getGravity();
            this.motionZ = 0;
        } else if (this.isInsideOfWater() && this.getY() >= this.getWaterHeight() - 2) {//防止鱼钩上浮超出水面
            this.motionX = 0;
            this.motionZ = 0;
            this.motionY = 0;
        } else {//处理不在水中的情况
            super.updateMotion();
        }
    }

    public int getWaterHeight() {
        for (int y = this.getFloorY(); y < 256; y++) {
            int id = this.level.getBlockIdAt(chunk, this.getFloorX(), y, this.getFloorZ());
            if (id == Block.AIR) {
                return y;
            }
        }
        return this.getFloorY();
    }

    public void fishBites() {
        Collection<Player> viewers = this.getViewers().values();

        EntityEventPacket pk = new EntityEventPacket();
        pk.eid = this.getId();
        pk.event = EntityEventPacket.FISH_HOOK_HOOK;
        Server.broadcastPacket(viewers, pk);

        EntityEventPacket bubblePk = new EntityEventPacket();
        bubblePk.eid = this.getId();
        bubblePk.event = EntityEventPacket.FISH_HOOK_BUBBLE;
        Server.broadcastPacket(viewers, bubblePk);

        EntityEventPacket teasePk = new EntityEventPacket();
        teasePk.eid = this.getId();
        teasePk.event = EntityEventPacket.FISH_HOOK_TEASE;
        Server.broadcastPacket(viewers, teasePk);

        this.level.addParticle(new BubbleParticle(this.setComponents(
                this.x + Utils.random.nextDouble() * 0.5 - 0.25,
                this.getWaterHeight(),
                this.z + Utils.random.nextDouble() * 0.5 - 0.25
        )), null, 5);
    }

    public void spawnFish() {
        this.fish = new Vector3(
                this.x + (Utils.random.nextDouble() * 1.2 + 1) * (Utils.random.nextBoolean() ? -1 : 1),
                this.getWaterHeight(),
                this.z + (Utils.random.nextDouble() * 1.2 + 1) * (Utils.random.nextBoolean() ? -1 : 1)
        );
    }

    public boolean attractFish() {
        double multiply = 0.1;
        this.fish.setComponents(
                this.fish.x + (this.x - this.fish.x) * multiply,
                this.fish.y,
                this.fish.z + (this.z - this.fish.z) * multiply
        );
        if (Utils.random.nextInt(100) < 85) {
            this.level.addParticle(new WaterParticle(this.fish));
        }
        double dist = Math.abs(Math.sqrt(this.x * this.x + this.z * this.z) - Math.sqrt(this.fish.x * this.fish.x + this.fish.z * this.fish.z));
        return dist < 0.15;
    }

    public void reelLine() {
        if (this.shootingEntity instanceof Player) {
            Player player = (Player) this.shootingEntity;
            if (this.caught) {
                Item item = Fishing.getFishingResult(this.rod);
                int experience = Utils.random.nextInt(3) + 1;
                Vector3 pos = new Vector3(this.x, this.getWaterHeight(), this.z); //实体生成在水面上
                Vector3 motion = player.subtract(pos).multiply(0.2);
                motion.y += Math.sqrt(player.add(0, player.getEyeHeight(), 0).distance(pos)) * 0.08;

                PlayerFishEvent event = new PlayerFishEvent(player, this, item, experience, motion);
                this.getServer().getPluginManager().callEvent(event);

                if (!event.isCancelled()) {
                    EntityItem itemEntity = new EntityItem(
                            this.level.getChunk((int) this.x >> 4, (int) this.z >> 4, true),
                            Entity.getDefaultNBT(pos, event.getMotion(), ThreadLocalRandom.current().nextFloat() * 360, 0).putShort("Health", 5).putCompound("Item", NBTIO.putItemHelper(event.getLoot())).putShort("PickupDelay", 1));

                    itemEntity.setOwner(player.getName());
                    itemEntity.spawnToAll();

                    player.getLevel().dropExpOrb(player, event.getExperience());
                }
            } else if (this.caughtEntity != null) {
                Vector3 motion = this.shootingEntity.subtract(this).multiply(0.1);
                motion.y += Math.sqrt(this.shootingEntity.distance(this)) * 0.08;
                this.caughtEntity.setMotion(motion);
            }
        }
        this.close();
    }

    @Override
    protected DataPacket createAddEntityPacket() {
        AddEntityPacket pk = new AddEntityPacket();
        pk.entityRuntimeId = this.getId();
        pk.entityUniqueId = this.getId();
        pk.type = NETWORK_ID;
        pk.x = (float) this.x;
        pk.y = (float) this.y;
        pk.z = (float) this.z;
        pk.speedX = (float) this.motionX;
        pk.speedY = (float) this.motionY;
        pk.speedZ = (float) this.motionZ;
        pk.yaw = (float) this.yaw;
        pk.pitch = (float) this.pitch;

        long ownerId = -1;
        if (this.shootingEntity != null) {
            ownerId = this.shootingEntity.getId();
        }
        pk.metadata = this.dataProperties.putLong(DATA_OWNER_EID, ownerId).clone();
        return pk;
    }

    @Override
    public boolean canCollide() {
        return this.canCollide;
    }

    @Override
    public void onCollideWithEntity(Entity entity) {
        if (entity == this.shootingEntity) {
            return;
        }
        ProjectileHitEvent hitEvent = new ProjectileHitEvent(this, MovingObjectPosition.fromEntity(entity));
        this.server.getPluginManager().callEvent(hitEvent);
        if (hitEvent.isCancelled()) {
            return;
        }
        float damage = this.getResultDamage();

        EntityDamageEvent ev;
        if (this.shootingEntity == null) {
            ev = new EntityDamageByEntityEvent(this, entity, DamageCause.PROJECTILE, damage);
        } else {
            ev = new EntityDamageByChildEntityEvent(this.shootingEntity, this, entity, DamageCause.PROJECTILE, damage);
        }

        if (entity.attack(ev)) {
            this.hadCollision = true;

            this.caughtEntity = entity;
            this.setTarget(entity.getId());
        }
    }

    public void checkLure() {
        if (rod != null) {
            Enchantment ench = rod.getEnchantment(Enchantment.ID_LURE);
            if (ench != null) {
                this.waitChance = 120 - (25 * ench.getLevel());
            }
        }
    }

    public void setTarget(long eid) {
        this.target = eid;
        this.setDataProperty(new LongEntityData(DATA_TARGET_EID, eid));
        this.canCollide = eid == 0;
    }
}
