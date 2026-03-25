package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.projectile.EntityProjectile;
import cn.nukkit.event.entity.EntityShootBowEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemShears;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.potion.Effect;
import cn.nukkit.utils.Utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class EntityBogged extends EntitySkeleton {

    public static final int NETWORK_ID = 144;
    protected boolean sheared = false;

    public EntityBogged(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(16);

        super.initEntity();

        if (this.namedTag.contains("Sheared")) {
            this.sheared = this.namedTag.getBoolean("Sheared");
            if (this.sheared) {
                this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHEARED, true);
            }
        }
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Sheared", this.sheared);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item instanceof ItemShears && !this.sheared && this.isAlive()) {
            this.shear();
            if (!player.isCreative()) {
                item.useOn(this);
            }
            return true;
        }
        return super.onInteract(player, item, clickedPos);
    }

    public void shear() {
        this.sheared = true;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHEARED, true);
        this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SHEAR);

        for (int i = 0; i < 2; i++) {
            Item mushroom = Utils.rand()
                    ? Item.get(Block.RED_MUSHROOM, 0, 1)
                    : Item.get(Block.BROWN_MUSHROOM, 0, 1);
            this.level.dropItem(this, mushroom);
        }
    }

    public boolean isSheared() {
        return this.sheared;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 30 && Utils.rand(1, 32) < 4) {
            this.attackDelay = 0;

            double distanceToTarget = Math.sqrt(this.distanceSquared(player));

            for (Block block : this.getLineOfSight(15, 15)) {
                if (!block.canPassThrough()) {
                    double blockDist = Math.sqrt(this.distanceSquared(this.temporalVector.setComponents(block.getX(), block.getY(), block.getZ())));
                    if (blockDist < distanceToTarget - 1) {
                        return;
                    }
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
                pitchCorrection = Math.min(20, distance * 0.5);
            }

            double pitch = basePitch + pitchCorrection;

            pitch = Math.max(-40, Math.min(40, pitch));

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

                arrow.getMobEffects().put(Effect.POISON, Objects.requireNonNull(Effect.getEffect(Effect.POISON)).setDuration(100));
                arrow.setFullEffect(true);

                double motionX = -Math.sin(yawR) * Math.cos(pitchR);
                double motionY = -Math.sin(pitchR);
                double motionZ = Math.cos(yawR) * Math.cos(pitchR);

                if (pitch > -20) {
                    motionY += 0.08;
                }

                Vector3 motion = new Vector3(motionX, motionY, motionZ).multiply(f);

                if (distance < 4) {
                    motion.y *= 0.7;
                } else if (distance > 10) {
                    motion.multiply(1.0 + (distance - 10) * 0.02);
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
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        for (int i = 0; i < Utils.rand(0, 2); i++) {
            drops.add(Item.get(Item.BONE, 0, 1));
        }

        for (int i = 0; i < Utils.rand(0, 2); i++) {
            drops.add(Item.get(Item.ARROW, 0, 1));
        }

        if (Utils.rand()) {
            drops.add(Item.get(Item.ARROW, 26, 1));
        }

        if (Utils.rand(0, 99) < 8) {
            drops.add(Item.get(Item.BOW, Utils.rand(100, 380), 1));
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }
}
