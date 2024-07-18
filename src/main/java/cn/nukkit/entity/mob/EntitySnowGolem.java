package cn.nukkit.entity.mob;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.projectile.EntitySnowball;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.Sound;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

public class EntitySnowGolem extends EntityWalkingMob {

    public static final int NETWORK_ID = 21;
    public boolean sheared = false;
    private int nowBiomeId;

    public EntitySnowGolem(FullChunk fullChunk, CompoundTag compoundTag) {
        super(fullChunk, compoundTag);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        return 0.7f;
    }

    @Override
    public float getHeight() {
        return 1.9f;
    }

    @Override
    public void initEntity() {
        this.setFriendly(true);
        this.setMaxHealth(4);

        super.initEntity();

        this.noFallDamage = true;
        if (this.namedTag.getBoolean("Sheared")) {
            this.shear(true);
        }
        if (this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING)) {
            this.nowBiomeId = this.chunk.getBiomeId(this.getFloorX() & 0xF, this.getFloorZ() & 0xF);
        }
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        return (!(creature instanceof Player) || creature.getId() == this.isAngryTo) && creature.isAlive() && distance <= 100;
    }

    @Override
    public void attackEntity(Entity player) {
        if (this.attackDelay > 23 && Utils.rand(1, 32) < 4 && this.distanceSquared(player) <= 55) {
            this.attackDelay = 0;

            double f = 1.2;
            double yaw = this.yaw + Utils.rand(-4.0, 4.0);
            double pitch = this.pitch + Utils.rand(-4.0, 4.0);
            Location location = new Location(this.x + (-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5), this.y + this.getEyeHeight(),
                    this.z + (Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5), yaw, pitch, this.level);
            Entity k = Entity.createEntity("Snowball", location, this);
            if (k == null) {
                return;
            }

            EntitySnowball snowball = (EntitySnowball) k;
            snowball.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f, Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f).multiply(f));

            ProjectileLaunchEvent launch = new ProjectileLaunchEvent(snowball);
            this.server.getPluginManager().callEvent(launch);
            if (launch.isCancelled()) {
                if (this.stayTime > 0 || this.distance(this.target) <= ((this.getWidth()) / 2 + 0.05) * nearbyDistanceMultiplier()) {
                    snowball.close();
                }
            } else {
                snowball.spawnToAll();
                this.level.addSound(this, Sound.MOB_SNOWGOLEM_SHOOT);
            }
        }
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.SNOWBALL, 0, Utils.rand(0, 15))};
    }

    @Override
    public int getKillExperience() {
        return 0;
    }

    @Override
    public String getName() {
        return this.hasCustomName() ? this.getNameTag() : "Snow Golem";
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (this.age % 20 == 0 && (this.level.getDimension() == Level.DIMENSION_NETHER || (this.level.isRaining() && this.level.canBlockSeeSky(this)))) {
            this.attack(new EntityDamageEvent(this, EntityDamageEvent.DamageCause.FIRE_TICK, 1));
        }

        boolean hasUpdate = super.entityBaseTick(tickDiff);
        if (!this.closed && this.level.getGameRules().getBoolean(GameRule.MOB_GRIEFING) && this.age % 10 == 0) {
            if (this.age % 400 == 0) {
                this.nowBiomeId = this.chunk.getBiomeId(this.getFloorX() & 0xF, this.getFloorZ() & 0xF);
            }
            if (this.nowBiomeId != EnumBiome.DESERT.id && this.nowBiomeId != EnumBiome.HELL.id && this.nowBiomeId != EnumBiome.BAMBOO_JUNGLE.id &&
                    this.nowBiomeId != EnumBiome.JUNGLE_HILLS.id && this.nowBiomeId != EnumBiome.JUNGLE_EDGE.id && this.nowBiomeId != EnumBiome.MESA.id &&
                    this.nowBiomeId != EnumBiome.MESA_PLATEAU_F.id && this.nowBiomeId != EnumBiome.MESA_PLATEAU.id && this.nowBiomeId != EnumBiome.DESERT_M.id &&
                    this.nowBiomeId != EnumBiome.JUNGLE_M.id && this.nowBiomeId != EnumBiome.JUNGLE_EDGE_M.id && this.nowBiomeId != EnumBiome.MESA_BRYCE.id &&
                    this.nowBiomeId != EnumBiome.MESA_PLATEAU_F_M.id && this.nowBiomeId != EnumBiome.MESA_PLATEAU_M.id &&
                    this.level.getBlockIdAt(this.chunk, this.getFloorX(), this.getFloorY(), this.getFloorZ()) == 0 &&
                    !Block.transparent[this.level.getBlockIdAt(this.getFloorX(), this.getFloorY() - 1, this.getFloorZ())]) {
                this.level.setBlockAt(this.getFloorX(), this.getFloorY(), this.getFloorZ(), Block.SNOW, 0);
            }
        }
        return hasUpdate;
    }

    @Override
    public int nearbyDistanceMultiplier() {
        return 10;
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.SHEARS && !this.sheared) {
            this.shear(true);
            this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SHEAR);
            player.getInventory().getItemInHand().setDamage(item.getDamage() + 1);
            return true;
        }

        return super.onInteract(player, item, clickedPos);
    }

    public void shear(boolean shear) {
        this.sheared = shear;
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_SHEARED, shear);
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putBoolean("Sheared", this.isSheared());
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        if (super.attack(ev)) {
            if (ev instanceof EntityDamageByEntityEvent) {
                this.isAngryTo = ((EntityDamageByEntityEvent) ev).getDamager().getId();
            }
            return true;
        }

        return false;
    }

    @Override
    public boolean canTarget(Entity entity) {
        return entity.canBeFollowed() && entity.getId() == this.isAngryTo;
    }

    public boolean isSheared() {
        return this.sheared;
    }
}

