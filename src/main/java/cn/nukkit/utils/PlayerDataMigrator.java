package cn.nukkit.utils;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.*;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Moves a player's saved data when the way their identity UUID is derived changes.
 * <p>
 * Unauthenticated players used to be identified by whatever UUID their client reported, which
 * differed between the legacy certificate chain and the v1.21.90+ token login. Their identity is
 * now derived from the player name, so existing data files have to follow them over.
 */
@Log4j2
public final class PlayerDataMigrator {

    public enum Result {
        MIGRATED,
        /**
         * 无需迁移：没有旧数据，或旧身份数据不可移交 Nothing to migrate, or the old data must not be handed out
         */
        SKIPPED,
        /**
         * 迁移失败：旧数据完好，下次登录应重试 Failed while the old data is intact; retry on the next login
         */
        FAILED
    }

    @FunctionalInterface
    interface FileMover {
        Path move(Path source, Path target, CopyOption... options) throws IOException;
    }

    private PlayerDataMigrator() {
    }

    /**
     * Identities every unauthenticated v1.21.90+ login collapsed onto before the XUID fallback
     * was fixed: {@code nameUUIDFromBytes("pocket-auth-1-xuid:" + xid)} with a {@code null} and
     * an empty XUID. The data files behind them are shared by an unknown number of players.
     */
    private static final Set<UUID> COLLAPSED_IDENTITIES = Set.of(
            UUID.fromString("3c14031c-69c0-30bb-8339-5ee69712b3e5"),
            UUID.fromString("13cec93e-ba60-30df-9f67-b97138b8516d")
    );

    /**
     * Migrate data only when the server has deliberately replaced an unauthenticated identity.
     * Authenticated identities belong to an Xbox account and must never inherit data merely
     * because a stale name lookup points at another account.
     *
     * @param previousSerializer serializer selected for the previous identity
     * @param currentSerializer  serializer selected for the current identity
     * @param previous           identity the player was last seen under
     * @param current            identity the player logged in with
     * @param xboxAuthed         whether the current login is Xbox authenticated
     */
    public static Result migrate(PlayerDataSerializer previousSerializer,
                                 PlayerDataSerializer currentSerializer,
                                 UUID previous,
                                 UUID current,
                                 boolean xboxAuthed) {
        if (xboxAuthed) {
            return Result.SKIPPED;
        }
        if (previous == null || current == null || previous.equals(current)) {
            return Result.SKIPPED;
        }
        if (COLLAPSED_IDENTITIES.contains(previous)) {
            log.warn("Not migrating player data from {}: that identity was shared by every unauthenticated player", previous);
            return Result.SKIPPED;
        }
        if (previousSerializer == currentSerializer
                && previousSerializer instanceof DefaultPlayerDataSerializer defaultSerializer) {
            return migrate(defaultSerializer.getPlayersDirectory(), previous, current);
        }

        String previousName = previous.toString();
        String currentName = current.toString();
        boolean targetOpened = false;
        try {
            Optional<InputStream> currentData = currentSerializer.read(currentName, current);
            if (currentData.isPresent()) {
                currentData.get().close();
                return Result.SKIPPED;
            }

            Optional<InputStream> previousData = previousSerializer.read(previousName, previous);
            if (previousData.isEmpty()) {
                return Result.SKIPPED;
            }

            // 先整体读入再写目标：读取中途失败不会留下半成品
            // Buffer the source fully before writing so a read failure leaves no partial target
            byte[] data;
            try (InputStream input = previousData.get()) {
                data = input.readAllBytes();
            }

            targetOpened = true;
            try (OutputStream output = currentSerializer.write(currentName, current)) {
                output.write(data);
            }
            log.info("Migrated player data from {} to {} through {}", previous, current,
                    currentSerializer.getClass().getSimpleName());
            return Result.MIGRATED;
        } catch (Exception e) {
            log.warn("Failed to migrate serialized player data from {} to {}", previous, current, e);
            if (targetOpened) {
                deletePartialTarget(currentSerializer, current);
            }
            return Result.FAILED;
        }
    }

    /**
     * Rename {@code <previous>.dat} to {@code <current>.dat}.
     *
     * @param playersDir directory holding the player data files
     * @param previous   identity the player was last seen under
     * @param current    identity the player logged in with
     */
    public static Result migrate(File playersDir, UUID previous, UUID current) {
        return migrate(playersDir, previous, current, Files::move);
    }

    static Result migrate(File playersDir, UUID previous, UUID current, FileMover mover) {
        if (previous == null || current == null || previous.equals(current)) {
            return Result.SKIPPED;
        }
        if (COLLAPSED_IDENTITIES.contains(previous)) {
            log.warn("Not migrating player data from {}: that file was shared by every unauthenticated player", previous);
            return Result.SKIPPED;
        }

        File previousFile = new File(playersDir, previous + ".dat");
        File currentFile = new File(playersDir, current + ".dat");
        if (!previousFile.isFile() || currentFile.exists()) {
            return Result.SKIPPED;
        }

        try {
            try {
                mover.move(previousFile.toPath(), currentFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                mover.move(previousFile.toPath(), currentFile.toPath());
            }
            log.info("Migrated player data from {} to {}", previous, current);
            return Result.MIGRATED;
        } catch (Exception e) {
            log.warn("Failed to migrate player data from {} to {}", previous, current, e);
            // currentFile 此时不可能是既有数据（存在则上方已跳过），删除失败移动的残留
            // currentFile cannot be pre-existing data (that skips above), so remove the failed move's remains
            if (!currentFile.delete() && currentFile.exists()) {
                log.warn("Failed to delete partially migrated player data {}", currentFile);
            }
            return Result.FAILED;
        }
    }

    /**
     * 清理失败迁移留下的半成品目标。仅默认序列化器暴露可删除的文件；自定义序列化器没有删除
     * 契约，其输出流在关闭时可自行回滚。
     * <p>
     * Remove a partially written target. Only the default serializer exposes a deletable file;
     * custom serializers have no delete contract and may roll back when their stream is closed.
     */
    private static void deletePartialTarget(PlayerDataSerializer serializer, UUID current) {
        if (serializer instanceof DefaultPlayerDataSerializer defaultSerializer) {
            File partial = new File(defaultSerializer.getPlayersDirectory(), current + ".dat");
            if (partial.exists() && !partial.delete()) {
                log.warn("Failed to delete partially written player data {}", partial);
            }
        }
    }
}
