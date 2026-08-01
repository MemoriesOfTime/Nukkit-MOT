package cn.nukkit.utils;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
     * @return whether data was migrated
     */
    public static boolean migrate(PlayerDataSerializer previousSerializer,
                                  PlayerDataSerializer currentSerializer,
                                  UUID previous,
                                  UUID current,
                                  boolean xboxAuthed) {
        if (xboxAuthed) {
            return false;
        }
        if (previous == null || current == null || previous.equals(current)) {
            return false;
        }
        if (COLLAPSED_IDENTITIES.contains(previous)) {
            log.warn("Not migrating player data from {}: that identity was shared by every unauthenticated player", previous);
            return false;
        }
        if (previousSerializer == currentSerializer
                && previousSerializer instanceof DefaultPlayerDataSerializer defaultSerializer) {
            return migrate(defaultSerializer.getPlayersDirectory(), previous, current);
        }

        String previousName = previous.toString();
        String currentName = current.toString();
        try {
            Optional<InputStream> currentData = currentSerializer.read(currentName, current);
            if (currentData.isPresent()) {
                currentData.get().close();
                return false;
            }

            Optional<InputStream> previousData = previousSerializer.read(previousName, previous);
            if (previousData.isEmpty()) {
                return false;
            }

            try (InputStream input = previousData.get();
                 OutputStream output = currentSerializer.write(currentName, current)) {
                input.transferTo(output);
            }
            log.info("Migrated player data from {} to {} through {}", previous, current,
                    currentSerializer.getClass().getSimpleName());
            return true;
        } catch (Exception e) {
            log.warn("Failed to migrate serialized player data from {} to {}", previous, current, e);
            return false;
        }
    }

    /**
     * Rename {@code <previous>.dat} to {@code <current>.dat}.
     *
     * @param playersDir directory holding the player data files
     * @param previous   identity the player was last seen under
     * @param current    identity the player logged in with
     * @return whether data was moved
     */
    public static boolean migrate(File playersDir, UUID previous, UUID current) {
        return migrate(playersDir, previous, current, Files::move);
    }

    static boolean migrate(File playersDir, UUID previous, UUID current, FileMover mover) {
        if (previous == null || current == null || previous.equals(current)) {
            return false;
        }
        if (COLLAPSED_IDENTITIES.contains(previous)) {
            log.warn("Not migrating player data from {}: that file was shared by every unauthenticated player", previous);
            return false;
        }

        File previousFile = new File(playersDir, previous + ".dat");
        File currentFile = new File(playersDir, current + ".dat");
        if (!previousFile.isFile() || currentFile.exists()) {
            return false;
        }

        try {
            try {
                mover.move(previousFile.toPath(), currentFile.toPath(), StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                mover.move(previousFile.toPath(), currentFile.toPath());
            }
            log.info("Migrated player data from {} to {}", previous, current);
            return true;
        } catch (Exception e) {
            log.warn("Failed to migrate player data from {} to {}", previous, current, e);
            return false;
        }
    }
}
