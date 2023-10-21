package cn.nukkit.scoreboard.scoreboard;

import cn.nukkit.network.protocol.types.DisplaySlot;
import cn.nukkit.network.protocol.types.SortOrder;
import cn.nukkit.scoreboard.displayer.IScoreboardViewer;
import cn.nukkit.scoreboard.scorer.IScorer;
import javax.annotation.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Scoreboard object
 * Can be sent to any object that implements the {@link cn.nukkit.scoreboard.displayer.IScoreboardViewer} interface
 */
public interface IScoreboard {
    /**
     * @return The identification name of this scoreboard
     */
    String getObjectiveName();

    /**
     * @return The display name of this scoreboard
     */
    String getDisplayName();

    /**
     * @return the "criteria" for this scoreboard (eg: dummy)
     */
    String getCriteriaName();

    /**
     * @return The sorting rules for this scoreboard
     */
    SortOrder getSortOrder();

    /**
     * Set the sorting rules of the scoreboard
     *
     * @param order sorting rules
     */
    void setSortOrder(SortOrder order);

    /**
     * @return all viewers of this scoreboard
     */
    Set<IScoreboardViewer> getAllViewers();

    /**
     * @param slot target slot
     * @return The viewer for the target slot of this scoreboard
     */
    Set<IScoreboardViewer> getViewers(DisplaySlot slot);

    /**
     * Delete an viewer in the target slot of this scoreboard
     *
     * @param viewer target viewer
     * @param slot   target slot
     * @return whether deletion is successful
     */
    boolean removeViewer(IScoreboardViewer viewer, DisplaySlot slot);

    /**
     * Add an viewer to this scoreboard target slot
     *
     * @param viewer target viewer
     * @param slot   target slot
     * @return whether the addition was successful
     */
    boolean addViewer(IScoreboardViewer viewer, DisplaySlot slot);

    /**
     * Check if there is a specific viewer in the target slot of this scoreboard
     *
     * @param viewer target viewer
     * @param slot   target slot
     * @return whether it exists
     */
    boolean containViewer(IScoreboardViewer viewer, DisplaySlot slot);

    /**
     * @return all rows in this scoreboard
     */
    Map<IScorer, IScoreboardLine> getLines();

    /**
     * Shortcut interface provided for plug-ins <br>
     * Set the content of the scoreboard in List order (use FakeScorer as the tracking object) <br>
     * will overwrite all previous lines <br>
     *
     * @param lines the string content that needs to be set
     */
    void setLines(List<String> lines);

    /**
     * Set the content of the scoreboard in List order <br>
     * will overwrite all previous lines <br>
     *
     * @param lines the line content that needs to be set
     */
    void setLines(Collection<IScoreboardLine> lines);

    /**
     * Get the row corresponding to the tracking object on this scoreboard (if it exists)
     *
     * @param scorer tracking object
     * @return corresponding line
     */
    @Nullable
    IScoreboardLine getLine(IScorer scorer);

    /**
     * Add a row to this scoreboard
     *
     * @param line target line
     * @return whether the addition was successful
     */
    boolean addLine(IScoreboardLine line);

    /**
     * Add a row to this scoreboard
     *
     * @param scorer tracking object
     * @param score  score
     * @return whether the addition was successful
     */
    boolean addLine(IScorer scorer, int score);

    /**
     * Convenient scoreboard display interface provided for plug-ins
     *
     * @param text  FakeScorer name
     * @param score score
     * @return whether the addition was successful
     */
    boolean addLine(String text, int score);

    /**
     * Delete the row corresponding to the tracking object on this scoreboard (if it exists)
     *
     * @param scorer target tracking object
     * @return whether deletion is successful
     */
    boolean removeLine(IScorer scorer);

    /**
     * Delete all scoreboard rows
     *
     * @param send whether to send to the viewer
     * @return whether deletion is successful
     */
    boolean removeAllLine(boolean send);

    /**
     * Check whether the tracking object has a record on this scoreboard
     *
     * @param scorer target tracking object
     * @return whether it exists
     */
    boolean containLine(IScorer scorer);

    /**
     * Send new score to all viewers <br>
     *
     * @param update the row that needs to be updated
     */
    void updateScore(IScoreboardLine update);

    /**
     * Resend this scoreboard with row information to all viewers <br>
     * For example, this method is called after a large number of changes have been made to the scoreboard <br>
     * Can save a lot of bandwidth
     */
    void resend();
}
