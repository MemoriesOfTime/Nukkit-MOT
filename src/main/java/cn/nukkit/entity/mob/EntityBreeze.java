package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.projectile.EntityBreezeWindCharge;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

public class EntityBreeze extends EntityFlyingMob {

    public static final int NETWORK_ID = 140;

    public EntityBreeze(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
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
        return 1.77f;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(30);

        super.initEntity();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player player) {
            return !player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure()) && distance <= 1024;
        }
        return false;
    }

    @Override
    public void attackEntity(Entity target) {
        if (this.attackDelay > 30 && Utils.rand(1, 20) < 4) {
            this.attackDelay = 0;

            double dx = target.x - this.x;
            double dz = target.z - this.z;
            double dy = (target.y + target.getHeight() * 0.5) - (this.y + this.getEyeHeight());

            double distanceXZ = Math.sqrt(dx * dx + dz * dz);
            double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);

            double yaw = Math.toDegrees(Math.atan2(dz, dx)) - 90;
            if (yaw < 0) yaw += 360;

            double pitch = -Math.toDegrees(Math.atan2(dy, distanceXZ));
            pitch = Math.max(-30, Math.min(30, pitch));

            double yawR = FastMath.toRadians(yaw);
            double pitchR = FastMath.toRadians(pitch);

            Location pos = new Location(
                    this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5,
                    this.y + this.getEyeHeight(),
                    this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5,
                    yaw, pitch, this.level
            );

            EntityBreezeWindCharge windCharge = (EntityBreezeWindCharge) Entity.createEntity("BreezeWindCharge", pos, this);
            if (windCharge == null) {
                return;
            }

            double f = 1.2;
            windCharge.setMotion(new Vector3(
                    -Math.sin(yawR) * Math.cos(pitchR) * f,
                    -Math.sin(pitchR) * f,
                    Math.cos(yawR) * Math.cos(pitchR) * f
            ));

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(windCharge);
            this.server.getPluginManager().callEvent(launch);
            if (launch.isCancelled()) {
                windCharge.close();
            } else {
                windCharge.spawnToAll();
            }
        }
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();
        Item breezeRod = Item.fromString(Item.BREEZE_ROD);
        breezeRod.setCount(Utils.rand(1, 2));
        drops.add(breezeRod);
        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 10;
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 1000; // don't follow
    }
}
