package cn.nukkit.network.protocol;

import lombok.ToString;

@ToString
public class VideoStreamConnectPacket extends DataPacket {

    public static final byte NETWORK_ID = ProtocolInfo.VIDEO_STREAM_CONNECT_PACKET;

    public static final byte ACTION_OPEN = 0;
    public static final byte ACTION_CLOSE = 1;

    public String address;
    public float screenshotFrequency;
    public byte action;
    public int width;
    public int height;

    @Override
    public byte pid() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putString(address);
        this.putLFloat(screenshotFrequency);
        this.putByte(action);
        if (protocol >= ProtocolInfo.v1_12_0) {
            this.putLInt(width);
            this.putLInt(height);
        }
    }
}
