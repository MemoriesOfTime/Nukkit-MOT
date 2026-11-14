package cn.nukkit.network.protocol;

import cn.nukkit.network.protocol.types.MusicPlaybackFailureEntry;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import lombok.ToString;

import java.util.List;

/**
 * 告知客户端哪些唱片无法播放及其原因。
 * <p>
 * Reports music discs that could not play so the client can classify their failures.
 *
 * @since v2250 1.26.70.23
 */
@ToString
public class ClientboundMusicPlaybackFailurePacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.CLIENTBOUND_MUSIC_PLAYBACK_FAILURE_PACKET;

    public final List<MusicPlaybackFailureEntry> failures = new ObjectArrayList<>();

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
        this.putArray(this.failures, MusicPlaybackFailureEntry::put);
    }
}
