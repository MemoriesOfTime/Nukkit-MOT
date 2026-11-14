package cn.nukkit.network.protocol.types;

/**
 * 在线音乐播放状态，线路上以字符串传输。
 * <p>
 * Online music playback state, carried as a string on the wire.
 *
 * @since v2250 1.26.70.23
 */
public enum MusicPlaybackState {
    LOCAL("Local"),
    ONLINE("Online"),
    NOT_FOUND("NotFound"),
    PENDING("Pending");

    private final String name;

    MusicPlaybackState(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public static MusicPlaybackState fromName(String name) {
        for (MusicPlaybackState state : values()) {
            if (state.name.equals(name)) {
                return state;
            }
        }
        return PENDING;
    }
}
