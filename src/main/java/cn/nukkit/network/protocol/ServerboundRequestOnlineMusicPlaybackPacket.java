package cn.nukkit.network.protocol;

import lombok.ToString;

/**
 * 客户端请求某个声音事件的在线音乐播放信息。
 * <p>
 * Client request for online music playback information of a sound event.
 *
 * @since v2238 1.26.70.21
 */
@ToString
public class ServerboundRequestOnlineMusicPlaybackPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.SERVERBOUND_REQUEST_ONLINE_MUSIC_PLAYBACK_PACKET;

    public int requestId;
    public String soundEventName = "";

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    public void decode() {
        this.requestId = this.getLInt();
        this.soundEventName = this.getString(256);
    }

    @Override
    public void encode() {
        this.encodeUnsupported();
    }
}
