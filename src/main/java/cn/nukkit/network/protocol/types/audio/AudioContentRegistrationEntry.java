package cn.nukkit.network.protocol.types.audio;

import cn.nukkit.utils.BinaryStream;
import lombok.Value;

/**
 * @since v2225
 */
@Value
public class AudioContentRegistrationEntry {
    String audioContentId;
    String sharedMetadataJwt;
    String serverContentJwt;
    String playbackContentJwt;

    public static AudioContentRegistrationEntry read(BinaryStream stream) {
        return new AudioContentRegistrationEntry(
                stream.getString(36),
                stream.getString(32768),
                stream.getString(32768),
                stream.getString(32768)
        );
    }

    public static void write(BinaryStream stream, AudioContentRegistrationEntry entry) {
        stream.putString(entry.audioContentId);
        stream.putString(entry.sharedMetadataJwt);
        stream.putString(entry.serverContentJwt);
        stream.putString(entry.playbackContentJwt);
    }
}
