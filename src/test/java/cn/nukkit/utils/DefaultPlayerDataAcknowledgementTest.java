package cn.nukkit.utils;

import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import java.io.*;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class DefaultPlayerDataAcknowledgementTest {
    @TempDir Path directory;

    @Test void qualifiedUnsupportedDirectorySyncHasAnExplicitWeakerReceiptWithoutAttemptingForce() throws Exception {
        Files.createDirectories(directory.resolve("players"));
        AtomicInteger forces = new AtomicInteger();
        var serializer = new DefaultPlayerDataSerializer(directory.toString()) {
            @Override void forceDirectory(Path path) { forces.incrementAndGet(); fail("unsupported capability was known before publication"); }
        };
        try (var capabilities = mockStatic(DefaultPlayerDataSerializer.class, CALLS_REAL_METHODS)) {
            capabilities.when(() -> DefaultPlayerDataSerializer.supportsDirectorySync(any(Path.class))).thenReturn(false);
            assertEquals(PlayerDataSerializer.WriteAcknowledgement.ATOMIC_FILE_SYNC,
                    serializer.writeAtomicallyAcknowledged("p", null, new CompoundTag().putInt("value", 3)));
        }
        assertEquals(0, forces.get());
        try (var input = serializer.read("p", null).orElseThrow()) { assertEquals(3, NBTIO.readCompressed(input).getInt("value")); }
    }

    @Test void supportedDirectoryFailureAfterRenameIsUnknownAndCannotDowngradeToAHealthyReceipt() throws Exception {
        Files.createDirectories(directory.resolve("players"));
        IOException failure = new AccessDeniedException("directory force");
        AtomicInteger forces = new AtomicInteger();
        var serializer = new DefaultPlayerDataSerializer(directory.toString()) {
            @Override void forceDirectory(Path path) throws IOException { forces.incrementAndGet(); throw failure; }
        };
        try (var capabilities = mockStatic(DefaultPlayerDataSerializer.class, CALLS_REAL_METHODS)) {
            capabilities.when(() -> DefaultPlayerDataSerializer.supportsDirectorySync(any(Path.class))).thenReturn(true);
            assertSame(failure, assertThrows(IOException.class, () ->
                    serializer.writeAtomicallyAcknowledged("p", null, new CompoundTag().putInt("value", 4))));
        }
        assertEquals(1, forces.get());
        // Replacement may be visible, but no successful result was issued and no rollback deletes the only copy.
        try (var input = serializer.read("p", null).orElseThrow()) { assertEquals(4, NBTIO.readCompressed(input).getInt("value")); }
        try (var files = Files.list(directory.resolve("players"))) { assertEquals(1, files.count()); }
    }

    @Test void missingFileIsDistinctFromExistingUnreadableOrCorruptData() throws Exception {
        Files.createDirectories(directory.resolve("players"));
        var serializer = new DefaultPlayerDataSerializer(directory.toString());
        assertTrue(serializer.readStrict("missing", null).isEmpty());
        Files.createDirectory(directory.resolve("players/unreadable.dat"));
        assertThrows(IOException.class, () -> {
            try (var input = serializer.readStrict("unreadable", null).orElseThrow()) { NBTIO.readCompressed(input); }
        });
        Files.writeString(directory.resolve("players/corrupt.dat"), "truncated gzip");
        assertThrows(IOException.class, () -> {
            try (var input = serializer.readStrict("corrupt", null).orElseThrow()) { NBTIO.readCompressed(input); }
        });
        assertEquals("truncated gzip", Files.readString(directory.resolve("players/corrupt.dat")));
    }
}
