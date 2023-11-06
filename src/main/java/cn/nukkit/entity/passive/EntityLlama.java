package cn.nukkit.entity.passive;

import cn.nukkit.Player;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityCreature;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.projectile.EntityLlamaSpit;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.ProjectileLaunchEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.Utils;
import org.apache.commons.math3.util.FastMath;

public class EntityLlama extends EntityHorseBase {

    public static final int NETWORK_ID = 29;

    private int variant;

    private static final int[] VARIANTS = {0, 1, 2, 3};

    private int attackTicks;
    private Entity damagedBy;

    public EntityLlama(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    @Override
    public int getNetworkId() {
        return NETWORK_ID;
    }

    @Override
    public float getWidth() {
        if (this.isBaby()) {
            return 0.45f;
        }
        return 0.9f;
    }

    @Override
    public float getHeight() {
        if (this.isBaby()) {
            return 0.935f;
        }
        return 1.87f;
    }

    @Override
    public boolean canBeSaddled() {
        return false;
    }

    @Override
    public void initEntity() {
        this.setMaxHealth(15);

        super.initEntity();

        if (this.namedTag.contains("Variant")) {
            this.variant = this.namedTag.getInt("Variant");
        } else {
            this.variant = getRandomVariant();
        }

        this.setDataProperty(new IntEntityData(DATA_VARIANT, this.variant));
    }

    @Override
    public void saveNBT() {
        super.saveNBT();
        this.namedTag.putInt("Variant", this.variant);
    }

    @Override
    public boolean attack(EntityDamageEvent ev) {
        super.attack(ev);

        if (ev instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) ev).getDamager();
            if (damager instanceof Player && (((Player) damager).isSurvival() || ((Player) damager).isAdventure())) {
                if (this.attackTicks <= 0) {
                    this.attackTicks = 60;
                    this.damagedBy = damager;
                }
            }
        }

        return true;
    }

    @Override
    public boolean onUpdate(int currentTick) {
        if (!this.closed) {
            if (this.attackTicks > 0) {
                this.attackTicks--;
                this.moveTime = 0;
                this.stayTime = 60;
                if (this.damagedBy != null) {
                    double x = this.damagedBy.x - this.x;
                    double z = this.damagedBy.z - this.z;
                    double diff = Math.abs(x) + Math.abs(z);
                    this.yaw = FastMath.toDegrees(-FastMath.atan2(x / diff, z / diff));
                    if (this.attackTicks == 0) {
                        if (this.distanceSquared(this.damagedBy) < 100) {
                            double f = 2;
                            double yaw = this.yaw;
                            double pitch = this.pitch;
                            Location pos = new Location(this.x - Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, this.y + this.getEyeHeight(),
                                    this.z + Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * 0.5, yaw, pitch, this.level);
                            Entity k = Entity.createEntity("LlamaSpit", pos, this);
                            if (k instanceof EntityLlamaSpit spit) {
                                spit.setMotion(new Vector3(-Math.sin(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f, -Math.sin(FastMath.toRadians(pitch)) * f * f,
                                        Math.cos(FastMath.toRadians(yaw)) * Math.cos(FastMath.toRadians(pitch)) * f * f));
                                ProjectileLaunchEvent launch = new ProjectileLaunchEvent(spit);
                                this.server.getPluginManager().callEvent(launch);
                                if (launch.isCancelled()) {
                                    spit.close();
                                } else {
                                    spit.spawnToAll();
                                    this.level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_SHOOT, -1, "minecraft:llama", false, false);
                                }
                            }
                        }
                    }
                }
            }
        }
        return super.onUpdate(currentTick);
    }

    @Override
    public Item[] getDrops() {
        return new Item[]{Item.get(Item.LEATHER, 0, Utils.rand(0, 2))};
    }

    @Override
    public boolean targetOption(EntityCreature creature, double distance) {
        boolean canTarget = super.targetOption(creature, distance);

        if (canTarget) {
            if (this.isInLove()) {
                return creature instanceof BaseEntity && ((BaseEntity) creature).isInLove() && creature.isAlive() && !creature.closed && creature.getNetworkId() == this.getNetworkId() && distance <= 100;
            }else if (creature instanceof Player player) {
                return player.spawned && player.isAlive() && !player.closed &&
                        this.isFeedItem(player.getInventory().getItemInHandFast()) && distance <= 40;
            }
        }

        return false;
    }

    @Override
    public boolean isFeedItem(Item item) {
        return item.getId() == Item.WHEAT;
    }

    @Override
    public void onPlayerInput(Player player, double strafe, double forward) {
        // can't be controlled
    }

    private static int getRandomVariant() {
        return VARIANTS[Utils.rand(0, VARIANTS.length - 1)];
    }
}
