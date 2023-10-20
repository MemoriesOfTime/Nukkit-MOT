package cn.nukkit.scoreboard.manager;

import cn.nukkit.Player;
import cn.nukkit.entity.EntityLiving;
import cn.nukkit.scoreboard.data.DisplaySlot;
import cn.nukkit.scoreboard.displayer.IScoreboardViewer;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.storage.IScoreboardStorage;
import javax.annotation.Nullable;

import java.util.Map;
import java.util.Set;

public interface IScoreboardManager {
    /**
     * Add a scoreboard
     *
     * @param scoreboard target scoreboard.
     * @return whether the addition was successful (returns false if the scoreboard already exists or if the event was canceled).
     */
    boolean addScoreboard(IScoreboard scoreboard);

    /**
     * Remove a scoreboard
     *
     * @param scoreboard target scoreboard.
     * @return whether the removal was successful (returns false if the scoreboard doesn't exist or if the event was canceled).
     */
    boolean removeScoreboard(IScoreboard scoreboard);

    /**
     * Remove a scoreboard
     *
     * @param objectiveName target scoreboard identifier.
     * @return whether the removal was successful (returns false if the scoreboard doesn't exist or if the event was canceled).
     */
    boolean removeScoreboard(String objectiveName);

    /**
     * Get the scoreboard object (if it exists)
     *
     * @param objectiveName target scoreboard identifier
     * @return Scoreboard object
     */
    @Nullable
    IScoreboard getScoreboard(String objectiveName);

    /**
     * Get all scoreboard objects
     *
     * @return all scoreboard objects
     */
    Map<String, IScoreboard> getScoreboards();

    /**
     * Check if a specific scoreboard exists
     *
     * @param scoreboard specific scoreboard
     * @return whether it exists
     */
    boolean containScoreboard(IScoreboard scoreboard);

    /**
     * Check if a specific scoreboard exists
     *
     * @param name specific scoreboard identifier
     * @return whether it exists
     */
    boolean containScoreboard(String name);

    /**
     * Get display slot information
     *
     * @return display slot information
     */
    Map<DisplaySlot, IScoreboard> getDisplay();

    /**
     * Get the scoreboard for a specific display slot (if it exists)
     *
     * @param slot specific slot
     * @return Scoreboard object
     */
    @Nullable
    IScoreboard getDisplaySlot(DisplaySlot slot);

    /**
     * Set the scoreboard to be displayed in a specific slot.
     * If the 'scoreboard' parameter is null, clear the content of the specified slot
     *
     * @param slot       specific slot
     * @param scoreboard Scoreboard object
     */
    void setDisplay(DisplaySlot slot, @Nullable IScoreboard scoreboard);

    /**
     * Get all viewers
     *
     * @return all viewers
     */
    Set<IScoreboardViewer> getViewers();

    /**
     * Add a viewer
     *
     * @param viewer target viewer
     * @return whether the addition was successful
     */
    boolean addViewer(IScoreboardViewer viewer);

    /**
     * Remove a viewer (if it exists)
     *
     * @param viewer target viewer
     * @return whether the removal was successful
     */
    boolean removeViewer(IScoreboardViewer viewer);

    /**
     * Server-side internal method
     */
    void onPlayerJoin(Player player);

    /**
     * Server-side internal method
     */
    void beforePlayerQuit(Player player);

    /**
     * Server-side internal method.
     */
    void onEntityDead(EntityLiving entity);

    /**
     * Get the scoreboard storage instance
     *
     * @return storage instance
     */
    IScoreboardStorage getStorage();

    /**
     * Save scoreboard information through the storage
     */
    void save();

    /**
     * Reload scoreboard information from the storage
     */
    void read();
}
