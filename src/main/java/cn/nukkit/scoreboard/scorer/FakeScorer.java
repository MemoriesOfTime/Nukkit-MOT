package cn.nukkit.scoreboard.scorer;

import cn.nukkit.network.protocol.SetScorePacket;
import cn.nukkit.network.protocol.types.ScorerType;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.scoreboard.IScoreboardLine;
import lombok.Getter;

@Getter
public class FakeScorer implements IScorer {

    private final String fakeName;

    public FakeScorer(String fakeName) {
        this.fakeName = fakeName;
    }

    @Override
    public ScorerType getScorerType() {
        return ScorerType.FAKE;
    }

    @Override
    public int hashCode() {
        return fakeName.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof FakeScorer fakeScorer) {
            return fakeScorer.fakeName.equals(fakeName);
        }
        return false;
    }

    @Override
    public String getName() {
        return fakeName;
    }

    @Override
    public SetScorePacket.ScoreInfo toNetworkInfo(IScoreboard scoreboard, IScoreboardLine line) {
        return new SetScorePacket.ScoreInfo(line.getLineId(), scoreboard.getObjectiveName(), line.getScore(), this.getFakeName());
    }
}
