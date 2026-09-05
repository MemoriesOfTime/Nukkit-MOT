package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedResourcePack;
import com.google.common.io.Files;
import lombok.extern.log4j.Log4j2;
import org.iq80.leveldb.fileenv.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.*;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Log4j2
public class ZippedResourcePackLoader implements ResourcePackLoader {

    //资源包文件存放地址
    protected final File path;

    protected ResourcePack.SupportType supportType = ResourcePack.SupportType.UNIVERSAL;

    /**
     * 根据文件名后缀检测资源包类型
     * <p>
     * Detect the resource pack support type by filename suffix.
     * 文件名含 {@code .netease.} 或以 {@code .netease} 结尾时视为网易版。
     * <p>
     * Names containing {@code .netease.} or ending with {@code .netease} are treated as NetEase packs.
     *
     * @param fileName the pack file/directory name
     * @return detected {@link ResourcePack.SupportType}
     */
    protected ResourcePack.SupportType detectSupportType(String fileName) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(".netease") || normalizedName.contains(".netease.")) {
            return ResourcePack.SupportType.NETEASE;
        }
        return this.supportType;
    }

    protected boolean shouldIgnoreFile(String fileName) {
        return fileName.equalsIgnoreCase("packs.yml");
    }

    public ZippedResourcePackLoader(File path) {
        this.path = path;
        if (!path.exists()) {
            path.mkdirs();
        } else if (!path.isDirectory()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage().translateString("nukkit.resources.invalid-path", path.getName()));
        }
    }

    public ZippedResourcePackLoader(File path, ResourcePack.SupportType supportType) {
        this(path);
        this.supportType = supportType;
    }

    /**
     * @deprecated Use {@link #ZippedResourcePackLoader(File, ResourcePack.SupportType)} instead
     */
    @Deprecated
    public ZippedResourcePackLoader(File path, boolean isNetEase) {
        this(path, isNetEase ? ResourcePack.SupportType.NETEASE : ResourcePack.SupportType.UNIVERSAL);
    }

    @Override
    public List<ResourcePack> loadPacks() {
        cleanSnapshotCache();
        var baseLang = Server.getInstance().getLanguage();
        List<ResourcePack> loadedResourcePacks = new ArrayList<>();
        for (File pack : path.listFiles()) {
            if (shouldIgnoreFile(pack.getName())) {
                continue;
            }
            try {
                ResourcePack resourcePack = null;
                String fileExt = Files.getFileExtension(pack.getName());
                ResourcePack.SupportType packType = detectSupportType(pack.getName());
                if (pack.isDirectory()) {
                    File file = loadDirectoryPack(pack);
                    if (file != null) {
                        resourcePack = new ZippedResourcePack(file, packType, true);
                    }
                } else {
                    switch (fileExt) {
                        case "zip", "mcpack" -> resourcePack = new ZippedResourcePack(pack, packType);
                        default -> log.warn(baseLang.translateString("nukkit.resources.unknown-format", pack.getName()));
                    }
                }
                if (resourcePack != null) {
                    loadedResourcePacks.add(resourcePack);
                    log.info(baseLang.translateString("nukkit.resources.zip.loaded", pack.getName()));
                }
            } catch (RuntimeException e) {
                // IllegalArgumentException = bad pack (skip); RuntimeException also covers
                // loadDirectoryPack I/O failures, so one broken pack cannot abort the whole load
                log.warn(baseLang.translateString("nukkit.resources.fail", pack.getName(), e.getMessage()), e);
            }
        }
        return loadedResourcePacks;
    }

    /**
     * Wipes this loader's snapshot cache dir: crash leftovers ({@code *.tmp}) and
     * orphans of removed packs. Instances still holding a channel keep serving from
     * the unlinked inode (POSIX); on Windows, because channels open files with
     * FILE_SHARE_DELETE, the delete succeeds but stays pending until the channel
     * closes — which is why {@code ResourcePackManager.reloadPacks()} closes old
     * instances before reloading.
     */
    protected void cleanSnapshotCache() {
        File cacheDir = new File(ZippedResourcePack.snapshotCacheRoot(), this.path.getName());
        File[] children = cacheDir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (!child.delete()) {
                log.debug("Failed to delete resource pack snapshot cache entry: {}", child);
            }
        }
    }

    protected static File loadDirectoryPack(File directory) {
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            manifestFile = new File(directory, "pack_manifest.json");
            if (!manifestFile.exists() || !manifestFile.isFile()) {
                return null;
            }
        }

        File snapshotFile = null;
        try {
            // Written straight into the snapshot cache: already a fresh private
            // snapshot, and safe from /tmp reapers on long-running servers.
            File parent = directory.getParentFile();
            File cacheDir = parent != null
                    ? new File(ZippedResourcePack.snapshotCacheRoot(), parent.getName())
                    : ZippedResourcePack.snapshotCacheRoot();
            //noinspection ResultOfMethodCallIgnored
            cacheDir.mkdirs();
            // Unique temp name: if a previous instance still pins the stable name
            // (Windows delete-pending), re-creating it would fail — the tmp file is
            // always creatable, and promotion to the stable name degrades gracefully.
            snapshotFile = new File(cacheDir,
                    directory.getName() + ".zip." + Long.toUnsignedString(System.nanoTime(), 36) + ".tmp");

            FileTime time = FileTime.fromMillis(0);
            try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(snapshotFile))) {
                stream.setLevel(Deflater.BEST_COMPRESSION);
                Collection<File> files = new TreeSet<>(FileUtils.listFiles(directory)); // todo: add further checks
                for (File file : files) {
                    if (file.isDirectory()) {
                        for (File directoryFile : getDirectoryFiles(file)) {
                            ZipEntry entry = new ZipEntry(directory.toPath().relativize(directoryFile.toPath()).toString())
                                    .setCreationTime(time)
                                    .setLastModifiedTime(time)
                                    .setLastAccessTime(time);
                            stream.putNextEntry(entry);
                            stream.write(Files.toByteArray(directoryFile));
                            stream.closeEntry();
                        }
                    } else if (file.isFile()) {
                        ZipEntry entry = new ZipEntry(directory.toPath().relativize(file.toPath()).toString())
                                .setCreationTime(time)
                                .setLastModifiedTime(time)
                                .setLastAccessTime(time);
                        stream.putNextEntry(entry);
                        stream.write(Files.toByteArray(file));
                        stream.closeEntry();
                    }
                }
            }
        } catch (IOException e) {
            if (snapshotFile != null) {
                //noinspection ResultOfMethodCallIgnored
                snapshotFile.delete();
            }
            throw new RuntimeException("Unable to create temporary mcpack file", e);
        }
        File stable = new File(snapshotFile.getParentFile(), directory.getName() + ".zip");
        return ZippedResourcePack.promoteSnapshot(snapshotFile, stable) ? stable : snapshotFile;
    }

    protected static List<File> getDirectoryFiles(File directory) {
        List<File> files = new ArrayList<>();
        File[] getFiles = directory.listFiles();
        if (getFiles == null) {
            return files;
        }
        for (File file : getFiles) {
            if (file.isDirectory()) {
                files.addAll(getDirectoryFiles(file));
            } else {
                files.add(file);
            }
        }
        return files;
    }
}
