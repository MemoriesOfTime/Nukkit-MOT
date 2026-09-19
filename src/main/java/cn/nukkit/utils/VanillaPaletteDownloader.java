package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Downloads vanilla block palettes ({@code vanilla_palette_*.nbt}) from the official mirror
 * ({@code https://bin-data.nkmot.com/}, backed by {@code MemoriesOfTime/Bin_Data}).
 * <p>
 * The mirror serves both individual {@code .nbt} files and a {@code palettes.zip} bundle
 * (~2.6 MB). A {@code palettes.json} manifest lists the bundle's size/SHA-256 and every
 * member's size/SHA-256. This class picks the cheaper path per run: fetch files individually
 * when only a few are missing, otherwise pull and extract the bundle (then delete it).
 * Idempotent and safe to run on every startup.
 * <p>
 * HTTP transport is handled by {@link HttpUtils}.
 * <p>
 * 日志文案经 BaseLang 本地化（lang.ini 键前缀 {@code nukkit.palette.*}）。
 * Log messages are localized via the server's {@link BaseLang} (lang.ini prefix {@code nukkit.palette.*}).
 */
@Log4j2
public final class VanillaPaletteDownloader {

    /** Base URL of the official mirror. Hardcoded; not configurable. */
    public static final String MIRROR_BASE_URL = "https://bin-data.nkmot.com/";

    /** Upstream GitHub repo backing the mirror; users can download the bundle manually from here. */
    public static final String SOURCE_REPO_URL = "https://github.com/MemoriesOfTime/Bin_Data";

    private static final String MANIFEST_FILE = "palettes.json";

    /** Above this many missing files, the bundle beats individual requests. */
    private static final int INCREMENTAL_THRESHOLD = 5;

    /** 无 Server 上下文（单元测试）时的英文兜底 / English fallback when no server is running. */
    private static volatile BaseLang fallbackLang;

    private VanillaPaletteDownloader() {
    }

    private static BaseLang getLang() {
        Server server = Server.getInstance();
        if (server != null) {
            return server.getLanguage();
        }
        BaseLang lang = fallbackLang;
        if (lang == null) {
            fallbackLang = lang = new BaseLang("eng");
        }
        return lang;
    }

    /**
     * Ensure {@code binDir} holds a complete, verified set of vanilla palettes matching the
     * remote manifest. Downloads only what is missing or fails verification.
     *
     * @param binDir local {@code bin/} directory (must exist)
     * @return a summary of the run, never {@code null}
     */
    public static Result downloadMissing(Path binDir) {
        try {
            JsonObject manifest = fetchManifest();
            return processManifest(binDir, manifest);
        } catch (Exception e) {
            log.warn(getLang().translateString("nukkit.palette.downloadFailed", MIRROR_BASE_URL, e.toString()));
            logManualRecoveryHint();
            return new Result(0, 0, 0, false);
        }
    }

    private static JsonObject fetchManifest() throws IOException, InterruptedException {
        String body = HttpUtils.fetchString(MIRROR_BASE_URL + MANIFEST_FILE);
        return JsonParser.parseString(body).getAsJsonObject();
    }

    private static void logManualRecoveryHint() {
        log.warn(getLang().translateString("nukkit.palette.manualHintBundle", SOURCE_REPO_URL));
        // Key mirrors CustomBlockSettings (@CustomKey "auto-download-vanilla-palette" in nukkit-mot.yml).
        log.warn(getLang().translateString("nukkit.palette.manualHintConfig"));
    }

    private static Result processManifest(Path binDir, JsonObject manifest) {
        List<Member> members = parseMembers(manifest);
        if (members.isEmpty()) {
            log.warn(getLang().translateString("nukkit.palette.manifestEmpty"));
            return new Result(0, 0, 0, false);
        }

        List<Member> missing = new ArrayList<>();
        for (Member member : members) {
            if (!verify(binDir.resolve(member.file), member.sha256, member.size)) {
                missing.add(member);
            }
        }
        int present = members.size() - missing.size();

        if (missing.isEmpty()) {
            log.info(getLang().translateString("nukkit.palette.summary", 0, present, 0, members.size()));
            return new Result(0, present, 0, true);
        }

        log.info(getLang().translateString("nukkit.palette.needUpdate", missing.size(), members.size()));

        if (missing.size() <= INCREMENTAL_THRESHOLD) {
            return downloadFilesIndividually(binDir, members, missing, present);
        }

        Bundle bundle;
        try {
            bundle = parseBundle(manifest);
        } catch (Exception e) {
            log.warn(getLang().translateString("nukkit.palette.manifestNoBundle", e.toString()));
            logManualRecoveryHint();
            return new Result(0, present, missing.size(), true);
        }

        Path bundlePath = binDir.resolve(bundle.file);
        try {
            downloadBundle(bundlePath, bundle);
        } catch (Exception e) {
            log.warn(getLang().translateString("nukkit.palette.bundleFailed", bundle.file, e.toString()));
            logManualRecoveryHint();
            return new Result(0, present, missing.size(), true);
        }

        try {
            return extractMembers(binDir, bundlePath, missing, members.size(), present);
        } finally {
            tryDelete(bundlePath);
        }
    }

    private static List<Member> parseMembers(JsonObject manifest) {
        List<Member> members = new ArrayList<>();
        if (!manifest.has("palettes")) {
            return members;
        }
        for (JsonElement element : manifest.getAsJsonArray("palettes")) {
            JsonObject entry = element.getAsJsonObject();
            String file = entry.get("file").getAsString();
            int size = entry.get("size").getAsInt();
            String sha256 = entry.get("sha256").getAsString();
            members.add(new Member(file, sha256, size));
        }
        return members;
    }

    private static Bundle parseBundle(JsonObject manifest) {
        JsonObject zip = manifest.getAsJsonObject("zip");
        String file = zip.get("file").getAsString();
        long size = zip.get("size").getAsLong();
        String sha256 = zip.get("sha256").getAsString();
        return new Bundle(file, size, sha256);
    }

    private static void downloadBundle(Path bundlePath, Bundle bundle)
            throws IOException, InterruptedException {
        log.info(getLang().translateString("nukkit.palette.bundleDownloading", formatBytes(bundle.size)));
        Path temp = bundlePath.resolveSibling(bundlePath.getFileName().toString() + ".part");
        try {
            HttpUtils.downloadFile(MIRROR_BASE_URL + bundle.file, temp);
            if (!verify(temp, bundle.sha256, bundle.size)) {
                throw new IOException("Bundle SHA-256/size verification failed");
            }
            Files.move(temp, bundlePath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static Result downloadFilesIndividually(Path binDir, List<Member> all,
                                                    List<Member> missing, int present) {
        log.info(getLang().translateString("nukkit.palette.individualDownloading", missing.size()));

        int downloaded = 0;
        int failed = 0;
        for (Member member : missing) {
            Path target = binDir.resolve(member.file);
            Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
            try {
                HttpUtils.downloadFile(MIRROR_BASE_URL + member.file, temp);
                if (!verify(temp, member.sha256, member.size)) {
                    throw new IOException("SHA-256/size verification failed");
                }
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                downloaded++;
            } catch (Exception e) {
                log.warn(getLang().translateString("nukkit.palette.fileFailed", member.file, e.toString()));
                failed++;
            } finally {
                tryDelete(temp);
            }
        }

        log.info(getLang().translateString("nukkit.palette.summary", downloaded, present, failed, all.size()));
        return new Result(downloaded, present, failed, true);
    }

    private static Result extractMembers(Path binDir, Path bundlePath,
                                         List<Member> missing, int total, int present) {
        Map<String, Member> byFile = new HashMap<>(missing.size() * 2);
        for (Member member : missing) {
            byFile.put(member.file, member);
        }

        log.info(getLang().translateString("nukkit.palette.extracting"));

        int downloaded = 0;
        int failed = 0;
        int processed = 0;

        try (InputStream raw = Files.newInputStream(bundlePath);
             ZipInputStream zip = new ZipInputStream(raw)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }
                Member member = byFile.get(entry.getName());
                if (member == null) {
                    continue;
                }
                processed++;

                Path target = binDir.resolve(member.file);
                Path temp = target.resolveSibling(target.getFileName().toString() + ".part");
                try {
                    writeAndVerify(zip, temp, member.sha256, member.size);
                    Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    downloaded++;
                } catch (Exception e) {
                    log.warn(getLang().translateString("nukkit.palette.extractFailed", member.file, e.toString()));
                    failed++;
                    Files.deleteIfExists(temp);
                }
            }
        } catch (IOException e) {
            log.warn(getLang().translateString("nukkit.palette.bundleReadFailed", e.toString()));
            logManualRecoveryHint();
        }

        if (processed < missing.size()) {
            int absent = missing.size() - processed;
            log.warn(getLang().translateString("nukkit.palette.bundleMissingEntries", absent));
            failed += absent;
        }

        log.info(getLang().translateString("nukkit.palette.summary", downloaded, present, failed, total));
        return new Result(downloaded, present, failed, true);
    }

    private static void tryDelete(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException e) {
            log.warn(getLang().translateString("nukkit.palette.deleteFailed", path, e.toString()));
        }
    }

    private static void writeAndVerify(InputStream zip, Path temp, String expectedSha256, int expectedSize)
            throws IOException {
        MessageDigest digest = newSha256();
        long written = 0;
        try (var out = Files.newOutputStream(temp)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = zip.read(buffer)) != -1) {
                out.write(buffer, 0, read);
                digest.update(buffer, 0, read);
                written += read;
            }
        }
        if (written != expectedSize) {
            throw new IOException("Size mismatch: expected " + expectedSize + ", got " + written);
        }
        String actual = HexFormat.of().formatHex(digest.digest());
        if (!actual.equalsIgnoreCase(expectedSha256)) {
            throw new IOException("SHA-256 mismatch: expected " + expectedSha256 + ", got " + actual);
        }
    }

    /**
     * Verify {@code file} exists, matches {@code expectedSize} bytes, and has the given SHA-256.
     *
     * @return {@code true} if present and matching, {@code false} otherwise
     */
    static boolean verify(Path file, String expectedSha256, long expectedSize) {
        if (!Files.isRegularFile(file)) {
            return false;
        }
        try {
            if (Files.size(file) != expectedSize) {
                return false;
            }
            return sha256(file).equalsIgnoreCase(expectedSha256);
        } catch (IOException e) {
            return false;
        }
    }

    private static String sha256(Path file) throws IOException {
        MessageDigest digest = newSha256();
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return HexFormat.of().formatHex(digest.digest());
    }

    private static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new AssertionError("SHA-256 unavailable", e);
        }
    }

    /** Human-readable byte size, e.g. {@code "2.6 MB"}. */
    private static String formatBytes(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024) {
            return String.format(java.util.Locale.ROOT, "%.1f KB", kb);
        }
        return String.format(java.util.Locale.ROOT, "%.1f MB", kb / 1024.0);
    }

    /** A single expected palette member from the manifest. */
    private record Member(String file, String sha256, int size) {
    }

    /** The bundle descriptor from the manifest's {@code zip} object. */
    private record Bundle(String file, long size, String sha256) {
    }

    /**
     * Summary of a download run.
     *
     * @param downloaded number of palettes newly fetched and verified
     * @param skipped    number of palettes already present and valid
     * @param failed     number of palettes that could not be downloaded or verified
     * @param manifestOk whether the manifest was successfully fetched
     */
    public record Result(int downloaded, int skipped, int failed, boolean manifestOk) {
    }
}
