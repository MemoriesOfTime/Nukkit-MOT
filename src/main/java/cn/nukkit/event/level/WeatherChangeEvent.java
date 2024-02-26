package cn.nukkit.event.level;

import cn.nukkit.event.Cancellable;
import cn.nukkit.event.HandlerList;
import cn.nukkit.level.Level;

/**
 * @author funcraft
 * Nukkit Project
 */
public class WeatherChangeEvent extends WeatherEvent implements Cancellable {

    private static final HandlerList handlers = new HandlerList();

    private final boolean to;
    private int intensity;

    public static HandlerList getHandlers() {
        return handlers;
    }

    @Deprecated
    public WeatherChangeEvent(Level level, boolean to) {
        this(level, to, 10000);
    }

    public WeatherChangeEvent(Level level, boolean to, int intensity) {
        super(level);
        this.to = to;
        this.intensity = intensity;
    }

    /**
     * Gets the state of weather that the world is being set to
     *
     * @return true if the weather is being set to raining, false otherwise
     */
    public boolean toWeatherState() {
        return to;
    }

    /**
     * Gets the intensity of the weather that the world is being set to
     *
     * @return the intensity of the weather
     */
    public int getIntensity() {
        return intensity;
    }

    /**
     * Sets the intensity of the weather that the world is being set to
     *
     * @param intensity the intensity of the weather
     */
    public void setIntensity(int intensity) {
        this.intensity = intensity;
    }
}
