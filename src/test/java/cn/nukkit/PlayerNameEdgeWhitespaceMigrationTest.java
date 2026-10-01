package cn.nukkit;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.encryption.EncryptionUtils;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.WriteBatch;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Proxy;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Profiles saved under a name padded with whitespace are folded onto the trimmed name at startup
 * only in the clean case. Every other state is left untouched and blocks offline logins with the name.
 */
class PlayerNameEdgeWhitespaceMigrationTest {

    private static final String NAME = "steve alex";
    private static final String PADDED = "steve alex ";

    @TempDir
    Path directory;

    private Path players;
    private Path quarantine;
    private DB lookup;
    private UUID padded;
    private UUID trimmed;

    @BeforeEach
    void open() throws IOException {
        players = Files.createDirectories(directory.resolve("players"));
        quarantine = directory.resolve(PlayerNameEdgeWhitespaceMigration.QUARANTINE_DIRECTORY);
        lookup = Iq80DBFactory.factory.open(players.toFile(),
                new Options().createIfMissing(true).compressionType(CompressionType.ZLIB_RAW));
        padded = EncryptionUtils.deriveOfflineIdentity(PADDED);
        trimmed = EncryptionUtils.deriveOfflineIdentity(NAME);
        assertNotEquals(padded, trimmed, "the padded spelling derives an identity of its own");
    }

    @AfterEach
    void close() throws IOException {
        lookup.close();
    }

    // ----- clean cases -----

    @Test
    void aLoneProfileUnderAPaddedNameMovesToTheTrimmedIdentity() throws IOException {
        save(padded, "Steve alex ", 1_789_000_000L, "only");
        key(PADDED, padded);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertEquals(1, report.renamed().size());
        assertTrue(report.quarantined().isEmpty());
        assertTrue(report.blocked().isEmpty());
        assertFalse(Files.exists(file(padded)));
        assertEquals("only", marker(file(trimmed)));
        assertNull(lookup.get(bytes(PADDED)), "the padded key is gone");
        assertEquals(trimmed, entry(NAME).uuid(), "offline lookups by name find the moved profile");
        assertEquals(Server.NameProvenance.OFFLINE, entry(NAME).provenance());
        assertFalse(Files.exists(quarantine), "nothing was quarantined");
        assertUnchangedOnSecondStart();
    }

    @Test
    void ofAPairTheLaterPlayedPaddedProfileStaysAndTheOlderGoesToQuarantine() throws IOException {
        save(trimmed, "Steve alex", 1_789_224_150L, "older");
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(NAME, trimmed);
        key(PADDED, padded);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertEquals(1, report.renamed().size());
        assertEquals(1, report.quarantined().size());
        assertEquals("newer", marker(file(trimmed)), "the profile played last now belongs to the name");
        assertFalse(Files.exists(file(padded)));
        Path quarantined = report.quarantined().get(0).destination();
        assertEquals(quarantine, quarantined.getParent());
        assertEquals("older", marker(quarantined), "the older profile is kept, not deleted");
        assertEquals(1, read(file(trimmed)).getList("Inventory").size(), "contents are never merged");
        assertNull(lookup.get(bytes(PADDED)));
        assertEquals(trimmed, entry(NAME).uuid());
        assertUnchangedOnSecondStart();
    }

    @Test
    void ofAPairTheLaterPlayedTrimmedProfileStaysInPlace() throws IOException {
        save(padded, "Steve alex ", 1_788_718_140L, "padded");
        save(trimmed, "Steve alex", 1_789_313_600L, "trimmed");
        // A stale rewrite made the padded file newer on disk; lastPlayed decides, not mtime.
        Files.setLastModifiedTime(file(padded), FileTime.fromMillis(1_789_400_000_000L));
        key(PADDED, padded);
        key(NAME, trimmed);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertTrue(report.renamed().isEmpty());
        assertEquals(1, report.quarantined().size());
        assertEquals("trimmed", marker(file(trimmed)));
        assertEquals("padded", marker(report.quarantined().get(0).destination()));
        assertUnchangedOnSecondStart();
    }

