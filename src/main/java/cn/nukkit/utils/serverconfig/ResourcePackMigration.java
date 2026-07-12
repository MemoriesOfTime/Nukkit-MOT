package cn.nukkit.utils.serverconfig;

import cn.nukkit.utils.Config;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * 将旧的分离式网易资源包/行为包目录迁移到统一目录结构。
 * <p>
 * Migrates legacy separated NetEase resource/behaviour pack directories into the unified
 * directory structure with {@code .netease.} filename suffixes.
 *
 * <h3>迁移规则 / Migration rules</h3>
 * <ul>
 *   <li>{@code resource_packs_netease/*} → {@code resource_packs/*}（文件名加 {@code .netease.}）</li>
 *   <li>{@code behaviour_packs_netease/*} → {@code behaviour_packs/*}（文件名加 {@code .netease.}）</li>
 *   <li>加密密钥文件 {@code <pack>.key} 的内容写入 {@code packs.yml} 的对应 UUID，然后删除原文件</li>
 *   <li>迁移完成后删除空的旧目录</li>
 * </ul>
 */
@Log4j2
public final class ResourcePackMigration {

    private ResourcePackMigration() {
    }

    private static final String NETEASE_SUFFIX = ".netease";

    /**
     * 执行迁移。仅在旧目录存在时生效。
     * <p>
     * Perform the migration. Only takes effect when legacy directories exist.
     *
     * @param dataPath 服务器数据根目录（Nukkit.DATA_PATH）
     */
    public static void migrate(File dataPath) {
        File packConfigFile = new File(dataPath, "resource_packs" + File.separator + "packs.yml");
        migrateDirectory(
                new File(dataPath, "resource_packs_netease"),
                new File(dataPath, "resource_packs"),
                packConfigFile
        );
        migrateDirectory(
                new File(dataPath, "behaviour_packs_netease"),
                new File(dataPath, "behaviour_packs"),
                packConfigFile
        );
    }

    /**
     * 将源目录中的所有文件迁移到目标目录，文件名插入 {@code .netease.} 后缀。
     * 加密密钥文件内容写入共享的 packs.yml。
     *
     * @param srcDir 旧网易目录（如 resource_packs_netease）
     * @param destDir 统一目标目录（如 resource_packs）
     * @param packConfigFile 资源包共享配置文件
     */
    private static void migrateDirectory(File srcDir, File destDir, File packConfigFile) {
        if (!srcDir.exists() || !srcDir.isDirectory()) {
            return;
        }

        File[] files = srcDir.listFiles();
        if (files == null) {
            return;
        }

        if (!destDir.exists()) {
            destDir.mkdirs();
        }

        int migrated = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                // 目录类型资源包
                File dest = new File(destDir, insertNeteaseSuffix(file.getName()));
                if (move(file, dest)) {
                    migrated++;
                }
            } else if (file.getName().endsWith(".key")) {
                // .key 文件由对应的 pack 文件处理时一并迁移，这里跳过独立出现的 .key
                continue;
            } else if (isPackFile(file.getName())) {
                // .zip / .mcpack 等资源包文件
                File dest = new File(destDir, insertNeteaseSuffix(file.getName()));
                if (move(file, dest)) {
                    migrated++;
                    // 同步迁移加密密钥到 packs.yml
                    File keyFile = new File(srcDir, file.getName() + ".key");
                    if (keyFile.exists()) {
                        migrateKeyFile(keyFile, dest, packConfigFile);
                    }
                }
            }
        }

        // 无法确认已安全迁移的密钥必须保留，以支持中断后的人工恢复。
        for (File file : files) {
            if (file.getName().endsWith(".key") && file.exists()) {
                String packName = file.getName().substring(0, file.getName().length() - 4);
                File packFile = new File(srcDir, packName);
                if (!packFile.exists()) {
                    log.warn("Found key file without its legacy pack, keeping for manual recovery: {}", file.getName());
                }
            }
        }

        if (migrated > 0) {
            log.info("Migrated {} file(s) from {} to {}", migrated, srcDir.getName(), destDir.getName());
        }

        // 清理系统生成的隐藏文件（如 macOS .DS_Store），然后删除空目录
        cleanSystemFiles(srcDir);
        deleteIfEmpty(srcDir);
    }

    /**
     * 将加密密钥文件内容写入 packs.yml 对应的 UUID，然后删除原文件。
     * <p>
     * Write the encryption key file content into packs.yml under the pack's UUID, then delete the file.
     */
    private static boolean migrateKeyFile(File keyFile, File packFile, File packConfigFile) {
        String uuid = readPackUuid(packFile);
        if (uuid == null) {
            // 无法解析 UUID，保留 .key 文件作为兜底（ZippedResourcePack 构造函数仍能读取）
            log.warn("Failed to read UUID from {}, keeping .key file as fallback", packFile.getName());
            return preserveKeyFile(keyFile, packFile);
        }

        try {
            String key = Files.readString(keyFile.toPath());
            if (key.isEmpty()) {
                return keyFile.delete();
            }
            File parent = packConfigFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            Config config = new Config(packConfigFile, Config.YAML);
            config.set(uuid + ".key", key);
            if (!config.save()) {
                log.warn("Failed to save encryption key for pack {} into packs.yml", uuid);
                return preserveKeyFile(keyFile, packFile);
            }
            log.info("Migrated encryption key for pack {} into packs.yml", uuid);
            return keyFile.delete();
        } catch (IOException e) {
            log.warn("Failed to migrate key file {}", keyFile, e);
            // 兜底：保留 .key 文件
            return preserveKeyFile(keyFile, packFile);
        }
    }

    private static boolean preserveKeyFile(File keyFile, File packFile) {
        File destKey = new File(packFile.getParentFile(), packFile.getName() + ".key");
        return move(keyFile, destKey);
    }

    /**
     * 从资源包文件（zip）的 manifest.json 读取 UUID。
     */
    private static String readPackUuid(File packFile) {
        try (ZipFile zip = new ZipFile(packFile)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                entry = zip.getEntry("pack_manifest.json");
            }
            if (entry == null) {
                entry = zip.stream()
                        .filter(e -> e.getName().toLowerCase(Locale.ROOT).endsWith("manifest.json") && !e.isDirectory())
                        .filter(e -> {
                            File fe = new File(e.getName());
                            return fe.getParent() == null || fe.getParentFile().getParent() == null;
                        })
                        .findFirst()
                        .orElse(null);
            }
            if (entry == null) {
                return null;
            }
            JsonObject manifest = JsonParser.parseReader(new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8))
                    .getAsJsonObject();
            if (manifest.has("header")) {
                JsonObject header = manifest.getAsJsonObject("header");
                if (header.has("uuid")) {
                    return header.get("uuid").getAsString();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to read manifest from {}", packFile.getName(), e);
        }
        return null;
    }

    private static boolean isPackFile(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".zip") || fileName.toLowerCase(Locale.ROOT).endsWith(".mcpack");
    }

    /**
     * 在最后一个扩展名前插入 {@code .netease}。
     * <p>例：{@code mypack.mcpack} → {@code mypack.netease.mcpack}
     */
    private static String insertNeteaseSuffix(String fileName) {
        String normalizedName = fileName.toLowerCase(Locale.ROOT);
        if (normalizedName.endsWith(NETEASE_SUFFIX) || normalizedName.contains(NETEASE_SUFFIX + ".")) {
            return fileName;
        }
        int dot = fileName.lastIndexOf('.');
        if (dot <= 0) {
            return fileName + NETEASE_SUFFIX;
        }
        return fileName.substring(0, dot) + NETEASE_SUFFIX + fileName.substring(dot);
    }

    private static boolean move(File src, File dest) {
        if (dest.exists()) {
            log.warn("Skip migrating {}: target already exists ({})", src.getName(), dest.getAbsolutePath());
            return false;
        }
        try {
            Files.move(src.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException e) {
            log.warn("Failed to migrate {} → {}", src, dest, e);
            return false;
        }
    }

    private static void deleteIfEmpty(File dir) {
        String[] children = dir.list();
        if (children != null && children.length == 0) {
            if (dir.delete()) {
                log.info("Removed empty legacy directory: {}", dir.getName());
            }
        }
    }

    /**
     * 清理操作系统自动生成的隐藏文件（如 macOS 的 .DS_Store、Thumbs.db）。
     * <p>
     * Remove OS-generated hidden files (e.g. macOS {@code .DS_Store}, {@code Thumbs.db})
     * so that {@link #deleteIfEmpty(File)} can clean up the directory.
     */
    private static void cleanSystemFiles(File dir) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            String name = child.getName();
            if (".DS_Store".equals(name) || "Thumbs.db".equals(name)) {
                child.delete();
            }
        }
    }
}
