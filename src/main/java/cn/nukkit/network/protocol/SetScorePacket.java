package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.ScorerType;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@ToString
public class SetScorePacket extends DataPacket {

    public Action action;
    public List<ScoreInfo> infos = new ArrayList<>();

    @Override
    public byte pid() {
        return ProtocolInfo.SET_SCORE_PACKET;
    }

    /**
     * v2168 wire type-name literals indexed by the v2168 entry type ordinal.
     * Index order: 0=REMOVE, 1=CHANGE_PLAYER, 2=CHANGE_ENTITY, 3=CHANGE_FAKE_PLAYER.
     * v2168 线上类型名称字面量，按下标对应条目类型序号：
     * 0=移除，1=变更玩家，2=变更实体，3=变更假玩家。
     */
    private static final String[] V2168_TYPE_NAMES = {"remove", "changeplayer", "changeentity", "changefakeplayer"};

    /**
     * Maps an internal {@link ScorerType} to the v2168 entry-type ordinal when the
     * action is SET, or returns 0 (REMOVE) when the action is REMOVE.
     * 将内部 {@link ScorerType} 在 SET 动作下映射为 v2168 条目类型序号；
     * 若动作为 REMOVE，则返回 0（移除）。
     */
    private static int v2168TypeOrdinal(Action action, ScorerType type) {
        if (action == Action.REMOVE) {
            return 0; // REMOVE
        }
        return switch (type) {
            case PLAYER -> 1;       // CHANGE_PLAYER
            case ENTITY -> 2;       // CHANGE_ENTITY
            case FAKE -> 3;         // CHANGE_FAKE_PLAYER
            default -> throw new IllegalArgumentException("Invalid score info received");
        };
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        if (this.protocol >= ProtocolInfo.v1_26_40) {
            this.putUnsignedVarInt(this.infos.size());
            for (ScoreInfo info : this.infos) {
                int typeOrdinal = v2168TypeOrdinal(this.action, info.type);
                this.putUnsignedVarInt(typeOrdinal);
                this.putString(V2168_TYPE_NAMES[typeOrdinal]);
                this.putVarLong(info.scoreboardId);
                switch (typeOrdinal) {
                    case 0 -> { // REMOVE / INVALID
                        String objectiveId = info.objectiveId;
                        boolean present = objectiveId != null && !objectiveId.isEmpty();
                        this.putBoolean(present);
                        if (present) {
                            if (this.protocol >= ProtocolInfo.v1_26_44) {
                                this.putBoolean(true);
                            }
                            this.putString(objectiveId);
                        }
                    }
                    case 1, 2 -> { // CHANGE_PLAYER / CHANGE_ENTITY
                        this.putString(info.objectiveId == null || info.objectiveId.isEmpty() ? " " : info.objectiveId);
                        this.putLInt(info.score);
                        this.putVarLong(info.entityId);
                    }
                    case 3 -> {    // CHANGE_FAKE_PLAYER
                        this.putString(info.objectiveId == null || info.objectiveId.isEmpty() ? " " : info.objectiveId);
                        this.putLInt(info.score);
                        this.putString(info.name == null || info.name.isEmpty() ? " " : info.name);
                    }
                    default -> {
                    }
                }
            }
        } else {
            this.putByte((byte) this.action.ordinal());
            this.putUnsignedVarInt(this.infos.size());

            for (ScoreInfo info : this.infos) {
                this.putVarLong(info.scoreboardId);
                this.putString(info.objectiveId);
                this.putLInt(info.score);
                if (this.action == Action.SET) {
                    this.putByte((byte) info.type.ordinal());
                    switch (info.type) {
                        case ENTITY, PLAYER -> this.putVarLong(info.entityId);
                        case FAKE -> this.putString(info.name);
                        default -> throw new IllegalArgumentException("Invalid score info received");
                    }
                }
            }
        }
    }

    public enum Action {
        SET,
        REMOVE
    }

    @ToString
    public static class ScoreInfo {
        public long scoreboardId;
        public String objectiveId;
        public int score;
        public ScorerType type;
        public String name;
        public long entityId;

        public ScoreInfo(long scoreboardId, String objectiveId, int score) {
            this.scoreboardId = scoreboardId;
            this.objectiveId = objectiveId;
            this.score = score;
            this.type = ScorerType.INVALID;
            this.name = null;
            this.entityId = -1;
        }

        public ScoreInfo(long scoreboardId, String objectiveId, int score, String name) {
            this.scoreboardId = scoreboardId;
            this.objectiveId = objectiveId;
            this.score = score;
            this.type = ScorerType.FAKE;
            this.name = name;
            this.entityId = -1;
        }

        public ScoreInfo(long scoreboardId, String objectiveId, int score, ScorerType type, long entityId) {
            this.scoreboardId = scoreboardId;
            this.objectiveId = objectiveId;
            this.score = score;
            this.type = type;
            this.entityId = entityId;
            this.name = null;
        }
    }
}
