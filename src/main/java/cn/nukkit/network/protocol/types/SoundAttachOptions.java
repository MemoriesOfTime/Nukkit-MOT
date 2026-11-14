package cn.nukkit.network.protocol.types;

import cn.nukkit.utils.BinaryStream;
import lombok.ToString;

/**
 * PlaySound 的可选挂载目标（v2259 取代裸 Entity To Attach To 字段）。
 * <p>
 * Optional attach target for PlaySound (replaces the bare Entity To Attach To field since v2259).
 *
 * @since v2259 1.26.70
 */
@ToString
public class SoundAttachOptions {

    public long entityRuntimeId;

    public SoundAttachOptions() {
    }

    public SoundAttachOptions(long entityRuntimeId) {
        this.entityRuntimeId = entityRuntimeId;
    }

    public static void put(BinaryStream stream, SoundAttachOptions options) {
        stream.putEntityRuntimeId(options.entityRuntimeId);
    }

    public static SoundAttachOptions get(BinaryStream stream) {
        return new SoundAttachOptions(stream.getEntityRuntimeId());
    }
}
