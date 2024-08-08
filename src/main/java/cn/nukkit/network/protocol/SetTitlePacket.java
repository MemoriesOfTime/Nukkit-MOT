package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * @author Tee7even
 */
@ToString
public class SetTitlePacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.SET_TITLE_PACKET;

    public static final int TYPE_CLEAR = 0;
    public static final int TYPE_RESET = 1;
    public static final int TYPE_TITLE = 2;
    public static final int TYPE_SUBTITLE = 3;
    public static final int TYPE_ACTION_BAR = 4;
    public static final int TYPE_ANIMATION_TIMES = 5;

    public int type;
    public String text = "";
    public int fadeInTime = 0;
    public int stayTime = 0;
    public int fadeOutTime = 0;
    public String xuid = "";
    public String platformOnlineId = "";
    /**
     * @since v712
     */
    public String filteredTitleText = "";

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.type = this.getVarInt();
        this.text = this.getString();
        this.fadeInTime = this.getVarInt();
        this.stayTime = this.getVarInt();
        this.fadeOutTime = this.getVarInt();
        if (this.protocol >= ProtocolInfo.v1_17_10) {
            this.xuid = this.getString();
            this.platformOnlineId = this.getString();
            if (this.protocol >= ProtocolInfo.v1_21_20) {
                this.filteredTitleText = this.getString();
            }
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(this.type);
        this.putString(this.text);
        this.putVarInt(this.fadeInTime);
        this.putVarInt(this.stayTime);
        this.putVarInt(this.fadeOutTime);
        if (this.protocol >= ProtocolInfo.v1_17_10) {
            this.putString(this.xuid);
            this.putString(this.platformOnlineId);
            if (this.protocol >= ProtocolInfo.v1_21_20) {
                this.putString(this.filteredTitleText);
            }
        }
    }
}
