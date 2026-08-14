package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Carries a player's saved data over when their identity UUID changes.
 */
class PlayerDataMigratorTest {

    private static final UUID PREVIOUS = UUID.fromString("6a1b7ac6-2f0f-4a6a-9f43-2b6c4a1d0e11");
    private static final UUID CURRENT = UUID.fromString("762705ea-dcc6-4dfe-b281-20b8d1ffa86b");

    @Test
    void neverMigratesAuthenticatedPlayerData(@TempDir Path dataDir) throws IOException {
        Path playersDir = Files.createDirectories(dataDir.resolve("players"));
        writeData(playersDir, PREVIOUS, "xbox-inventory");
        PlayerDataSerializer serializer = new DefaultPlayerDataSerializer(dataDir + File.separator);

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, true));

        assertEquals("xbox-inventory", readData(playersDir, PREVIOUS));
        assertFalse(Files.exists(dataFile(playersDir, CURRENT)));
    }

    @Test
    void migratesUnauthenticatedPlayerDataThroughCustomSerializer() {
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.put(PREVIOUS, "database-inventory");

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", serializer.get(CURRENT));
        assertEquals("database-inventory", serializer.get(PREVIOUS),
                "generic serializers have no delete contract, so the source must remain intact");
    }

    @Test
    void keepsCurrentDataInCustomSerializerWhenItAlreadyExists() {
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.put(PREVIOUS, "stale-database-inventory");
        serializer.put(CURRENT, "live-database-inventory");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertEquals("live-database-inventory", serializer.get(CURRENT));
    }

    @Test
    void defaultSerializerMovesDataInsteadOfLeavingTheOldFile(@TempDir Path dataDir) throws IOException {
        Path playersDir = Files.createDirectories(dataDir.resolve("players"));
        writeData(playersDir, PREVIOUS, "default-storage-inventory");
        PlayerDataSerializer serializer = new DefaultPlayerDataSerializer(dataDir + File.separator);

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertEquals("default-storage-inventory", readData(playersDir, CURRENT));
        assertFalse(Files.exists(dataFile(playersDir, PREVIOUS)));
    }

    @Test
    void migratesPreviousIdentityWhenCurrentDataIsAbsent(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "inventory");

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertEquals("inventory", readData(playersDir, CURRENT));
        assertFalse(Files.exists(dataFile(playersDir, PREVIOUS)), "the old file must not linger");
    }

    @Test
    void fallsBackToRegularMoveWhenAtomicMoveIsUnsupported(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "network-volume-inventory");
        AtomicInteger attempts = new AtomicInteger();
        PlayerDataMigrator.FileMover mover = (source, target, options) -> {
            attempts.incrementAndGet();
            if (Arrays.asList(options).contains(StandardCopyOption.ATOMIC_MOVE)) {
                throw new AtomicMoveNotSupportedException(source.toString(), target.toString(), "not supported");
            }
            return Files.move(source, target, options);
        };

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT, mover));

        assertEquals(2, attempts.get());
        assertEquals("network-volume-inventory", readData(playersDir, CURRENT));
        assertFalse(Files.exists(dataFile(playersDir, PREVIOUS)));
    }

    @Test
    void keepsCurrentDataWhenItAlreadyExists(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "stale");
        writeData(playersDir, CURRENT, "live");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertEquals("live", readData(playersDir, CURRENT));
    }

    @Test
    void refusesToHandOutTheDataFileSharedByTheCollapsedIdentityBug(@TempDir Path playersDir) throws IOException {
        // Every unauthenticated v1.21.90+ login used to collapse onto this UUID, so its data file
        // is a pile of whoever logged out last. Handing it to the first player to reconnect would
        // give one of them everyone else's belongings.
        UUID collapsed = UUID.fromString("3c14031c-69c0-30bb-8339-5ee69712b3e5");
        writeData(playersDir, collapsed, "merged");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(playersDir.toFile(), collapsed, CURRENT));

        assertFalse(Files.exists(dataFile(playersDir, CURRENT)));
        assertEquals("merged", readData(playersDir, collapsed), "the shared file is left untouched");
    }

    @Test
    void reportsFailedMoveAndKeepsSourceIntact(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "inventory");
        PlayerDataMigrator.FileMover mover = (source, target, options) -> {
            throw new IOException("disk full");
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT, mover));

        assertEquals("inventory", readData(playersDir, PREVIOUS), "a failed move must leave the old data alone");
        assertFalse(Files.exists(dataFile(playersDir, CURRENT)), "no half-written target may block the retry");
    }

    @Test
    void deletesPartialTargetWhenMoveFailsHalfway(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "inventory");
        PlayerDataMigrator.FileMover mover = (source, target, options) -> {
            Files.write(target, "inv".getBytes(StandardCharsets.UTF_8));
            throw new IOException("disk full");
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT, mover));

        assertEquals("inventory", readData(playersDir, PREVIOUS));
        assertFalse(Files.exists(dataFile(playersDir, CURRENT)),
                "a partial target would read as existing data and silently reset the player");
    }

    @Test
    void reportsFailedReadThroughCustomSerializer() {
        PlayerDataSerializer unreachable = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) throws IOException {
                throw new IOException("database down");
            }

            @Override
            public OutputStream write(String name, UUID uuid) throws IOException {
                throw new AssertionError("must not write");
            }
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(unreachable, unreachable, PREVIOUS, CURRENT, false));
    }

    @Test
    void reportsFailedWriteThroughCustomSerializerAndKeepsSource() {
        MemoryPlayerDataSerializer previousSerializer = new MemoryPlayerDataSerializer();
        previousSerializer.put(PREVIOUS, "database-inventory");
        PlayerDataSerializer failingWrite = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) {
                return Optional.empty();
            }

            @Override
            public OutputStream write(String name, UUID uuid) {
                return new OutputStream() {
                    @Override
                    public void write(int b) throws IOException {
                        throw new IOException("disk full");
                    }

                    @Override
                    public void write(byte[] b) throws IOException {
                        throw new IOException("disk full");
                    }
                };
            }
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(previousSerializer, failingWrite, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", previousSerializer.get(PREVIOUS));
    }

    private static void writeData(Path playersDir, UUID uuid, String content) throws IOException {
        Files.writeString(dataFile(playersDir, uuid), content, StandardCharsets.UTF_8);
    }

    private static String readData(Path playersDir, UUID uuid) throws IOException {
        return Files.readString(dataFile(playersDir, uuid), StandardCharsets.UTF_8);
    }

    private static Path dataFile(Path playersDir, UUID uuid) {
        return playersDir.resolve(uuid + ".dat");
    }

    private static final class MemoryPlayerDataSerializer implements PlayerDataSerializer {
        private final Map<String, byte[]> data = new HashMap<>();

        void put(UUID uuid, String value) {
            data.put(uuid.toString(), value.getBytes(StandardCharsets.UTF_8));
        }

        String get(UUID uuid) {
            byte[] value = data.get(uuid.toString());
            return value == null ? null : new String(value, StandardCharsets.UTF_8);
        }

        @Override
        public Optional<InputStream> read(String name, UUID uuid) {
            byte[] value = data.get(name);
            return value == null ? Optional.empty() : Optional.of(new ByteArrayInputStream(value));
        }

        @Override
        public OutputStream write(String name, UUID uuid) {
            return new ByteArrayOutputStream() {
                @Override
                public void close() throws IOException {
                    data.put(name, toByteArray());
                    super.close();
                }
            };
        }
    }
}