    @Test
    void aNeverPlayedPaddedProfileDoesNotPushARealProfileIntoQuarantine() throws IOException {
        save(trimmed, "Steve alex", 1_789_000_000L, 1_789_220_000L, "real");
        save(padded, "Steve alex ", 1_789_300_000L, 1_789_300_000L, "fresh");
        key(PADDED, padded);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertTrue(report.renamed().isEmpty());
        assertEquals("real", marker(file(trimmed)));
        assertEquals("fresh", marker(report.quarantined().get(0).destination()));
        assertUnchangedOnSecondStart();
    }

    @Test
    void namesWithoutPaddingAreNeverTouched() throws IOException {
        UUID inner = EncryptionUtils.deriveOfflineIdentity("gg  vam");
        save(inner, "Gg  vam", 1_789_000_000L, "inner");
        key("gg  vam", inner);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertFalse(report.changed());
        assertTrue(report.blocked().isEmpty());
        assertEquals("inner", marker(file(inner)));
    }

    // ----- unclean cases: nothing is touched and the name is blocked -----

    @Test
    void aPaddedKeyPointingAtAnotherIdentityBlocks() throws IOException {
        UUID stranger = UUID.fromString("0f3e6a3c-2a5e-4a1f-9b4e-7d1c2a9e0b11");
        save(stranger, "Steve alex ", 1_789_000_000L, "stranger");
        key(PADDED, stranger);

        assertBlockedAndUntouched("points at " + stranger);
    }

    @Test
    void aTrimmedKeyPointingAtAThirdIdentityBlocks() throws IOException {
        UUID old = UUID.fromString("5a1d7f3e-9c4b-4e2a-8f6d-3b2c1a0e9d88");
        save(old, "Steve alex", 1_789_320_000L, "real newest");
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        key(NAME, old);
        key(PADDED, padded);

        assertBlockedAndUntouched("points at " + old);
    }

    @Test
    void anXboxTrimmedKeyBlocks() throws IOException {
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        key(PADDED, padded);
        lookup.put(bytes(NAME), Server.encodeNameEntry(UUID.randomUUID(), Server.NameProvenance.XBOX_AUTHED));

        assertBlockedAndUntouched("XBOX_AUTHED");
    }

    @Test
    void aLegacyPaddedKeyBlocks() throws IOException {
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        ByteBuffer legacy = ByteBuffer.allocate(16);
        legacy.putLong(padded.getMostSignificantBits()).putLong(padded.getLeastSignificantBits());
        lookup.put(bytes(PADDED), legacy.array());

        assertBlockedAndUntouched("LEGACY_UNKNOWN");
    }

    @Test
    void anUnreadableTrimmedFileBlocks() throws IOException {
        Files.write(file(trimmed), new byte[]{1, 2, 3});
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        key(NAME, trimmed);
        key(PADDED, padded);

        assertBlockedAndUntouched("cannot be read");
    }

    @Test
    void anUnreadablePaddedFileBlocks() throws IOException {
        save(trimmed, "Steve alex", 1_789_311_160L, "trimmed");
        Files.write(file(padded), new byte[]{1, 2, 3});
        key(NAME, trimmed);
        key(PADDED, padded);

        assertBlockedAndUntouched("cannot be read");
    }

    @Test
    void aTrimmedFileSavedForAnotherNameBlocks() throws IOException {
        save(trimmed, "Somebody", 1_789_000_000L, "foreign");
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(PADDED, padded);

        assertBlockedAndUntouched("is saved for \"Somebody\"");
    }

    @Test
    void aTrimmedFileWithAnXuidBlocks() throws IOException {
        save(trimmed, "Steve alex", 1_789_000_000L, "xbox");
        Files.write(file(trimmed), NBTIO.writeGZIPCompressed(read(file(trimmed)).putString("XUID", "2535400000000000")));
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(PADDED, padded);

        assertBlockedAndUntouched("Xbox authenticated");
    }

