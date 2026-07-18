package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityExplosive;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.event.entity.EntityDamageByChildEntityEvent;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.entity.EntityExplosionPrimeEvent;
import cn.nukkit.event.vehicle.VehicleDamageEvent;
import cn.nukkit.event.vehicle.VehicleDestroyEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemMinecartTNT;
import cn.nukkit.level.Explosion;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.vibration.VibrationEvent;
import cn.nukkit.level.vibration.VibrationType;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.network.protocol.LevelSoundEventPacket;
import cn.nukkit.utils.MinecartType;
import cn.nukkit.utils.Utils;

import java.util.concurrent.ThreadLocalRandom;

/**
 * @author Adam Matthew [larryTheCoder]
 *
 * Nukkit Project.
 */
public class EntityMinecartTNT extends EntityMinecartAbstract implements EntityExplosive {

    public static final int NETWORK_ID = 97;
    private int fuse;

    public EntityMinecartTNT(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
        setName("Minecart with TNT");
    }

    @Override
    protected Block getDefaultDisplayBlock() {
        return Block.get(BlockID.TNT);
    }

    @Override
    public boolean isRideable() {
        return false;
    }

    @Override
    public String getInteractButtonText() {
        return "";
    }

    @Override
    public void initEntity() {
        super.initEntity();

        if (namedTag.contains("fuse")) {
            fuse = namedTag.getByte("fuse");
        } else {
            fuse = -1;
        }
        this.setDataFlag(DATA_FLAGS, DATA_FLAG_CHARGED, false);
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        boolean hasUpdate = super.entityBaseTick(tickDiff);

        if (!this.closed && this.isAlive() && fuse > 0) {
            if (fuse % 5 == 0) {
                setDataProperty(new IntEntityData(DATA_FUSE_LENGTH, fuse));
            }

            fuse -= tickDiff;

            if (fuse <= 0) {
                if (this.level.getGameRules().getBoolean(GameRule.TNT_EXPLODES)) {
                    this.explode(ThreadLocalRandom.current().nextInt(5));
                }
                this.close();
                return false;
            }
        }

        return hasUpdate;
    }

    @Override
    public void activate(int x, int y, int z, boolean flag) {
        level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IGNITE);
        this.fuse = 80;
    }

    /**
     * 模仿原版 {@code MinecartTNT#hurtServer}：被着火的箭（如火矢附魔弓射出的箭）击中时，
     * 会立即引爆 TNT 矿车，而不是仅造成普通伤害。
     * <p>
     * Mirrors vanilla {@code MinecartTNT#hurtServer}: a flaming arrow
     * (e.g. one fired from a Flame-enchanted bow) instantly detonates the TNT
     * minecart instead of merely damaging it.
     */
    @Override
    public boolean attack(EntityDamageEvent source) {
        EntityArrow ignitingProjectile = getIgnitingProjectile(source);
        boolean shouldExplode = ignitingProjectile != null
                && this.level.getGameRules().getBoolean(GameRule.TNT_EXPLODES);

        if (!shouldExplode) {
            return super.attack(source);
        }

        if (!this.processIgnitionDamage(source)) {
            return false;
        }

        double speedSqr = ignitingProjectile.motionX * ignitingProjectile.motionX
                + ignitingProjectile.motionY * ignitingProjectile.motionY
                + ignitingProjectile.motionZ * ignitingProjectile.motionZ;
        this.explode(speedSqr);
        return true;
    }

    private static EntityArrow getIgnitingProjectile(EntityDamageEvent source) {
        Entity direct = source instanceof EntityDamageByChildEntityEvent childEvent
                ? childEvent.getChild()
                : source instanceof EntityDamageByEntityEvent byEntity
                        ? byEntity.getDamager()
                        : null;
        return direct instanceof EntityArrow projectile && projectile.isOnFire() ? projectile : null;
    }

    private boolean processIgnitionDamage(EntityDamageEvent source) {
        if (invulnerable) {
            return false;
        }

        source.setDamage(source.getDamage() * 15);

        VehicleDamageEvent vehicleDamageEvent = new VehicleDamageEvent(
                this, source.getEntity(), source.getFinalDamage());
        getServer().getPluginManager().callEvent(vehicleDamageEvent);
        if (vehicleDamageEvent.isCancelled()) {
            return false;
        }

        boolean instantKill = false;
        if (source instanceof EntityDamageByEntityEvent damageByEntityEvent) {
            Entity damager = damageByEntityEvent.getDamager();
            instantKill = damager instanceof Player && ((Player) damager).isCreative();
        }

        if (instantKill || getHealth() - source.getFinalDamage() < 1) {
            VehicleDestroyEvent vehicleDestroyEvent = new VehicleDestroyEvent(this, source.getEntity());
            getServer().getPluginManager().callEvent(vehicleDestroyEvent);
            if (vehicleDestroyEvent.isCancelled()) {
                return false;
            }
        }

        if (instantKill) {
            source.setDamage(1000);
        }

        recalculateResistanceDamage(source);
        server.getPluginManager().callEvent(source);
        if (source.isCancelled()) {
            return false;
        }

        setLastDamageCause(source);
        if (source.getFinalDamage() > 0) {
            this.level.getVibrationManager().callVibrationEvent(new VibrationEvent(
                    source.getEntity(), new Vector3(this.x, this.y, this.z), VibrationType.ENTITY_DAMAGE));
        }
        return true;
    }

    @Override
    public void explode() {
        explode(0);
    }

    public void explode(double square) {
        double root = Math.sqrt(square);

        if (root > 5.0D) {
            root = 5.0D;
        }

        EntityExplosionPrimeEvent event = new EntityExplosionPrimeEvent(this, (4.0D + Utils.random.nextDouble() * 1.5D * root));
        server.getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return;
        }
        Explosion explosion = new Explosion(this, event.getForce(), this);
        if (event.isBlockBreaking()) {
            explosion.explodeA();
        }
        explosion.explodeB();
        this.close();
    }

    @Override
    public void dropItem() {
        if (this.lastDamageCause instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) this.lastDamageCause).getDamager();
            if (damager instanceof Player && ((Player) damager).isCreative()) {
                return;
            }
        }
        level.dropItem(this, new ItemMinecartTNT());
    }

    @Override
    public MinecartType getType() {
        return MinecartType.valueOf(3);
    }

    @Override
    public int getNetworkId() {
        return EntityMinecartTNT.NETWORK_ID;
    }

    @Override
    public void saveNBT() {
        super.saveNBT();

        super.namedTag.putInt("fuse", this.fuse);
    }

    @Override
    public boolean onInteract(Player player, Item item, Vector3 clickedPos) {
        if (item.getId() == Item.FLINT_AND_STEEL || item.getId() == Item.FIRE_CHARGE) {
            level.addLevelSoundEvent(this, LevelSoundEventPacket.SOUND_IGNITE);
            this.fuse = 80;
            return true;
        }

        return super.onInteract(player, item, clickedPos);
    }

    @Override
    public boolean mountEntity(Entity entity, byte mode) {
        return false;
    }
}
