package cn.nukkit.network.protocol;

import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import lombok.ToString;

import java.util.Map;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@ToString
public class GameRulesChangedPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.GAME_RULES_CHANGED_PACKET;
    public GameRules gameRules;
    public Map<GameRule, GameRules.Value> gameRulesMap;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
    }

    @Override
    public void encode() {
        this.reset();
        if (gameRulesMap == null) { // For compatibility
            putGameRules(protocol, gameRules);
        } else {
            putGameRulesMap(protocol, gameRulesMap);
        }
    }
}
