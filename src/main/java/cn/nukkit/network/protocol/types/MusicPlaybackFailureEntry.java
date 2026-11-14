package cn.nukkit.network.protocol.types;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * 单个无法播放的唱片条目。
 * <p>
 * One music disc that could not be played.
 *
 * @since v2250 1.26.70.23
 */
@ToString
public class MusicPlaybackFailureEntry {

    public MusicPlaybackState state;
    public String description = "";

    public MusicPlaybackFailureEntry() {
    }

    public MusicPlaybackFailureEntry(MusicPlaybackState state, String description) {
        this.state = state;
        this.description = description;
    }

    public static void put(BinaryStream stream, MusicPlaybackFailureEntry entry) {
        stream.putString(entry.state.getName());
        stream.putString(entry.description);
    }

    public static MusicPlaybackFailureEntry get(BinaryStream stream) {
        return new MusicPlaybackFailureEntry(
                MusicPlaybackState.fromName(stream.getString(32)),
                stream.getString(65536)
        );
    }
}
