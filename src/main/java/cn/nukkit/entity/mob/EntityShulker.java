package cn.nukkit.entity.mob;

import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.projectile.EntityShulkerBullet;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.sound.EndermanTeleportSound;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

public class EntityShulker extends EntityWalkingMob {

    public static final int NETWORK_ID = 54;

    public EntityShulker(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 1f;
    }

    @Override
    public float getHeight() {
        return 1f;
    }
    
    @Override
    public double getSpeed() {
        return 0;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(15);

        super.initEntity();

        this.fireProof = true;
        this.noFallDamage = true;
        this.setDamage(Utils.getEmptyDamageArray());
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 60 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 256) {
            this.attackDelay = 0;

            double f = 0.5;
            double yaw = this.yaw + Utils.rand(-4.0, 4.0);
            double pitch = this.pitch + Utils.rand(-4.0, 4.0);
            double yawR = FastMath.toRadians(yaw);
            double pitchR = FastMath.toRadians(pitch);
            Location pos = new Location(this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5, this.y + this.getHeight() - 0.18,
                        this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5, yaw, pitch, this.level);

            if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) != Block.AIR) {
                return;
            }

            Entity k = Entity.createEntity("ShulkerBullet", pos, this);
            if (!(k instanceof EntityShulkerBullet bullet)) {
                return;
            }

            bullet.setMotion(new Vector3(-Math.sin(yawR) * Math.cos(pitchR) * f * f, -Math.sin(pitchR) * f * f,
                    Math.cos(yawR) * Math.cos(pitchR) * f * f));

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(bullet);
            this.server.getPluginManager().callEvent(launch);

            if (launch.isCancelled()) {
                bullet.close();
            } else {
                bullet.spawnToAll();
                this.level.addSoundToViewers(this, Sound.MOB_SHULKER_SHOOT);
            }
        }
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (!ev.isCancelled()) {
            if (Utils.rand(1, 10) == 1) {
                this.level.addSound(new EndermanTeleportSound(this));
                this.move(Utils.rand(-10, 10), 0, Utils.rand(-10, 10));
            }
        }

        return true;
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.SHULKER_SHELL, 0, Utils.rand(0, 1))};
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public void knockBack(Entity attacker, double damage, double x, double z, double base) {
    }
}
