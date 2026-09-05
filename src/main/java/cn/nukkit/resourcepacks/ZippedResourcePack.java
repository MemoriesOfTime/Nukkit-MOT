package cn.nukkit.resourcepacks;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 构造时把源归档复制为磁盘快照，此后所有数据均从钉在该副本上的 FileChannel 读取。
 * <p>
 * The source archive is copied to a disk snapshot at construction; everything is
 * then served from a FileChannel pinned to that copy, so replacing the source
 * at runtime cannot mix identities.
 */
public class ZippedResourcePack extends AbstractResourcePack implements Closeable {

    /** Test injection point for the snapshot cache root; null = DATA_PATH default. */
    static volatile File cacheRootOverride;

    private File file;
    private FileChannel snapshotChannel;
    private int packSize;
    private byte[] sha256;

    public ZippedResourcePack(File file) {
        this(file, SupportType.UNIVERSAL);
    }

    /**
     * @deprecated Use {@link #ZippedResourcePack(File, SupportType)} instead
     */
    @Deprecated
    public ZippedResourcePack(File file, boolean isNetEase) {
        this(file, isNetEase ? SupportType.NETEASE : SupportType.UNIVERSAL);
    }

    public ZippedResourcePack(File file, SupportType packType) {
        this(file, packType, false);
    }

    /**
     * @param alreadySnapshot file is already a fresh private snapshot (produced by
     *                        {@code ZippedResourcePackLoader#loadDirectoryPack}); held without copying
     */
    @ApiStatus.Internal
    public ZippedResourcePack(File file, SupportType packType, boolean alreadySnapshot) {
        if (!file.exists()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.not-found", file.getName()));
        }

        this.file = file;
        this.setSupportType(packType);

        File parentFolder = this.file.getParentFile();
        if (parentFolder == null || !parentFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid resource pack path");
        }

        File snapshotTmp = null;
        try {
            if (alreadySnapshot) {
                this.packSize = (int) file.length();
                this.sha256 = digestFile(file);
            } else {
                snapshotTmp = copyToSnapshot(file);
            }
            File snapshot = snapshotTmp != null ? snapshotTmp : file;
            this.snapshotChannel = FileChannel.open(snapshot.toPath(), StandardOpenOption.READ);
            loadManifest(snapshot);
            if (!this.verifyManifest()) {
                throw new IllegalArgumentException(Server.getInstance().getLanguage()
                        .translateString("nukkit.resources.zip.invalid-manifest"));
            }
            if (snapshotTmp != null) {
                promoteSnapshot(snapshotTmp, file.getName());
            }
        } catch (IOException e) {
            close();
            if (snapshotTmp != null) {
                //noinspection ResultOfMethodCallIgnored
                snapshotTmp.delete();
            }
            throw new IllegalArgumentException(
                    "Failed to load resource pack snapshot: " + file.getName(), e);
        } catch (RuntimeException e) {
            close();
            if (snapshotTmp != null) {
                //noinspection ResultOfMethodCallIgnored
                snapshotTmp.delete();
            }
            throw e;
        }
        // 加密密钥只能来自 packs.yml（由 ResourcePackManager.applyPackConfig 注入）。
        // Encryption keys now come exclusively from packs.yml (injected by ResourcePackManager.applyPackConfig).
    }

