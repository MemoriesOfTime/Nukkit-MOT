package cn.nukkit.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

/**
 * Unit tests for {@link VanillaPaletteDownloader}.
 * <p>
 * The pure verification logic is tested without any network access. A separate
 * live test against the public mirror is included but {@link Disabled} by default
 * so the suite stays hermetic.
 */
class VanillaPaletteDownloaderTest {

    private static String sha256Hex(byte[] bytes) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(bytes));
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void verifyPassesForMatchingFile(@TempDir Path tmp) throws IOException {
        byte[] content = "hello palette".getBytes(StandardCharsets.UTF_8);
        Path file = tmp.resolve("vanilla_palette_999.nbt");
        Files.write(file, content);

        Assertions.assertTrue(VanillaPaletteDownloader.verify(file, sha256Hex(content), content.length));
    }

    @Test
    void verifyFailsForWrongSize(@TempDir Path tmp) throws IOException {
        byte[] content = "hello palette".getBytes(StandardCharsets.UTF_8);
        Path file = tmp.resolve("vanilla_palette_999.nbt");
        Files.write(file, content);

        Assertions.assertFalse(VanillaPaletteDownloader.verify(file, sha256Hex(content), content.length + 1));
    }

    @Test
    void verifyFailsForWrongHash(@TempDir Path tmp) throws IOException {
        byte[] content = "hello palette".getBytes(StandardCharsets.UTF_8);
        Path file = tmp.resolve("vanilla_palette_999.nbt");
        Files.write(file, content);

        // 64 hex chars but wrong content
        String wrongHash = "0".repeat(64);
        Assertions.assertFalse(VanillaPaletteDownloader.verify(file, wrongHash, content.length));
    }

    @Test
    void verifyFailsForMissingFile(@TempDir Path tmp) {
        Assertions.assertFalse(VanillaPaletteDownloader.verify(tmp.resolve("does_not_exist.nbt"), "0".repeat(64), 1));
    }

    @Test
    @Disabled("Hits the public mirror at https://bin-data.nkmot.com/. Remove @Disabled to run manually.")
    void downloadMissingFetchesRealFiles(@TempDir Path tmp) {
        // Cold start: every palette missing -> bundle path.
        VanillaPaletteDownloader.Result result = VanillaPaletteDownloader.downloadMissing(tmp);

        Assertions.assertTrue(result.manifestOk(), "manifest should be reachable");
        Assertions.assertTrue(result.downloaded() > 0, "at least one palette should have been downloaded");
        // The bundle is a throwaway download artifact, never a cache.
        Assertions.assertFalse(Files.exists(tmp.resolve("palettes.zip")),
                "bundle should be deleted after extraction");

        // Re-running should skip everything now that the files exist and verify.
        VanillaPaletteDownloader.Result second = VanillaPaletteDownloader.downloadMissing(tmp);
        Assertions.assertEquals(0, second.downloaded(), "second run should download nothing");
        Assertions.assertTrue(second.skipped() > 0, "second run should skip already-present files");

        // Fine-grained recovery: corrupt one local file. One missing is below the incremental
        // threshold, so this run takes the per-file path (no bundle fetched at all).
        try (var stream = Files.list(tmp)) {
            Path first = stream.filter(p -> p.getFileName().toString().endsWith(".nbt")).findFirst().orElseThrow();
            Files.write(first, new byte[]{0});
        } catch (IOException e) {
            throw new AssertionError(e);
        }
        VanillaPaletteDownloader.Result recovery = VanillaPaletteDownloader.downloadMissing(tmp);
        Assertions.assertEquals(1, recovery.downloaded(), "corrupted file should be re-downloaded");
        // The repaired file counts as downloaded (not skipped), so skipped drops by exactly one.
        Assertions.assertEquals(second.skipped() - 1, recovery.skipped(), "only the corrupted file should be re-fetched");
        Assertions.assertEquals(0, recovery.failed(), "recovery should not report failures");
        Assertions.assertFalse(Files.exists(tmp.resolve("palettes.zip")),
                "incremental path must never leave a bundle behind");
    }
}
