package cn.nukkit.scoreboard.scoreboard;

import cn.nukkit.scoreboard.scorer.IScorer;
import lombok.Getter;

@Getter
public class ScoreboardLine implements IScoreboardLine {

    protected static long staticLineId = 0;

    protected final IScoreboard scoreboard;
    protected final IScorer scorer;
    protected final long lineId;
    protected int score;

    public ScoreboardLine(IScoreboard scoreboard, IScorer scorer) {
        this(scoreboard, scorer, 0);
    }

    public ScoreboardLine(IScoreboard scoreboard, IScorer scorer, int score) {
        this.scoreboard = scoreboard;
        this.scorer = scorer;
        this.score = score;
        this.lineId = ++staticLineId;
    }

    @Override
    public boolean setScore(int score) {
        this.score = score;
        this.updateScore();
        return true;
    }
}
