package cn.nukkit.utils;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    @Test
    void paletteMessagesExistInEveryBundledLanguage() throws IOException {
        Map<String, String> eng = loadIniKeys("eng");
        Map<String, String> paletteKeys = new HashMap<>();
        eng.forEach((key, value) -> {
            if (key.startsWith("nukkit.palette.")) {
                paletteKeys.put(key, value);
            }
        });
        Assertions.assertFalse(paletteKeys.isEmpty(), "eng must define nukkit.palette.* keys");

        // 该目录下语言文件惯例为全量覆盖，缺失键会静默回退英文 / bundled languages are kept
        // in full key parity; a missing key would silently fall back to English.
        for (String folder : new String[]{"chs", "deu", "jpn", "rus", "vie"}) {
            Map<String, String> lang = loadIniKeys(folder);
            for (Map.Entry<String, String> entry : paletteKeys.entrySet()) {
                String translated = lang.get(entry.getKey());
                Assertions.assertNotNull(translated, folder + " lacks " + entry.getKey());
                Assertions.assertEquals(placeholders(entry.getValue()), placeholders(translated),
                        folder + "/" + entry.getKey() + " placeholder set differs from eng");
            }
        }
    }

    private static Map<String, String> loadIniKeys(String folder) throws IOException {
        try (InputStream in = VanillaPaletteDownloaderTest.class.getClassLoader()
                .getResourceAsStream("lang/" + folder + "/lang.ini")) {
            Assertions.assertNotNull(in, folder + "/lang.ini missing from classpath");
            Map<String, String> map = new HashMap<>();
            for (String line : new String(in.readAllBytes(), StandardCharsets.UTF_8).split("\n")) {
                line = line.trim();
                if (line.isEmpty() || line.charAt(0) == '#') {
                    continue;
                }
                String[] split = line.split("=", 2);
                if (split.length == 2) {
                    map.put(split[0], split[1]);
                }
            }
            return map;
        }
    }

    private static List<String> placeholders(String text) {
        List<String> found = new ArrayList<>();
        Matcher matcher = Pattern.compile("\\{%\\d+}").matcher(text);
        while (matcher.find()) {
            found.add(matcher.group());
        }
        Collections.sort(found);
        return found;
    }
}
