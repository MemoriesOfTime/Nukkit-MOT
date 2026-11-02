package cn.nukkit.utils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Optional;
import java.util.UUID;

/**
 * A serializer that handles the player data saving.
 *
 * Use setPlayerDataSerializer() in Server to register it.
 */
public interface PlayerDataSerializer {

    /** Successful publication contract; none is a promise about remote storage or power-loss hardware. */
    enum WriteAcknowledgement {
        /** The selected custom provider accepted and closed its stream. */
        PROVIDER_ACK,
        /** Atomic native rename after file force; this provider has no directory-force API. */
        ATOMIC_FILE_SYNC,
        /** Atomic native rename after file force, followed by successful parent-directory force. */
        ATOMIC_FILE_AND_DIRECTORY_SYNC
    }


    /**
     * Reads player data from {@link InputStream} if the file exists otherwise it will create the default data.
     *
     * @param name name of player or {@link UUID} as {@link String}
     * @param uuid uuid of player. Could be null if name is used.
     * @return {@link InputStream} if the player data exists
     */
    Optional<InputStream> read(String name, UUID uuid) throws IOException;

    /**
     * Opts into profile read/decode on the bounded player-data read pool.
     * Selection events still run on MAIN. Legacy serializers retain their original read thread.
     */
    default boolean supportsConcurrentReads() { return false; }

    /**
     * Writes player data to given {@link OutputStream}.
     *
     * @param name name of player or {@link UUID} as {@link String}
     * @param uuid uuid of player. Could be null if name is used.
     * @return stream to write player data
     */
    OutputStream write(String name, UUID uuid) throws IOException;

    /**
     * 删除玩家数据，删除后 {@link #read} 应返回空。用于清理失败迁移的半成品目标；未实现或删除失败时，
     * 迁移写入会尝试直接覆盖目标。
     * <p>
     * Deletes player data; {@link #read} must return empty afterwards. Used to clean up a
     * partially written migration target; when unimplemented or the delete fails, the
     * migration write attempts to overwrite the target instead.
     *
     * @param name name of player or {@link UUID} as {@link String}
     * @param uuid uuid of player. Could be null if name is used.
     * @return true if data existed and was deleted
     */
    default boolean delete(String name, UUID uuid) throws IOException {
        return false;
    }
}
