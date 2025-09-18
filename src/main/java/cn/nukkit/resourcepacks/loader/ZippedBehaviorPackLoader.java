package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedResourcePack;
import com.google.common.io.Files;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.attribute.FileTime;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Log4j2
public class ZippedBehaviorPackLoader implements ResourcePackLoader {

    //资源包文件存放地址
    protected final File path;

    protected boolean isNetEase = false;

    public ZippedBehaviorPackLoader(File path) {
        this.path = path;
        if (!path.exists()) {
            path.mkdirs();
        } else if (!path.isDirectory()) {
            throw new IllegalArgumentException(Server.getInstance().getLanguage().translateString("nukkit.resources.invalid-path", path.getName()));
        }
    }

    public ZippedBehaviorPackLoader(File path, boolean isNetEase) {
        this(path);
        this.isNetEase = isNetEase;
    }

    @Override
    public List<ResourcePack> loadPacks() {
        var baseLang = Server.getInstance().getLanguage();
        List<ResourcePack> loadedResourcePacks = new ArrayList<>();
        for (File pack : path.listFiles()) {
            try {
                ZippedResourcePack resourcePack = null;
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
                if (resourcePack != null && resourcePack.isBehaviourPack()) {
//                    resourcePack.setNetEase(this.isNetEase);
                    loadedResourcePacks.add(resourcePack);
                    log.info(baseLang.translateString("nukkit.resources.zip.loaded", pack.getName()));
                }
            } catch (IllegalArgumentException e) {
                log.warn(baseLang.translateString("nukkit.resources.fail", pack.getName(), e.getMessage()), e);
            }
        }
        return loadedResourcePacks;
    }

    protected static File loadDirectoryPack(File directory) {
        // 首先检查根目录是否有manifest文件
        File manifestFile = new File(directory, "manifest.json");
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            manifestFile = new File(directory, "pack_manifest.json");
        }
        
        // 如果根目录没有manifest文件，递归搜索子文件夹
        File foundManifest = null;
        if (!manifestFile.exists() || !manifestFile.isFile()) {
            foundManifest = findManifestInSubdirectories(directory);
            if (foundManifest == null) {
                return null;
            }
        } else {
            foundManifest = manifestFile;
        }

        File tempFile;
        try {
            tempFile = File.createTempFile("pack", ".zip");
            tempFile.deleteOnExit();
            // 将zip文件保存到服务器根目录，便于对比
            // String packName = directory.getName();
            // tempFile = new File(packName + "_generated.zip");
            // // 如果文件已存在，先删除
            // if (tempFile.exists()) {
            //     tempFile.delete();
            // }

            FileTime time = FileTime.fromMillis(0);
            try (ZipOutputStream stream = new ZipOutputStream(new FileOutputStream(tempFile))) {
                stream.setLevel(Deflater.BEST_COMPRESSION);
                
//                // 首先将找到的manifest文件添加到zip根目录
//                String manifestName = foundManifest.getName();
//                ZipEntry manifestEntry = new ZipEntry(manifestName)
//                        .setCreationTime(time)
//                        .setLastModifiedTime(time)
//                        .setLastAccessTime(time);
//                stream.putNextEntry(manifestEntry);
//                stream.write(Files.toByteArray(foundManifest));
//                stream.closeEntry();
                
                // 然后递归添加所有其他文件
                addAllFilesToZip(directory, directory, stream, time, foundManifest);
            }
        } catch (IOException e) {
            throw new RuntimeException("Unable to create temporary mcpack file", e);
        }
        return tempFile;
    }

    /**
     * 在子文件夹中递归搜索manifest.json或pack_manifest.json文件
     * @param directory 要搜索的根目录
     * @return 找到的manifest文件，如果没找到返回null
     */
    protected static File findManifestInSubdirectories(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return null;
        }
        
        for (File file : files) {
            if (file.isDirectory()) {
                // 检查当前子文件夹是否包含manifest文件
                File manifestFile = new File(file, "manifest.json");
                if (manifestFile.exists() && manifestFile.isFile()) {
                    return manifestFile;
                }
                
                manifestFile = new File(file, "pack_manifest.json");
                if (manifestFile.exists() && manifestFile.isFile()) {
                    return manifestFile;
                }
                
                // 递归搜索更深层的子文件夹
                File found = findManifestInSubdirectories(file);
                if (found != null) {
                    return found;
                }
            }
        }
        
        return null;
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

    /**
     * 递归添加目录中的所有文件到zip流中
     * @param rootDirectory 根目录（用于计算相对路径）
     * @param currentDirectory 当前处理的目录
     * @param stream zip输出流
     * @param time 文件时间
     * @param skipFile 要跳过的文件（通常是已经添加的manifest文件）
     */
    private static void addAllFilesToZip(File rootDirectory, File currentDirectory, ZipOutputStream stream, FileTime time, File skipFile) throws IOException {
        File[] files = currentDirectory.listFiles();
        if (files == null) {
            return;
        }
        
        // 如果当前目录不是根目录且为空目录，添加目录条目
        if (!currentDirectory.equals(rootDirectory) && files.length == 0) {
            String relativePath = rootDirectory.toPath().relativize(currentDirectory.toPath()).toString().replace('\\', '/') + "/";
            ZipEntry dirEntry = new ZipEntry(relativePath)
                    .setCreationTime(time)
                    .setLastModifiedTime(time)
                    .setLastAccessTime(time);
            stream.putNextEntry(dirEntry);
            stream.closeEntry();
            return;
        }
        
        // 检查是否有子目录，如果有则为当前目录添加条目（仅当不是根目录时）
        boolean hasSubdirectories = false;
        for (File file : files) {
            if (file.isDirectory() && !file.equals(skipFile)) {
                hasSubdirectories = true;
                break;
            }
        }
        
        // 如果当前目录不是根目录且包含子目录，添加目录条目
        if (!currentDirectory.equals(rootDirectory) && hasSubdirectories) {
            String relativePath = rootDirectory.toPath().relativize(currentDirectory.toPath()).toString().replace('\\', '/') + "/";
            ZipEntry dirEntry = new ZipEntry(relativePath)
                    .setCreationTime(time)
                    .setLastModifiedTime(time)
                    .setLastAccessTime(time);
            stream.putNextEntry(dirEntry);
            stream.closeEntry();
        }
        
        for (File file : files) {
//            // 跳过已经添加的manifest文件
//            if (file.equals(skipFile)) {
//                continue;
//            }
            
            if (file.isDirectory()) {
                // 递归处理子目录
                addAllFilesToZip(rootDirectory, file, stream, time, skipFile);
            } else if (file.isFile()) {
                // 添加文件到zip
                String relativePath = rootDirectory.toPath().relativize(file.toPath()).toString().replace('\\', '/');
                ZipEntry entry = new ZipEntry(relativePath)
                        .setCreationTime(time)
                        .setLastModifiedTime(time)
                        .setLastAccessTime(time);
                stream.putNextEntry(entry);
                stream.write(Files.toByteArray(file));
                stream.closeEntry();
            }
        }
    }
}