    @Test
    void aTrimmedFileWithoutANameTagBlocks() throws IOException {
        save(trimmed, null, 1_789_320_000L, "no name");
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        key(PADDED, padded);

        assertBlockedAndUntouched("carries no NameTag");
    }

    @Test
    void aPaddedFileWithoutANameTagBlocks() throws IOException {
        save(padded, null, 1_789_311_160L, "padded");
        key(PADDED, padded);

        assertBlockedAndUntouched("carries no NameTag");
    }

    @Test
    void aPaddedKeyWithoutAnyFileBlocks() throws IOException {
        save(trimmed, "Steve alex", 1_789_224_150L, "trimmed");
        key(PADDED, padded);

        assertBlockedAndUntouched("has no data file");
    }

    @Test
    void aMixedGroupWithOnePaddedKeyWithoutAFileBlocks() throws IOException {
        UUID leading = EncryptionUtils.deriveOfflineIdentity(" steve alex");
        save(leading, " Steve alex", 1_789_311_160L, "leading");
        key(" steve alex", leading);
        key(PADDED, padded);

        assertBlockedAndUntouched("\"steve alex \" has no data file");
    }

    @Test
    void aTrimmedKeyWithoutItsFileBlocks() throws IOException {
        save(padded, "Steve alex ", 1_789_311_160L, "padded");
        key(NAME, trimmed);
        key(PADDED, padded);

        assertBlockedAndUntouched("\"steve alex\" has no data file");
    }

    // ----- failures -----

    @Test
    void aFailedSecondRenameRestoresTheFilesBlocksTheNameAndTheNextStartFinishes() throws IOException {
        save(trimmed, "Steve alex", 1_789_224_150L, "older");
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(NAME, trimmed);
        key(PADDED, padded);
        List<String> before = listing();
        int[] moves = {0};

        PlayerNameEdgeWhitespaceMigration.Report failed = run((source, destination) -> {
            if (++moves[0] == 2) {
                throw new IOException("disk said no");
            }
            Files.move(source, destination);
        });

        assertEquals(Set.of(NAME), failed.blockedNames());
        assertEquals(before, listing(), "the quarantined loser is back in place");
        assertEquals("older", marker(file(trimmed)));
        assertEquals("newer", marker(file(padded)));
        assertEquals(padded, entry(PADDED).uuid(), "keys are untouched");

        PlayerNameEdgeWhitespaceMigration.Report next = run();

        assertTrue(next.blocked().isEmpty());
        assertEquals("newer", marker(file(trimmed)));
        assertFalse(Files.exists(file(padded)));
        assertNull(lookup.get(bytes(PADDED)));
    }

    @Test
    void aFailedUndoStillBlocksOnlyThatName() throws IOException {
        save(trimmed, "Steve alex", 1_789_224_150L, "older");
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(NAME, trimmed);
        key(PADDED, padded);
        UUID other = EncryptionUtils.deriveOfflineIdentity("makac20 ");
        save(other, "makac20 ", 1_789_000_000L, "other");
        key("makac20 ", other);

        // The winner's rename fails, and so does moving the loser back out of quarantine.
        PlayerNameEdgeWhitespaceMigration.Report report = run((source, destination) -> {
            if (source.equals(file(padded)) || quarantine.equals(source.getParent())) {
                throw new IOException("disk said no");
            }
            Files.move(source, destination);
        });

        assertEquals(Set.of(NAME), report.blockedNames());
        assertEquals("newer", marker(file(padded)));
        assertEquals("other", marker(file(EncryptionUtils.deriveOfflineIdentity("makac20"))), "other names fold as usual");
        assertNotNull(lookup.get(bytes(PADDED)), "the blocked name keeps its keys");
    }

