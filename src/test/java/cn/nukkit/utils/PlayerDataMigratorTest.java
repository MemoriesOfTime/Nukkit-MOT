package cn.nukkit.utils;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

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
        assertEquals("stale", readData(playersDir, PREVIOUS));
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
    void refusesToHandOutDataBehindServerDerivedIdentities(@TempDir Path playersDir) throws IOException {
        // 旧条目无来源记录、旧存档无 XUID 标记，版本 3 UUID 是认证身份唯一的回溯特征
        // A legacy entry carries no provenance and the save no marker: a v3 UUID is the only
        // retroactive signal of an authenticated identity
        UUID authedPrevious = UUID.nameUUIDFromBytes("pocket-auth-1-xuid:2535412345678901".getBytes(StandardCharsets.UTF_8));
        writeData(playersDir, authedPrevious, "xbox-inventory");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(playersDir.toFile(), authedPrevious, CURRENT));

        assertFalse(Files.exists(dataFile(playersDir, CURRENT)));
        assertEquals("xbox-inventory", readData(playersDir, authedPrevious),
                "an authenticated account's legacy data stays where it is");
    }

    @Test
    void refusesServerDerivedIdentitiesThroughCustomSerializer() {
        UUID authedPrevious = UUID.nameUUIDFromBytes("pocket-auth-1-xuid:2535412345678901".getBytes(StandardCharsets.UTF_8));
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.put(authedPrevious, "xbox-inventory");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(serializer, serializer, authedPrevious, CURRENT, false));

        assertEquals("xbox-inventory", serializer.get(authedPrevious));
        assertFalse(serializer.contains(CURRENT));
    }

    @Test
    void refusesToHandOutXboxAuthenticatedMarkerData(@TempDir Path playersDir) throws IOException {
        writeXuidData(playersDir, PREVIOUS, "xbox-account-inventory", "1234567890");

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertFalse(Files.exists(dataFile(playersDir, CURRENT)));
        assertEquals("xbox-account-inventory", readData(playersDir, PREVIOUS),
                "authenticated account data must stay where it is");
    }

    @Test
    void refusesToHandOutXboxAuthenticatedMarkerDataThroughCustomSerializer() {
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.put(PREVIOUS, new CompoundTag()
                .putString("marker", "xbox-account-inventory")
                .putString("XUID", "1234567890"));

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertFalse(serializer.contains(CURRENT));
        assertEquals("xbox-account-inventory", serializer.get(PREVIOUS));
    }

    @Test
    void ignoresNonStringXuidTagsStoredByPlugins(@TempDir Path playersDir) throws IOException {
        // 插件可能存同名非字符串 tag；只有认证登录写入的字符串 XUID 才是禁用标记
        // Only the string XUID written by an authenticated login is the handover marker
        CompoundTag tagged = new CompoundTag()
                .putString("marker", "inventory")
                .putCompound("XUID", new CompoundTag().putInt("pluginData", 1));
        writeNbtFile(dataFile(playersDir, PREVIOUS), tagged);

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertEquals("inventory", readData(playersDir, CURRENT));
    }

    @Test
    void reportsFailedInspectionWhenTargetReadThrowsUnexpectedly() {
        PlayerDataSerializer exploding = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) {
                throw new IllegalStateException("boom");
            }

            @Override
            public OutputStream write(String name, UUID uuid) {
                throw new AssertionError("must not write");
            }
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(exploding, exploding, PREVIOUS, CURRENT, false));
    }

    @Test
    void skipsSourceWhenParsingFailsUnexpectedly() {
        InputStream explodingStream = new InputStream() {
            @Override
            public int read() {
                throw new IllegalStateException("boom");
            }
        };
        MemoryPlayerDataSerializer currentSerializer = new MemoryPlayerDataSerializer();
        PlayerDataSerializer previousSerializer = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) {
                return Optional.of(explodingStream);
            }

            @Override
            public OutputStream write(String name, UUID uuid) {
                throw new AssertionError("must not write");
            }
        };

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(previousSerializer, currentSerializer, PREVIOUS, CURRENT, false));
    }

    @Test
    void skipsCorruptSourceFile(@TempDir Path playersDir) throws IOException {
        writeCorruptData(playersDir, PREVIOUS);

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertTrue(Files.exists(dataFile(playersDir, PREVIOUS)), "corrupt or not, the source is left alone");
        assertFalse(Files.exists(dataFile(playersDir, CURRENT)));
    }

    @Test
    void skipsCorruptSourceInCustomSerializer() {
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.putCorrupt(PREVIOUS);

        assertEquals(PlayerDataMigrator.Result.SKIPPED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertTrue(serializer.contains(PREVIOUS));
        assertFalse(serializer.contains(CURRENT));
    }

    @Test
    void removesResidueTargetAndMigrates(@TempDir Path playersDir) throws IOException {
        writeCorruptData(playersDir, CURRENT);
        writeData(playersDir, PREVIOUS, "inventory");

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT));

        assertEquals("inventory", readData(playersDir, CURRENT));
        assertFalse(Files.exists(dataFile(playersDir, PREVIOUS)));
    }

    @Test
    void removesResidueTargetThroughDeleteContract() {
        MemoryPlayerDataSerializer serializer = new MemoryPlayerDataSerializer();
        serializer.putCorrupt(CURRENT);
        serializer.put(PREVIOUS, "database-inventory");

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(serializer, serializer, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", serializer.get(CURRENT));
    }

    @Test
    void overwritesUndeletableResidueTargetThroughWrite() {
        // 删不掉的残留不再保守跳过——那会让登录静默重置并遗弃源数据
        // An undeletable residue is no longer skipped: that silently resets the player
        // and abandons the source data
        MemoryPlayerDataSerializer previousSerializer = new MemoryPlayerDataSerializer();
        previousSerializer.put(PREVIOUS, "database-inventory");
        MemoryPlayerDataSerializer currentSerializer = new MemoryPlayerDataSerializer() {
            @Override
            public boolean delete(String name, UUID uuid) {
                return false;
            }
        };
        currentSerializer.putCorrupt(CURRENT);

        assertEquals(PlayerDataMigrator.Result.MIGRATED,
                PlayerDataMigrator.migrate(previousSerializer, currentSerializer, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", currentSerializer.get(CURRENT));
        assertEquals("database-inventory", previousSerializer.get(PREVIOUS));
    }

    @Test
    void failsWhenTheResidueTargetCannotBeOverwrittenEither() {
        MemoryPlayerDataSerializer previousSerializer = new MemoryPlayerDataSerializer();
        previousSerializer.put(PREVIOUS, "database-inventory");
        PlayerDataSerializer unoverwritable = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) {
                return Optional.of(new ByteArrayInputStream("not-nbt".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public OutputStream write(String name, UUID uuid) throws IOException {
                throw new IOException("target exists and cannot be replaced");
            }
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(previousSerializer, unoverwritable, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", previousSerializer.get(PREVIOUS), "a failed migration keeps the source");
    }

    @Test
    void failsWhenTheFreshlyWrittenTargetDoesNotReadBack() {
        MemoryPlayerDataSerializer previousSerializer = new MemoryPlayerDataSerializer();
        previousSerializer.put(PREVIOUS, "database-inventory");
        PlayerDataSerializer lying = new PlayerDataSerializer() {
            @Override
            public Optional<InputStream> read(String name, UUID uuid) {
                return Optional.of(new ByteArrayInputStream("garbage".getBytes(StandardCharsets.UTF_8)));
            }

            @Override
            public OutputStream write(String name, UUID uuid) {
                return new ByteArrayOutputStream(); // 写入被丢弃，回读到垃圾
            }
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(previousSerializer, lying, PREVIOUS, CURRENT, false));

        assertEquals("database-inventory", previousSerializer.get(PREVIOUS));
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
    void keepsPartialTargetWhenSourceDisappearedMidMove(@TempDir Path playersDir) throws IOException {
        writeData(playersDir, PREVIOUS, "inventory");
        PlayerDataMigrator.FileMover mover = (source, target, options) -> {
            Files.delete(source);
            Files.write(target, "inv".getBytes(StandardCharsets.UTF_8));
            throw new IOException("disk full");
        };

        assertEquals(PlayerDataMigrator.Result.FAILED,
                PlayerDataMigrator.migrate(playersDir.toFile(), PREVIOUS, CURRENT, mover));

        assertTrue(Files.exists(dataFile(playersDir, CURRENT)),
                "with the source gone, the partial target is the only remaining copy and must not be deleted");
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

    private static void writeData(Path playersDir, UUID uuid, String marker) throws IOException {
        writeNbtFile(dataFile(playersDir, uuid), new CompoundTag().putString("marker", marker));
    }

    private static void writeXuidData(Path playersDir, UUID uuid, String marker, String xuid) throws IOException {
        writeNbtFile(dataFile(playersDir, uuid), new CompoundTag()
                .putString("marker", marker)
                .putString("XUID", xuid));
    }

    private static void writeCorruptData(Path playersDir, UUID uuid) throws IOException {
        Files.writeString(dataFile(playersDir, uuid), "not-nbt", StandardCharsets.UTF_8);
    }

    private static void writeNbtFile(Path file, CompoundTag tag) throws IOException {
        try (OutputStream out = Files.newOutputStream(file)) {
            NBTIO.writeGZIPCompressed(tag, out, ByteOrder.BIG_ENDIAN);
        }
    }

    private static String readData(Path playersDir, UUID uuid) throws IOException {
        try (InputStream in = Files.newInputStream(dataFile(playersDir, uuid))) {
            return NBTIO.readCompressed(in).getString("marker");
        }
    }

    private static Path dataFile(Path playersDir, UUID uuid) {
        return playersDir.resolve(uuid + ".dat");
    }

    private static class MemoryPlayerDataSerializer implements PlayerDataSerializer {
        private final Map<String, byte[]> data = new HashMap<>();

        void put(UUID uuid, String marker) {
            put(uuid, new CompoundTag().putString("marker", marker));
        }

        void put(UUID uuid, CompoundTag tag) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                NBTIO.writeGZIPCompressed(tag, out, ByteOrder.BIG_ENDIAN);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
            data.put(uuid.toString(), out.toByteArray());
        }

        void putCorrupt(UUID uuid) {
            data.put(uuid.toString(), "not-nbt".getBytes(StandardCharsets.UTF_8));
        }

        String get(UUID uuid) {
            byte[] value = data.get(uuid.toString());
            if (value == null) {
                return null;
            }
            try (InputStream in = new ByteArrayInputStream(value)) {
                return NBTIO.readCompressed(in).getString("marker");
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        boolean contains(UUID uuid) {
            return data.containsKey(uuid.toString());
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

        @Override
        public boolean delete(String name, UUID uuid) {
            return data.remove(name) != null;
        }
    }
}
