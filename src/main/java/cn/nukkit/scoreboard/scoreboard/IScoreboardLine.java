package cn.nukkit.scoreboard.scoreboard;

import cn.nukkit.network.protocol.SetScorePacket;
import cn.nukkit.scoreboard.scorer.IScorer;

public interface IScoreboardLine {

    IScorer getScorer();

    long getLineId();

    IScoreboard getScoreboard();

    int getScore();

    /**
     * Set score
     *
     * @param score score
     * @return whether it is successful (it will be false if the event is withdrawn)
     */
    boolean setScore(int score);

    /**
     * Increase score
     *
     * @param addition amount of addition
     * @return whether it is successful (it will be false if the event is withdrawn)
     */
    default boolean addScore(int addition) {
        return this.setScore(this.getScore() + addition);
    }

    /**
     * Reduce points
     *
     * @param reduction reduction amount
     * @return whether it is successful (it will be false if the event is withdrawn)
     */
    default boolean removeScore(int reduction) {
        return this.setScore(this.getScore() - reduction);
    }

    /**
     * Internal method <br>
     * Convert to network information
     *
     * @return network information
     */
    default SetScorePacket.ScoreInfo toNetworkInfo() {
        return this.getScorer().toNetworkInfo(this.getScoreboard(), this);
    }

    /**
     * Internal method
     * Notify the scoreboard object to update this row information
     */
    default void updateScore() {
        this.getScoreboard().updateScore(this);
    }
}
