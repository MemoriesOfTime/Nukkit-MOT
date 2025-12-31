package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.EntitySmite;
import cn.nukkit.entity.data.LongEntityData;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBow;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector2;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.network.protocol.MobArmorEquipmentPacket;
import cn.nukkit.network.protocol.MobEquipmentPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

public class EntitySkeleton extends EntityWalkingMob implements EntitySmite {

    public static final int NETWORK_ID = 34;

    private boolean angryFlagSet;
    private boolean hasPumpkin;

    public EntitySkeleton(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(20);

        super.initEntity();

        if (java.time.LocalDate.now().toString().contains("-10-31") && Utils.rand(0, 10) < 2) {
            this.hasPumpkin = true;
        }
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.6f;
    }

    @Override
    public float getHeight() {
        return 1.99f;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 55) {
            this.attackDelay = 0;

            for (Block block : this.getLineOfSight(7, 7)) {
                if (!block.canPassThrough()) {
                    return;
                }
            }

            double f = 1.3;

            double dx = player.x - this.x;
            double dz = player.z - this.z;

            double targetHeight = player.getHeight() * 0.85;
            double targetY = player.y + targetHeight;

            double myY = this.y + this.getEyeHeight();
            double dy = targetY - myY;

            double distanceXZ = Math.sqrt(dx * dx + dz * dz);

            double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
            if (yaw < 0) yaw += 360;

            double basePitch = -Math.toDegrees(Math.atan2(dy, distanceXZ));

            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double pitchCorrection = 0;

            if (distance > 3) {
                pitchCorrection = Math.min(15, distance * 0.8);
            }

            double pitch = basePitch + pitchCorrection;

            pitch = Math.max(-30, Math.min(30, pitch));

            double yawR = Math.toRadians(yaw);
            double pitchR = Math.toRadians(pitch);

            double shootY = this.y + this.getEyeHeight();
            if (pitch > -5) {
                shootY += 0.15;
            }

            Location pos = new Location(
                    this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5,
                    shootY,
                    this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5,
                    yaw, pitch, this.level
            );

            if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) == Block.AIR) {
                Entity k = Entity.createEntity("Arrow", pos, this);
                if (!(k instanceof EntityArrow arrow)) {
                    return;
                }

                double motionX = -Math.sin(yawR) * Math.cos(pitchR);
                double motionY = -Math.sin(pitchR);
                double motionZ = Math.cos(yawR) * Math.cos(pitchR);

                if (pitch > -20) {
                    motionY += 0.08;
                }

                Vector3 motion = new Vector3(motionX, motionY, motionZ).multiply(f);

                if (distance < 4) {
                    motion.y *= 0.7;
                }

                arrow.setMotion(motion);

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
                        projectile.spawnToAll();
                        ((EntityArrow) projectile).setPickupMode(EntityArrow.PICKUP_NONE);
                        this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_BOW);
                    }
                }
            }
        }
    }

    @Override
    public void spawnTo(Player player) {
        super.spawnTo(player);

        MobEquipmentPacket pk = new MobEquipmentPacket();
        pk.eid = this.getId();
        pk.item = new ItemBow();
        pk.hotbarSlot = 0;
        player.dataPacket(pk);

        if (this.hasPumpkin) {
            MobArmorEquipmentPacket pk2 = new MobArmorEquipmentPacket();
            pk2.eid = this.getId();
            pk2.slots[0] = Item.get(Item.PUMPKIN);
            player.dataPacket(pk2);
        }
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate;

        if (getServer().getDifficulty() == 0) {
            this.close();
            return true;
        }

        hasUpdate = super.entityBaseTick(tickDiff);

        if (!this.closed && level.shouldMobBurn(this)) {
            this.setOnFire(100);
        }

        return hasUpdate;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        drops.add(Item.get(Item.BONE, 0, Utils.rand(0, 2)));
        drops.add(Item.get(Item.ARROW, 0, Utils.rand(0, 2)));

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 10;
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        boolean hasTarget = super.targetOption(creature, distance);
        if (hasTarget) {
            if (!this.angryFlagSet && creature != null) {
                this.setDataProperty(new LongEntityData(DATA_TARGET_EID, creature.getId()));
                this.angryFlagSet = true;
            }
        } else {
            if (this.angryFlagSet) {
                this.setDataProperty(new LongEntityData(DATA_TARGET_EID, 0));
                this.angryFlagSet = false;
                this.stayTime = 100;
            }
        }
        return hasTarget;
    }

    @Override
    public void kill() {
        if (!this.isAlive()) {
            return;
        }

        super.kill();

        if (this.lastDamageCause instanceof EntityDamageByChildEntityEvent) {
            Entity damager;
            if (((EntityDamageByChildEntityEvent) this.lastDamageCause).getChild() instanceof EntityArrow && (damager = ((EntityDamageByChildEntityEvent) this.lastDamageCause).getDamager()) instanceof Player) {
                if (new Vector2(this.x, this.z).distance(new Vector2(damager.x, damager.z)) >= 50) {
                    ((Player) damager).awardAchievement("snipeSkeleton");
                }
            }
        }
    }
}
