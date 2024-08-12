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
    public static final int TYPE_TITLE_JSON = 6;
    public static final int TYPE_SUBTITLE_JSON = 7;
    public static final int TYPE_ACTIONBAR_JSON = 8;

    public int type;
    public String text = "";
    public int fadeInTime = 0;
    public int stayTime = 0;
    public int fadeOutTime = 0;
    private String xuid = "";
    private String platformOnlineId = "";

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
        if (protocol >= ProtocolInfo.v1_17_10) {
            this.xuid = this.getString();
            this.platformOnlineId = this.getString();
        }
    }

    @Override
    public void encode() {
        this.reset();
        this.putVarInt(type);
        this.putString(text);
        this.putVarInt(fadeInTime);
        this.putVarInt(stayTime);
        this.putVarInt(fadeOutTime);
        if (protocol >= ProtocolInfo.v1_17_10) {
            this.putString(xuid);
            this.putString(platformOnlineId);
        }
    }
}