    @Test
    void aKeyBatchThatThrowsAfterApplyingBlocksWithoutMovingFilesBack() throws IOException {
        save(trimmed, "Steve alex", 1_789_224_150L, "older");
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        key(NAME, trimmed);
        key(PADDED, padded);
        DB failingAfterWrite = (DB) Proxy.newProxyInstance(DB.class.getClassLoader(), new Class<?>[]{DB.class},
                (proxy, method, args) -> {
                    Object result;
                    try {
                        result = method.invoke(lookup, args);
                    } catch (InvocationTargetException failure) {
                        throw failure.getCause();
                    }
                    if (method.getName().equals("write") && args.length == 1 && args[0] instanceof WriteBatch) {
                        throw new RuntimeException("write acknowledged too late");
                    }
                    return result;
                });

        PlayerNameEdgeWhitespaceMigration.Report report =
                PlayerNameEdgeWhitespaceMigration.run(failingAfterWrite, players.toFile(), quarantine.toFile());

        assertEquals(Set.of(NAME), report.blockedNames());
        assertEquals("newer", marker(file(trimmed)), "the files stay folded");
        assertFalse(Files.exists(file(padded)));
        assertNull(lookup.get(bytes(PADDED)), "the batch was applied");
        assertUnchangedOnSecondStart();
    }

    // ----- a later start finishes what an interrupted one left -----

    @Test
    void aStartInterruptedAfterQuarantiningTheTrimmedLoserRenamesTheWinner() throws IOException {
        save(padded, "Steve alex ", 1_789_311_160L, "newer");
        Files.createDirectories(quarantine);
        save(quarantine.resolve(trimmed + ".dat"), "Steve alex", 1_788_000_000L, 1_789_224_150L, "older");
        key(NAME, trimmed);
        key(PADDED, padded);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertTrue(report.blocked().isEmpty());
        assertEquals("newer", marker(file(trimmed)));
        assertFalse(Files.exists(file(padded)));
        assertNull(lookup.get(bytes(PADDED)));
        assertUnchangedOnSecondStart();
    }

    @Test
    void keysLeftByAnInterruptedQuarantineAreCleanedUp() throws IOException {
        save(trimmed, "Steve alex", 1_789_313_600L, "trimmed");
        Files.createDirectories(quarantine);
        save(quarantine.resolve(padded + ".dat"), "Steve alex ", 1_788_000_000L, 1_788_718_140L, "padded");
        key(PADDED, padded);
        key(NAME, trimmed);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertEquals(1, report.namesFixed());
        assertTrue(report.blocked().isEmpty());
        assertNull(lookup.get(bytes(PADDED)));
        assertEquals("trimmed", marker(file(trimmed)));
        assertUnchangedOnSecondStart();
    }

    @Test
    void keysLeftByAnInterruptedRenameAreCleanedUp() throws IOException {
        save(trimmed, "Steve alex ", 1_789_000_000L, "renamed");
        key(PADDED, padded);

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertEquals(1, report.namesFixed());
        assertTrue(report.blocked().isEmpty());
        assertNull(lookup.get(bytes(PADDED)));
        assertEquals(trimmed, entry(NAME).uuid());
        assertEquals("renamed", marker(file(trimmed)));
    }

    @Test
    void aQuarantinedFileOfAnotherNameIsNotEvidence() throws IOException {
        save(trimmed, "Steve alex", 1_789_313_600L, "trimmed");
        Files.createDirectories(quarantine);
        save(quarantine.resolve(padded + ".dat"), "Somebody", 1_788_000_000L, 1_788_718_140L, "foreign");
        key(PADDED, padded);

        assertBlockedAndUntouched("has no data file");
    }

    // ----- helpers -----

    private void assertBlockedAndUntouched(String reason) throws IOException {
        List<String> before = listing();
        Map<String, byte[]> keysBefore = keys();

        PlayerNameEdgeWhitespaceMigration.Report report = run();

        assertEquals(Set.of(NAME), report.blockedNames());
        assertTrue(report.blocked().get(NAME).contains(reason), report.blocked().get(NAME));
        assertFalse(report.changed());
        assertEquals(before, listing(), "no file was moved");
        assertKeysEqual(keysBefore, keys());

        PlayerNameEdgeWhitespaceMigration.Report again = run();
        assertEquals(Set.of(NAME), again.blockedNames(), "it stays blocked until the files are checked by hand");
        assertEquals(before, listing());
    }

