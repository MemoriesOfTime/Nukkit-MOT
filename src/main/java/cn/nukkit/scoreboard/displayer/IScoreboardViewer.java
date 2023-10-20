package cn.nukkit.scoreboard.displayer;

import cn.nukkit.scoreboard.data.DisplaySlot;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.scoreboard.IScoreboardLine;

public interface IScoreboardViewer {
    /**
     * Display scoreboard in specified slot
     *
     * @param scoreboard target scoreboard
     * @param slot       target slot
     */
    void display(IScoreboard scoreboard, DisplaySlot slot);

    /**
     * Clear the displayed content of the specified slot
     *
     * @param slot target slot
     */
    void hide(DisplaySlot slot);

    /**
     * Notify viewers that the scoreboard has been deleted (if the scoreboard is in any display slot, the slot will be cleared as well)
     *
     * @param scoreboard target scoreboard
     */
    void removeScoreboard(IScoreboard scoreboard);

    /**
     * Notify observers that the specified row on the specified scoreboard has been deleted
     *
     * @param line target line
     */
    void removeLine(IScoreboardLine line);

    /**
     * Send the new score of the specified row to the viewer
     *
     * @param line target line
     */
    void updateScore(IScoreboardLine line);
}
