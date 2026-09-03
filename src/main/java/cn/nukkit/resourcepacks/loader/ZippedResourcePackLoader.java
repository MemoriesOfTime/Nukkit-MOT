package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.utils.Config;
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
        var baseLang = Server.getInstance().getLanguage();
        List<ResourcePack> loadedResourcePacks = new ArrayList<>();
        for (File pack : orderedPackFiles()) {
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
                        resourcePack = new ZippedResourcePack(file, packType);
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
            } catch (IllegalArgumentException e) {
                log.warn(baseLang.translateString("nukkit.resources.fail", pack.getName(), e.getMessage()), e);
            }
        }
        return loadedResourcePacks;
    }

    /**
     * Order the packs the way the server owner declared them, not the way the filesystem
     * happened to list them.
     *
     * <p>{@link File#listFiles()} returns directory order, so the resource pack stack sent to
     * every client depended on inode order: the same packs could be applied in a different
     * priority after a redeploy, silently swapping which pack wins for a shared texture atlas
     * key. Servers migrated from other server software already carry a {@code resource_packs.yml}
     * with an explicit {@code resource_stack} list, so it is read here as the intended order.
     * The first entry keeps the highest priority, matching the client stack order.
     *
     * <p>A pack present on disk but missing from the list is still loaded — removing it would
     * silently drop content — but it is appended after every declared pack and reported, so an
     * unexpected leftover archive can no longer outrank the packs that were listed on purpose.
     */
    protected File[] orderedPackFiles() {
        File[] packs = this.path.listFiles();
        if (packs == null) {
            return new File[0];
        }
        List<String> declared = declaredStack();
        File[] ordered = packs.clone();
        Arrays.sort(ordered, Comparator
                .comparingInt((File pack) -> {
                    int index = declared.indexOf(pack.getName());
                    return index < 0 ? declared.size() : index;
                })
                .thenComparing(File::getName));
        if (!declared.isEmpty()) {
            for (File pack : ordered) {
                if (!shouldIgnoreFile(pack.getName()) && !declared.contains(pack.getName())) {
                    log.warn("Resource pack {} is not listed in resource_packs.yml; it is applied "
                            + "below every declared pack", pack.getName());
                }
            }
        }
        return ordered;
    }

    protected List<String> declaredStack() {
        File config = new File(Nukkit.DATA_PATH, "resource_packs.yml");
        if (!config.isFile()) {
            return Collections.emptyList();
        }
        try {
            return new Config(config, Config.YAML).getStringList("resource_stack");
        } catch (RuntimeException e) {
            log.warn("Failed to read resource_packs.yml; packs are ordered by name", e);
            return Collections.emptyList();
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

        File tempFile;
        try {
            tempFile = File.createTempFile("pack", ".zip");
            tempFile.deleteOnExit();

            FileTime time = FileTime.fromMillis(0);
            try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(tempFile))) {
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
            throw new RuntimeException("Unable to create temporary mcpack file", e);
        }
        return tempFile;
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
