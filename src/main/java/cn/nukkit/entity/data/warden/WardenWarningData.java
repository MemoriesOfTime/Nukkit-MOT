package cn.nukkit.entity.data.warden;

/**
 * Tracks a player's warden warning state. Adapted from PowerNukkitX.
 */
public class WardenWarningData {

    public int warningLevel = 0;
    public long lastWarningTick = 0;
    public long lastShriekTick = 0;
}
