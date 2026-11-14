package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.MusicPlaybackState;
import lombok.ToString;

/**
 * 回复 {@link ServerboundRequestOnlineMusicPlaybackPacket} 的在线音乐播放信息。
 * <p>
 * Online music playback information answering {@link ServerboundRequestOnlineMusicPlaybackPacket}.
 *
 * @since v2238 1.26.70.21
 */
@ToString
public class ClientboundOnlineMusicPlaybackResponsePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_ONLINE_MUSIC_PLAYBACK_RESPONSE_PACKET;

    public int requestId;
    public MusicPlaybackState state = MusicPlaybackState.LOCAL;
    public String sharedMetadata = "";
    public String playbackContent = "";

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
        this.decodeUnsupported();
    }

    @Override
    public void encode() {
        this.reset();
        this.putLInt(this.requestId);
        this.putString(this.state.getName());
        this.putString(this.sharedMetadata);
        this.putString(this.playbackContent);
    }
}
