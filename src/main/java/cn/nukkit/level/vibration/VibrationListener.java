package cn.nukkit.level.vibration;


import cn.nukkit.entity.Entity;
import cn.nukkit.math.Vector3;

/**
 * Vibration listener. Adapted from PowerNukkitX.
 */
public interface VibrationListener {

    /** Listener position. */
    Vector3 getListenerVector();

    /**
     * Whether this listener responds to the vibration. If true, a vibration signal particle is
     * emitted. By default {@link #onVibrationArrive} is then called by the manager after a delay
     * equal to the distance; listeners that return true from {@link #handleArrivalSelf()} take
     * over arrival timing themselves and {@link #onVibrationArrive} will NOT be called by the manager.
     */
    boolean onVibrationOccur(VibrationEvent event);

    /** Called when the vibration signal arrives. Skipped if {@link #handleArrivalSelf()} is true. */
    void onVibrationArrive(VibrationEvent event);

    /**
     * If true, this listener schedules its own arrival (e.g. via a per-tick countdown) and the
     * manager will NOT schedule the delayed {@link #onVibrationArrive} call. Allows persistence
     * of in-flight vibrations and single-flight enforcement. Default false.
     */
    default boolean handleArrivalSelf() {
        return false;
    }

    /** Listen range radius. */
    double getListenRange();

    /** If true, an entity-specific nbt tag is used for the signal particle; else treated as a block. */
    default boolean isEntity() {
        return this instanceof Entity;
    }

    /** The entity this listener represents, when {@link #isEntity()} is true. */
    default Entity asEntity() {
        return (Entity) this;
    }
}
