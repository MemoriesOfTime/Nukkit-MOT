package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.inventory.PlayerInventory;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

/**
 * @author Erik Miller | EinBexiii
 */
public class EntityPiglin extends EntityWalkingMob {

    public final static int NETWORK_ID = 123;

    private int angry;
    private boolean angryFlagSet;

    public EntityPiglin(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    private static boolean isWearingGold(Player p) {
        PlayerInventory i = p.getInventory();
        return i.getHelmetFast().getId() == Item.GOLD_HELMET || i.getChestplateFast().getId() == Item.GOLD_CHESTPLATE || i.getLeggingsFast().getId() == Item.GOLD_LEGGINGS || i.getBootsFast().getId() == Item.GOLD_BOOTS;
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(16);

        super.initEntity();
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.95f;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 80 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 100) {
            this.attackDelay = 0;

            double f = 1.5;
            double yaw = this.yaw;
            double pitch = this.pitch;
            double yawR = FastMath.toRadians(yaw);
            double pitchR = FastMath.toRadians(pitch);
            Location pos = new Location(this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5, this.y + this.getHeight() - 0.18,
                    this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5, yaw, pitch, this.level);

            if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) == Block.AIR) {
                Entity k = Entity.createEntity("Arrow", pos, this);
                if (!(k instanceof EntityArrow arrow)) {
                    return;
                }

                setProjectileMotion(arrow, pitch, yawR, pitchR, f);

                EntityShootBowEvent ev = new EntityShootBowEvent(this, Item.get(Item.ARROW, 0, 1), arrow, f);
                this.server.getPluginManager().callEvent(ev);

                EntityProjectile projectile = ev.getProjectile();
                if (ev.isCancelled()) {
                    projectile.close();
                } else {
                    ProjectileLaunchEvent launch = new ProjectileLaunchEvent(projectile);
                    this.server.getPluginManager().callEvent(launch);
                    if (launch.isCancelled()) {
                        projectile.close();
                    } else {
                        projectile.namedTag.putDouble("damage", 4);
                        projectile.spawnToAll();
                        ((EntityArrow) projectile).setPickupMode(EntityArrow.PICKUP_NONE);
                        this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_CROSSBOW_SHOOT);
                    }
                }
            }
        }
    }

    public boolean isAngry() {
        return this.angry > 0;
    }

    public void setAngry(int val) {
        this.angry = val;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGED, val > 0);
        this.angryFlagSet = val > 0;
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (!ev.isCancelled() && ev instanceof EntityDamageByEntityEvent) {
            if (((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
                this.setAngry(600);
            }
        }

        return true;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (distance <= 100 && this.isAngry() && creature instanceof EntityPiglin && !((EntityPiglin) creature).isAngry()) {
            ((EntityPiglin) creature).setAngry(600);
        }
        boolean hasTarget = creature instanceof Player && (this.isAngry() || !isWearingGold((Player) creature)) && super.targetOption(creature, distance);
        if (hasTarget) {
            if (!this.angryFlagSet) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGED, true);
                this.angryFlagSet = true;
            }
        } else {
            if (this.angryFlagSet) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGED, false);
                this.angryFlagSet = false;
                this.stayTime = 100;
            }
        }
        return hasTarget;
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        MobEquipmentPacket pk = new MobEquipmentPacket();
        pk.eid = this.getId();
        pk.item = Item.get(Item.CROSSBOW, 0, 1);
        pk.hotbarSlot = 0;
        player.dataPacket(pk);
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 20;
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.angry > 0) {
            if (this.angry == 1) {
                this.setAngry(0); // Reset flag
            } else {
                this.angry--;
            }
        }

        return super.entityBaseTick(tickDiff);
    }
}