    private PlayerNameEdgeWhitespaceMigration.Report run() {
        return PlayerNameEdgeWhitespaceMigration.run(lookup, players.toFile(), quarantine.toFile());
    }

    private PlayerNameEdgeWhitespaceMigration.Report run(PlayerNameEdgeWhitespaceMigration.FileMover mover) {
        return PlayerNameEdgeWhitespaceMigration.run(lookup, players.toFile(), quarantine.toFile(), mover);
    }

    private void assertUnchangedOnSecondStart() throws IOException {
        List<String> before = listing();
        PlayerNameEdgeWhitespaceMigration.Report again = run();
        assertFalse(again.changed(), "a second start changes nothing");
        assertTrue(again.blocked().isEmpty());
        assertEquals(before, listing());
    }

    private List<String> listing() throws IOException {
        List<String> names = new ArrayList<>();
        for (Path root : List.of(players, quarantine)) {
            if (!Files.exists(root)) continue;
            try (Stream<Path> files = Files.list(root)) {
                files.filter(path -> path.toString().endsWith(".dat"))
                        .map(path -> root.getFileName() + "/" + path.getFileName())
                        .sorted()
                        .forEach(names::add);
            }
        }
        return names;
    }

    private Map<String, byte[]> keys() {
        Map<String, byte[]> keys = new TreeMap<>();
        try (var iterator = lookup.iterator()) {
            iterator.seekToFirst();
            while (iterator.hasNext()) {
                var entry = iterator.next();
                keys.put(new String(entry.getKey(), StandardCharsets.UTF_8), entry.getValue());
            }
        }
        return keys;
    }

    private static void assertKeysEqual(Map<String, byte[]> expected, Map<String, byte[]> actual) {
        assertEquals(expected.keySet(), actual.keySet(), "no key was added or removed");
        expected.forEach((name, value) -> assertArrayEquals(value, actual.get(name), "key " + name + " was rewritten"));
    }

    private void save(UUID uuid, String nameTag, long lastPlayed, String marker) throws IOException {
        save(file(uuid), nameTag, lastPlayed - 3600, lastPlayed, marker);
    }

    private void save(UUID uuid, String nameTag, long firstPlayed, long lastPlayed, String marker) throws IOException {
        save(file(uuid), nameTag, firstPlayed, lastPlayed, marker);
    }

    private static void save(Path file, String nameTag, long firstPlayed, long lastPlayed, String marker) throws IOException {
        CompoundTag tag = new CompoundTag()
                .putLong("firstPlayed", firstPlayed)
                .putLong("lastPlayed", lastPlayed)
                .putString("marker", marker)
                .putList(new ListTag<CompoundTag>("Inventory")
                        .add(new CompoundTag().putString("Name", "minecraft:iron_shovel").putByte("Count", 1)));
        if (nameTag != null) {
            tag.putString("NameTag", nameTag);
        }
        Files.write(file, NBTIO.writeGZIPCompressed(tag));
    }

    private void key(String name, UUID uuid) {
        lookup.put(bytes(name), Server.encodeNameEntry(uuid, Server.NameProvenance.OFFLINE));
    }

    private Server.NameEntry entry(String name) {
        return Server.decodeNameEntry(lookup.get(bytes(name)));
    }

    private Path file(UUID uuid) {
        return players.resolve(uuid + ".dat");
    }

    private static String marker(Path file) throws IOException {
        return read(file).getString("marker");
    }

    private static CompoundTag read(Path file) throws IOException {
        try (InputStream input = Files.newInputStream(file)) {
            return NBTIO.readCompressed(input);
        }
    }

    private static byte[] bytes(String name) {
        return name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
    }
}
