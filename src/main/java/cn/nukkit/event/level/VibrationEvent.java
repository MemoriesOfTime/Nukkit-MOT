package cn.nukkit.event.level;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.Event;

/**
 * Base class for vibration-related plugin events. Adapted from PowerNukkitX.
 */
public abstract class VibrationEvent extends Event implements Cancellable {

    protected cn.nukkit.level.vibration.VibrationEvent vibrationEvent;

    public VibrationEvent(cn.nukkit.level.vibration.VibrationEvent vibrationEvent) {
        this.vibrationEvent = vibrationEvent;
    }

    public cn.nukkit.level.vibration.VibrationEvent getVibrationEvent() {
        return vibrationEvent;
    }
}
