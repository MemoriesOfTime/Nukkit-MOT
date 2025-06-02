package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.projectile.EntityGhastFireBall;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelEventPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

public class EntityGhast extends EntityFlyingMob {

    public static final int NETWORK_ID = 41;

    private boolean attacked;

    public EntityGhast(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 4;
    }

    @Override
    public float getHeight() {
        return 4;
    }

    @Override
    public double getSpeed() {
        return 1.2;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(10);

        super.initEntity();

        this.fireProof = true;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_FIRE_IMMUNE, true);
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player player) {
            return !player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure()) && distance <= 4096;
        }
        return false;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.distanceSquared(player) <= (this.attacked ? 4096 : 784)) { // 28 blocks or 64 blocks if attacked)
            if (Utils.rand()) {
                this.attackDelay--;
                return;
            }
            if (this.attackDelay == 50) {
                this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_GHAST);
            }
            if (this.attackDelay > 60) {
                this.attackDelay = 0;

                double f = 1.01;
                double yaw = this.yaw + Utils.rand(-4.0, 4.0);
                double pitch = this.pitch + Utils.rand(-4.0, 4.0);
                Location pos = new Location(this.x - Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, this.y + this.getEyeHeight(),
                        this.z + Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, yaw, pitch, this.level);

                if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) != Block.AIR) {
                    return;
                }

                // 暂时不要遍历64格，以免卡顿
                for (Block block : this.getLineOfSight(28, 28)) {
                    if (!block.canPassThrough()) {
                        return;
                    }
                }

                EntityGhastFireBall fireball = (EntityGhastFireBall) Entity.createEntity("GhastFireBall", pos, this);
                fireball.setExplode(true);
                fireball.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f,
                        Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f));

                ProjectileLaunchEvent launch = new ProjectileLaunchEvent(fireball);
                this.server.getPluginManager().callEvent(launch);
                if (launch.isCancelled()) {
                    fireball.close();
                } else {
                    fireball.spawnToAll();
                    this.level.addLevelEvent(this, LevelEventPacket.EVENT_SOUND_GHAST_SHOOT);
                }
            }
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        boolean result = super.attack(ev);

        if (!ev.isCancelled() && ev instanceof EntityDamageByEntityEvent) {
            if (((EntityDamageByEntityEvent) ev).getDamager() instanceof Player) {
                this.attacked = true;
            }
        }

        return result;
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        drops.add(Item.get(Item.GUNPOWDER, 0, Utils.rand(0, 2)));
        drops.add(Item.get(Item.GHAST_TEAR, 0, Utils.rand(0, 1)));

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 1000; // don't follow
    }

    @Override
    public void kill() {
        if (this.isAlive()) {
            super.kill();

            if (this.getLastDamageCause() instanceof EntityDamageByChildEntityEvent && ((EntityDamageByChildEntityEvent) this.getLastDamageCause()).getDamager() == this) {
                Entity damager = ((EntityDamageByChildEntityEvent) this.getLastDamageCause()).getChild();
                if (damager instanceof EntityGhastFireBall && ((EntityGhastFireBall) damager).directionChanged != null) {
                    ((EntityGhastFireBall) damager).directionChanged.awardAchievement("ghast");
                }
            }
        }
    }
}
