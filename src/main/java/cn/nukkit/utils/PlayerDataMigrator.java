package cn.nukkit.utils;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.StringTag;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteOrder;
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
         * 无需迁移：没有旧数据、旧数据不可移交，或目标已有有效数据 Nothing to migrate, the old data must
         * not be handed out, or valid target data already exists
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
     * State of the migration target's storage, inspected before touching the source.
     */
    private enum TargetState {
        ABSENT,
        HAS_DATA,
        RESIDUE_REMOVED,
        UNDELETABLE_RESIDUE,
        INSPECT_FAILED
    }

    /**
     * Migrate data only when the server has deliberately replaced an unauthenticated identity.
     * Authenticated identities belong to an Xbox account and must never inherit data merely
     * because a stale name lookup points at another account.
     * <p>
     * The data travels through a parse-validated NBT round trip instead of a raw byte copy, so a
     * corrupt source is refused outright and memory usage matches a normal login load.
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
        switch (inspectTarget(currentSerializer, currentName, current)) {
            case HAS_DATA, UNDELETABLE_RESIDUE -> {
                return Result.SKIPPED;
            }
            case INSPECT_FAILED -> {
                return Result.FAILED;
            }
            default -> {
            }
        }

        try {
            Optional<InputStream> previousData = previousSerializer.read(previousName, previous);
            if (previousData.isEmpty()) {
                return Result.SKIPPED;
            }

            CompoundTag tag;
            try (InputStream input = previousData.get()) {
                tag = NBTIO.readCompressed(input);
            } catch (IOException | RuntimeException e) {
                log.warn("Not migrating corrupt player data from {}: {}", previous, e.toString());
                return Result.SKIPPED;
            }
            if (!isHandoverAllowed(tag, previous)) {
                return Result.SKIPPED;
            }

            try (OutputStream output = currentSerializer.write(currentName, current)) {
                NBTIO.writeGZIPCompressed(tag, output, ByteOrder.BIG_ENDIAN);
            }
            log.info("Migrated player data from {} to {} through {}", previous, current,
                    currentSerializer.getClass().getSimpleName());
            return Result.MIGRATED;
        } catch (Exception e) {
            log.warn("Failed to migrate serialized player data from {} to {}", previous, current, e);
            deletePartialTarget(currentSerializer, currentName, current);
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
        if (!previousFile.isFile()) {
            return Result.SKIPPED;
        }
        if (currentFile.exists()) {
            if (isParseableNbtFile(currentFile)) {
                return Result.SKIPPED;
            }
            // 上次失败迁移的半成品：清掉再迁移，否则会阻塞重试并触发静默重置
            // Failed-migration residue: remove it or it blocks the retry and silently resets the player
            if (!currentFile.delete()) {
                log.error("Corrupt player data migration target {} could not be deleted; skipping migration", currentFile);
                return Result.SKIPPED;
            }
        }

        CompoundTag sourceTag;
        try {
            sourceTag = readNbtFile(previousFile);
        } catch (IOException | RuntimeException e) {
            log.warn("Not migrating corrupt player data from {}: {}", previous, e.toString());
            return Result.SKIPPED;
        }
        if (!isHandoverAllowed(sourceTag, previous)) {
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
            // 仅当源仍完好时才清理半成品目标：绝不删除唯一剩余副本
            // Clean up the partial target only while the source is still intact: never delete
            // the only remaining copy
            if (previousFile.isFile() && !currentFile.delete() && currentFile.exists()) {
                log.warn("Failed to delete partially migrated player data {}", currentFile);
            }
            return Result.FAILED;
        }
    }

    /**
     * 校验目标存储状态：有效数据不覆盖；无法解析视为失败迁移残留，经删除契约清理，清不掉则保守跳过。
     * <p>
     * Inspects the target: genuine data is kept; unparseable data is failed-migration residue,
     * deleted through the serializer's contract or skipped conservatively when undeletable.
     */
    private static TargetState inspectTarget(PlayerDataSerializer serializer, String name, UUID uuid) {
        Optional<InputStream> existing;
        try {
            existing = serializer.read(name, uuid);
        } catch (IOException | RuntimeException e) {
            log.warn("Cannot inspect player data migration target {}: {}", uuid, e.toString());
            return TargetState.INSPECT_FAILED;
        }
        if (existing.isEmpty()) {
            return TargetState.ABSENT;
        }
        try (InputStream input = existing.get()) {
            NBTIO.readCompressed(input);
            return TargetState.HAS_DATA;
        } catch (IOException | RuntimeException e) {
            try {
                if (serializer.delete(name, uuid)) {
                    return TargetState.RESIDUE_REMOVED;
                }
            } catch (IOException deleteEx) {
                log.error("Failed to delete corrupt player data migration target {}", uuid, deleteEx);
                return TargetState.UNDELETABLE_RESIDUE;
            }
            log.error("Corrupt player data migration target {} cannot be deleted; skipping migration", uuid);
            return TargetState.UNDELETABLE_RESIDUE;
        }
    }

    /**
     * 认证账户的数据（带 XUID 字符串标记）不得移交离线身份；仅识别字符串 tag，插件可能存同名其他类型。
     * <p>
     * Authenticated-account data (XUID string marker) must not flow into an offline identity;
     * only string tags count — plugins may store unrelated data under the same name.
     */
    private static boolean isHandoverAllowed(CompoundTag tag, UUID previous) {
        if (tag.get("XUID") instanceof StringTag xuid && !xuid.data.isEmpty()) {
            log.warn("Not migrating player data from {}: it belongs to an Xbox authenticated account", previous);
            return false;
        }
        return true;
    }

    private static void deletePartialTarget(PlayerDataSerializer serializer, String name, UUID uuid) {
        try {
            serializer.delete(name, uuid);
        } catch (IOException e) {
            log.error("Failed to delete partially written player data for {}", uuid, e);
        }
    }

    private static CompoundTag readNbtFile(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            return NBTIO.readCompressed(input);
        }
    }

    private static boolean isParseableNbtFile(File file) {
        try {
            readNbtFile(file);
            return true;
        } catch (IOException | RuntimeException e) {
            return false;
        }
    }
}
