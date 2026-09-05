package cn.nukkit.resourcepacks;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import com.google.gson.JsonParser;
import org.jetbrains.annotations.ApiStatus;

import java.io.*;
import java.nio.ByteBuffer;
import java.nio.channels.ClosedChannelException;
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

    private FileChannel snapshotChannel;
    private int packSize;
    private byte[] sha256;
    /** Guards against a stack trace per chunk once reloadPacks() has closed the channel. */
    private boolean channelCloseReported;

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
     * @param alreadySnapshot file is a fresh private snapshot (produced by
     *                        {@code ZippedResourcePackLoader#loadDirectoryPack}); the constructor takes
     *                        ownership of it: held without copying, deleted here on failure
     */
    @ApiStatus.Internal
    public ZippedResourcePack(File file, SupportType packType, boolean alreadySnapshot) {
        if (!file.exists()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage()
                    .translateString("nukkit.resources.zip.not-found", file.getName()));
        }

        this.setSupportType(packType);

        File parentFolder = file.getParentFile();
        if (parentFolder == null || !parentFolder.isDirectory()) {
            throw new IllegalArgumentException("Invalid resource pack path");
        }

        File snapshotTmp = null;
        try {
            if (alreadySnapshot) {
                long length = file.length();
                if (length > Integer.MAX_VALUE) {
                    throw new IOException("Resource pack too large (" + length + " bytes): " + file.getName());
                }
                this.packSize = (int) length;
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
                promoteSnapshot(snapshotTmp, new File(snapshotTmp.getParentFile(), file.getName()));
            }
        } catch (IOException e) {
            cleanupOnFailure(snapshotTmp, alreadySnapshot ? file : null);
            throw new IllegalArgumentException(
                    "Failed to load resource pack snapshot: " + file.getName(), e);
        } catch (RuntimeException e) {
            cleanupOnFailure(snapshotTmp, alreadySnapshot ? file : null);
            throw e;
        }
        // 加密密钥只能来自 packs.yml（由 ResourcePackManager.applyPackConfig 注入）。
        // Encryption keys now come exclusively from packs.yml (injected by ResourcePackManager.applyPackConfig).
    }

    /** Releases the channel and deletes the orphaned snapshot so it cannot outlive the failed instance. */
    private void cleanupOnFailure(File snapshotTmp, File ownedSnapshot) {
        close();
        File orphan = snapshotTmp != null ? snapshotTmp : ownedSnapshot;
        if (orphan != null) {
            //noinspection ResultOfMethodCallIgnored
            orphan.delete();
        }
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
     * A failed rename is harmless: callers keep serving from the temp file, and the
     * next {@code cleanSnapshotCache} sweep removes it once no channel pins it
     * (on Windows the stable name is unusable while an old channel is still open,
     * because the delete leaves it in the pending state where re-creation fails).
     *
     * @return whether the rename succeeded
     */
    @ApiStatus.Internal
    public static boolean promoteSnapshot(File tmp, File stable) {
        try {
            Files.move(tmp.toPath(), stable.toPath(),
                    StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            return true;
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            try {
                Files.move(tmp.toPath(), stable.toPath(), StandardCopyOption.REPLACE_EXISTING);
                return true;
            } catch (IOException ignored) {
                return false;
            }
        } catch (IOException ignored) {
            return false;
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
        } catch (ClosedChannelException e) {
            // Expected after reloadPacks() closed this instance: pre-login players
            // whose in-flight download still pins the old instance (their client
            // fails validation and re-downloads on rejoin), or a stale reference
            // held by a plugin. Report once instead of a stack trace per chunk.
            if (!this.channelCloseReported) {
                this.channelCloseReported = true;
                Server.getInstance().getLogger().logException(e);
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().logException(e);
        }

        return chunk;
    }

    /**
     * Closes the snapshot channel. Called by {@link ResourcePackManager#reloadPacks()}
     * before replacing this instance: releasing the handle first lets the snapshot
     * file be physically deleted and re-created (on Windows a pinned name stays in
     * delete-pending state, blocking re-creation with ERROR_ACCESS_DENIED).
     */
    @Override
    public void close() {
        if (this.snapshotChannel != null) {
            try {
                this.snapshotChannel.close();
            } catch (IOException ignored) {
            }
        }
    }

    /** Monitoring/test hook: whether the snapshot channel is closed. */
    @ApiStatus.Internal
    boolean isSnapshotClosed() {
        return this.snapshotChannel == null || !this.snapshotChannel.isOpen();
    }

    /**
     * @deprecated Use {@link #setCDNUrl(String)} instead
     */
    @Deprecated
    public void setCdnUrl(String cdnUrl) {
        this.setCDNUrl(cdnUrl);
    }
}
