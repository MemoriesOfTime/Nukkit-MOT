package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.item.EntityPotion;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

import java.util.ArrayList;
import java.util.List;

public class EntityWitch extends EntityWalkingMob {

    public static final int NETWORK_ID = 45;

    public EntityWitch(FullChunk chunk, CompoundTag nbt) {
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
        return 1.95f;
    }

    @Override
    protected void initEntity() {
        this.setMaxHealth(26);

        super.initEntity();
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        if (creature instanceof Player) {
            Player player = (Player) creature;
            return !player.closed && player.spawned && player.isAlive() && (player.isSurvival() || player.isAdventure()) && distance <= 100;
        }
        return creature.isAlive() && !creature.closed && distance <= 100;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 60 && Utils.rand(1, 3) == 2 && this.distanceSquared(player) <= 60) {
            this.attackDelay = 0;
            if (player.isAlive() && !player.closed) {

                double f = 1;
                double yaw = this.yaw + Utils.rand(-4.0, 4.0);
                double yawR = FastMath.toRadians(yaw);
                double pitchR = FastMath.toRadians(pitch);
                Location pos = new Location(this.x - Math.sin(yawR) * Math.cos(pitchR) * 0.5, this.y + this.getEyeHeight(),
                        this.z + Math.cos(yawR) * Math.cos(pitchR) * 0.5, yaw, pitch, this.level);

                if (this.getLevel().getBlockIdAt(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ()) != Block.AIR) {
                    return;
                }

                EntityPotion thrownPotion = (EntityPotion) Entity.createEntity("ThrownPotion", pos, this);

                double distance = this.distanceSquared(player);

                if (!player.hasEffect(Effect.SLOWNESS) && distance <= 64) {
                    thrownPotion.potionId = Potion.SLOWNESS;
                } else if (player.getHealth() >= 8) {
                    thrownPotion.potionId = Potion.POISON;
                } else if (!player.hasEffect(Effect.WEAKNESS) && Utils.rand(0, 4) == 0 && distance <= 9) {
                    thrownPotion.potionId = Potion.WEAKNESS;
                } else {
                    thrownPotion.potionId = Potion.HARMING;
                }

                thrownPotion.setMotion(new Vector3(-Math.sin(yawR) * Math.cos(pitchR) * f * f, -Math.sin(pitchR) * f * f,
                        Math.cos(yawR) * Math.cos(pitchR) * f * f));
                ProjectileLaunchEvent launch = new ProjectileLaunchEvent(thrownPotion);
                this.server.getPluginManager().callEvent(launch);
                if (launch.isCancelled()) {
                    thrownPotion.close();
                } else {
                    thrownPotion.spawnToAll();
                    this.level.addSoundToViewers(this, Sound.MOB_WITCH_THROW);
                }
            }
        }
    }

    @Override
    public Item[] getDrops() {
        List<Item> drops = new ArrayList<>();

        if (Utils.rand(1, 4) == 1) {
            drops.add(Item.get(Item.STICK, 0, Utils.rand(0, 2)));
        }

        if (Utils.rand(1, 3) == 1) {
            switch (Utils.rand(1, 6)) {
                case 1:
                    drops.add(Item.get(Item.BOTTLE, 0, Utils.rand(0, 2)));
                    break;
                case 2:
                    drops.add(Item.get(Item.GLOWSTONE_DUST, 0, Utils.rand(0, 2)));
                    break;
                case 3:
                    drops.add(Item.get(Item.GUNPOWDER, 0, Utils.rand(0, 2)));
                    break;
                case 4:
                    drops.add(Item.get(Item.REDSTONE, 0, Utils.rand(0, 2)));
                    break;
                case 5:
                    drops.add(Item.get(Item.SPIDER_EYE, 0, Utils.rand(0, 2)));
                    break;
                case 6:
                    drops.add(Item.get(Item.SUGAR, 0, Utils.rand(0, 2)));
                    break;
            }
        }

        return drops.toArray(Item.EMPTY_ARRAY);
    }

    @Override
    public int getKillExperience() {
        return 5;
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 8;
    }
}
