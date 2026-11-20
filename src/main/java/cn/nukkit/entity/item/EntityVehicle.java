package cn.nukkit.entity.item;

import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityInteractable;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.data.IntEntityData;
import cn.nukkit.event.entity.EntityDamageByEntityEvent;
import cn.nukkit.event.entity.EntityDamageEvent;
import cn.nukkit.event.vehicle.VehicleDamageEvent;
import cn.nukkit.event.vehicle.VehicleDestroyEvent;
import cn.nukkit.event.vehicle.VehicleMoveEvent;
import cn.nukkit.event.vehicle.VehicleUpdateEvent;
import cn.nukkit.level.Level;
import cn.nukkit.level.Location;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.nbt.tag.CompoundTag;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public abstract class EntityVehicle extends Entity implements EntityRideable, EntityInteractable {

    private int hurtTime;
    private int hurtDirection;
    private int damage;

    public EntityVehicle(FullChunk chunk, CompoundTag nbt) {
        super(chunk, nbt);
    }

    public int getRollingAmplitude() {
        return hurtTime;
    }

    public void setRollingAmplitude(int time) {
        this.hurtTime = time;
        this.setDataProperty(new IntEntityData(DATA_HURT_TIME, time));
    }

    public int getRollingDirection() {
        return hurtDirection;
    }

    public void setRollingDirection(int direction) {
        this.hurtDirection = direction;
        this.setDataProperty(new IntEntityData(DATA_HURT_DIRECTION, direction));
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
        this.setDataProperty(new IntEntityData(DATA_HEALTH, damage)); // false data name (should be DATA_DAMAGE_TAKEN)
    }

    @Override
    public String getInteractButtonText() {
        return "action.interact.mount";
    }

    @Override
    public boolean canDoInteraction() {
        return passengers.isEmpty();
    }

    @Override
    public boolean entityBaseTick(int tickDiff) {
        if (getRollingAmplitude() > 0) {
            setRollingAmplitude(getRollingAmplitude() - 1);
        }

        return super.entityBaseTick(tickDiff);
    }

    /** Keeps the pre-update movement snapshot without allocating it for an empty handler list. */
    final void dispatchVehicleMovementEvents() {
        double fromX = this.lastX;
        double fromY = this.lastY;
        double fromZ = this.lastZ;
        double fromYaw = this.lastYaw;
        double fromPitch = this.lastPitch;
        Level fromLevel = this.level;
        double toX = this.x;
        double toY = this.y;
        double toZ = this.z;
        double toYaw = this.yaw;
        double toPitch = this.pitch;
        Level toLevel = this.level;

        if (VehicleUpdateEvent.getHandlers().getRegisteredListeners().length != 0) {
            this.getServer().getPluginManager().callEvent(new VehicleUpdateEvent(this));
        }

        // An update listener may move the vehicle or register/unregister a move listener.
        // Recheck subscriptions after Update, but use the original coordinates and level.
        if (VehicleMoveEvent.getHandlers().getRegisteredListeners().length != 0) {
            Location from = new Location(fromX, fromY, fromZ, fromYaw, fromPitch, fromLevel);
            Location to = new Location(toX, toY, toZ, toYaw, toPitch, toLevel);
            if (!from.equals(to)) {
                this.getServer().getPluginManager().callEvent(new VehicleMoveEvent(this, from, to));
            }
        }
    }

    protected boolean rollingDirection = true;

    protected boolean performHurtAnimation() {
        setRollingAmplitude(9);
        setRollingDirection(rollingDirection ? 1 : -1);
        rollingDirection = !rollingDirection;
        return true;
    }

    @Override
    public boolean attack(EntityDamageEvent source) {
        VehicleDamageEvent event = new VehicleDamageEvent(this, source.getEntity(), source.getFinalDamage());
        getServer().getPluginManager().callEvent(event);
        if (event.isCancelled()) {
            return false;
        }

        boolean instantKill = false;

        if (source instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) source).getDamager();
            instantKill = damager instanceof Player && ((Player) damager).isCreative();
        }

        if (instantKill || getHealth() - source.getFinalDamage() < 1) {
            VehicleDestroyEvent event2 = new VehicleDestroyEvent(this, source.getEntity());
            getServer().getPluginManager().callEvent(event2);

            if (event2.isCancelled()) {
                return false;
            }
        }

        if (instantKill) {
            source.setDamage(1000);
        }

        return super.attack(source);
    }
}