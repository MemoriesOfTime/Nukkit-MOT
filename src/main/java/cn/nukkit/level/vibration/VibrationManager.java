package cn.nukkit.level.vibration;


/**
 * Vibration manager. Adapted from PowerNukkitX.
 */
public interface VibrationManager {
    void callVibrationEvent(VibrationEvent event);

    void addListener(VibrationListener listener);

    void removeListener(VibrationListener listener);
}