    private void loadManifest(File snapshot) throws IOException {
        try (ZipFile zip = new ZipFile(snapshot)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                entry = zip.getEntry("pack_manifest.json");
            }
            if (entry == null) {
                entry = zip.stream()
                        .filter(e -> !e.isDirectory() &&
                                (e.getName().toLowerCase(Locale.ROOT).endsWith("manifest.json") || e.getName().toLowerCase(Locale.ROOT).endsWith("pack_manifest.json")))
                        .filter(e -> {
                            File fe = new File(e.getName());
                            if (!fe.getName().equalsIgnoreCase("manifest.json") && !fe.getName().equalsIgnoreCase("pack_manifest.json")) {
                                return false;
                            }
                            return fe.getParent() == null || fe.getParentFile().getParent() == null;
                        })
                        .findFirst()
                        .orElseThrow(() -> new IllegalArgumentException(
                                Server.getInstance().getLanguage().translateString("nukkit.resources.zip.no-manifest")));
            }

            this.manifest = new JsonParser()
                    .parse(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))
                    .getAsJsonObject();
        }
    }

    /** Stream-copies the source to a temp cache file, computing size and SHA-256 in the same pass. */
    private File copyToSnapshot(File source) throws IOException {
        File cacheDir = new File(snapshotCacheRoot(), source.getParentFile().getName());
        Files.createDirectories(cacheDir.toPath());
        File tmp = new File(cacheDir,
                source.getName() + "." + Long.toUnsignedString(System.nanoTime(), 36) + ".tmp");
        MessageDigest digest = sha256Digest();
        long total = 0;
        try (InputStream in = Files.newInputStream(source.toPath());
             OutputStream out = Files.newOutputStream(tmp.toPath(),
                     StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE)) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
                out.write(buffer, 0, read);
                total += read;
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        if (total > Integer.MAX_VALUE) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw new IOException("Resource pack too large (" + total + " bytes): " + source.getName());
        }
        this.packSize = (int) total;
        this.sha256 = digest.digest();
        return tmp;
    }

    /**
     * Renames the temp snapshot to a stable name for inspection and orphan cleanup.
     * A failed rename is harmless: the pinned channel keeps serving the temp file.
     */
    private static void promoteSnapshot(File tmp, String stableName) {
        File stable = new File(tmp.getParentFile(), stableName);
        try {
            Files.move(tmp.toPath(), stable.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), stable.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
            }
        } catch (IOException ignored) {
        }
    }

    private static byte[] digestFile(File file) throws IOException {
        MessageDigest digest = sha256Digest();
        try (InputStream in = Files.newInputStream(file.toPath())) {
            byte[] buffer = new byte[64 * 1024];
            int read;
            while ((read = in.read(buffer)) != -1) {
                digest.update(buffer, 0, read);
            }
        }
        return digest.digest();
    }

    private static MessageDigest sha256Digest() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 digest unavailable", e);
        }
    }

    /** Snapshot cache root; the package-private {@link #cacheRootOverride} is for tests only. */
    @ApiStatus.Internal
    public static File snapshotCacheRoot() {
        return cacheRootOverride != null ? cacheRootOverride
                : new File(Nukkit.DATA_PATH, "cache/resourcepacks");
    }

    @Override
    public int getPackSize() {
        return this.packSize;
    }

    @Override
    public byte[] getSha256() {
        return this.sha256;
    }

    @Override
    public byte[] getPackChunk(int off, int len) {
        int size = this.packSize;
        byte[] chunk;
        if (size - off > len) {
            chunk = new byte[len];
        } else {
            chunk = new byte[size - off];
        }

        if (chunk.length == 0) {
            return chunk;
        }
        try {
            ByteBuffer buffer = ByteBuffer.wrap(chunk);
            while (buffer.hasRemaining()) {
                // positional read: thread-safe, independent of the channel position
                if (this.snapshotChannel.read(buffer, off + buffer.position()) < 0) {
                    break;
                }
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
        }

        return chunk;
    }

    /** Closes the snapshot channel; packs live for the process lifetime, so explicit close is rarely needed. */
    @Override
    public void close() {
        if (this.snapshotChannel != null) {
            try {
                this.snapshotChannel.close();
            } catch (IOException ignored) {
            }
        }
    }

    /**
     * @deprecated Use {@link #setCDNUrl(String)} instead
     */
    @Deprecated
    public void setCdnUrl(String cdnUrl) {
        this.setCDNUrl(cdnUrl);
    }
}
