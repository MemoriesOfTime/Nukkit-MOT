package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.GameType;
import lombok.ToString;

/**
 * @since v407
 */
@ToString
public class UpdatePlayerGameTypePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.UPDATE_PLAYER_GAME_TYPE_PACKET;

    public GameType gameType;
    public long entityId;
    /**
     * @since v671
     */
    public long tick;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.gameType = GameType.from(this.getVarInt());
        this.entityId = this.getVarLong();
        if (this.protocol >= ProtocolInfo.v1_20_80) {
            this.tick = (int) this.getUnsignedVarLong();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(this.gameType.ordinal());
        this.putVarLong(entityId);
        if (this.protocol >= ProtocolInfo.v1_20_80) {
            this.putUnsignedVarLong(this.tick);
        }
    }
}
