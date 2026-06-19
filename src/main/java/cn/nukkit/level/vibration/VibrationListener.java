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
     * emitted. The manager then either schedules a delayed {@link VibrationArriveEvent} itself, or
     * (when {@link #handleArrivalSelf()} is true) the listener fires it on arrival.
     */
    boolean onVibrationOccur(VibrationEvent event);

    /**
     * Called when the vibration signal arrives and the manager scheduled it (i.e. when
     * {@link #handleArrivalSelf()} is false). Listeners that self-schedule arrival will NOT have
     * this invoked by the manager, but still fire {@link VibrationArriveEvent}.
     */
    void onVibrationArrive(VibrationEvent event);

    /**
     * If true, this listener schedules its own arrival (e.g. via a per-tick countdown) and the
     * manager will NOT schedule the delayed {@link #onVibrationArrive} call. The listener is still
     * responsible for firing {@link VibrationArriveEvent} so plugins can cancel the reaction.
     * Allows persistence of in-flight vibrations and single-flight enforcement. Default false.
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
