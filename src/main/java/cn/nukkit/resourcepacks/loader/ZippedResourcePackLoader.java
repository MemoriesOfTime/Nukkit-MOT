package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedResourcePack;
import com.google.common.io.Files;
import lombok.extern.log4j.Log4j2;
import org.iq80.leveldb.util.FileUtils;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.TreeSet;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Log4j2
public class ZippedResourcePackLoader implements ResourcePackLoader {

    //资源包文件存放地址
    protected final File path;

    public ZippedResourcePackLoader(File path) {
        this.path = path;
        if (!path.exists()) {
            path.mkdirs();
        } else if (!path.isDirectory()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage().translateString("nukkit.resources.invalid-path", path.getName()));
        }
    }

    @Override
    public List<ResourcePack> loadPacks() {
        var baseLang = Server.getInstance().getLanguage();
        List<ResourcePack> loadedResourcePacks = new ArrayList<>();
        for (File pack : path.listFiles()) {
            try {
                ResourcePack resourcePack = null;
                String fileExt = Files.getFileExtension(pack.getName());
                if (pack.isDirectory()) {
                    File file = loadDirectoryPack(pack);
                    if (file != null) {
                        resourcePack = new ZippedResourcePack(file);
                    }
                } else if (!fileExt.equals("key")) { //directory resource packs temporarily unsupported
                    switch (fileExt) {
                        case "zip", "mcpack" -> resourcePack = new ZippedResourcePack(pack);
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

    private static File loadDirectoryPack(File directory) {
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            return null;
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
                            ZipEntry entry = new ZipEntry(file.toPath().relativize(directoryFile.toPath()).toString())
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
                return getDirectoryFiles(file);
            } else {
                files.add(file);
            }
        }
        return files;
    }
}
