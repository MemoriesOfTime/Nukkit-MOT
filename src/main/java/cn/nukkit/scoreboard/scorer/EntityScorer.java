package cn.nukkit.scoreboard.scorer;

import cn.nukkit.entity.Entity;
import cn.nukkit.network.protocol.SetScorePacket;
import cn.nukkit.network.protocol.types.ScorerType;
import cn.nukkit.scoreboard.scoreboard.IScoreboard;
import cn.nukkit.scoreboard.scoreboard.IScoreboardLine;
import lombok.Getter;

import java.util.UUID;

@Getter
public class EntityScorer implements IScorer {

    private final UUID entityUuid;

    public EntityScorer(UUID uuid) {
        this.entityUuid = uuid;
    }

    public EntityScorer(Entity entity) {
        this.entityUuid = entity.getUniqueId();
    }

    @Override
    public ScorerType getScorerType() {
        return ScorerType.ENTITY;
    }

    @Override
    public int hashCode() {
        return entityUuid.hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof EntityScorer entityScorer) {
            return entityUuid.equals(entityScorer.entityUuid);
        }
        return false;
    }

    @Override
    public String getName() {
        return entityUuid.toString();
    }

    @Override
    public SetScorePacket.ScoreInfo toNetworkInfo(IScoreboard scoreboard, IScoreboardLine line) {
        return new SetScorePacket.ScoreInfo(line.getLineId(), scoreboard.getObjectiveName(), line.getScore(), ScorerType.ENTITY, entityUuid.getMostSignificantBits());
    }
}
