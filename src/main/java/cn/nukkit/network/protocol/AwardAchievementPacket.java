package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @since 685
 */
@ToString
public class AwardAchievementPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.AWARD_ACHIEVEMENT_PACKET;

    public int achievementId;

    @Override
    public void decode() {
        this.achievementId = this.getLInt();
    }

    @Override
    public void encode() {
        this.putLInt(achievementId);
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }
}
