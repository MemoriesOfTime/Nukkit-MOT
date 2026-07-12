package cn.nukkit.utils.serverconfig;

import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Locale;
import java.util.UUID;
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
        File resourcePacksSource = new File(dataPath, "resource_packs_netease");
        File behaviourPacksSource = new File(dataPath, "behaviour_packs_netease");
        if (!isDirectory(resourcePacksSource) && !isDirectory(behaviourPacksSource)) {
            return;
        }

        Config packConfig = loadPackConfig(packConfigFile);
        if (packConfig == null) {
            return;
        }

        migrateDirectory(
                resourcePacksSource,
                new File(dataPath, "resource_packs"),
                packConfigFile,
                packConfig
        );
        migrateDirectory(
                behaviourPacksSource,
                new File(dataPath, "behaviour_packs"),
                packConfigFile,
                packConfig
        );
    }

    /**
     * 将源目录中的所有文件迁移到目标目录，文件名插入 {@code .netease.} 后缀。
     * 加密密钥文件内容写入共享的 packs.yml。
     *
     * @param srcDir 旧网易目录（如 resource_packs_netease）
     * @param destDir 统一目标目录（如 resource_packs）
     * @param packConfigFile 资源包共享配置文件
     * @param packConfig 已验证的资源包配置
     */
    private static void migrateDirectory(File srcDir, File destDir, File packConfigFile, Config packConfig) {
        if (!isDirectory(srcDir)) {
            return;
        }

        File[] files = srcDir.listFiles();
        if (files == null) {
            return;
        }

        try {
            Files.createDirectories(destDir.toPath());
        } catch (IOException e) {
            log.warn("Failed to create resource pack destination directory {}", destDir, e);
            return;
        }

        int migrated = 0;
        for (File file : files) {
            if (file.isDirectory()) {
                // 目录类型资源包
                File dest = findAvailableDestination(file, new File(destDir, insertNeteaseSuffix(file.getName())));
                File keyFile = new File(srcDir, file.getName() + ".key");
                KeyMigrationResult keyResult = prepareKeyMigration(keyFile, file, packConfigFile, packConfig);
                if (move(file, dest)) {
                    migrated++;
                    finishKeyMigration(keyFile, dest, keyResult);
                }
            } else if (file.getName().endsWith(".key")) {
                // .key 文件由对应的 pack 文件处理时一并迁移，这里跳过独立出现的 .key
                continue;
            } else if (isPackFile(file.getName())) {
                // .zip / .mcpack 等资源包文件
                File dest = findAvailableDestination(file, new File(destDir, insertNeteaseSuffix(file.getName())));
                File keyFile = new File(srcDir, file.getName() + ".key");
                KeyMigrationResult keyResult = prepareKeyMigration(keyFile, file, packConfigFile, packConfig);
                if (move(file, dest)) {
                    migrated++;
                    finishKeyMigration(keyFile, dest, keyResult);
                }
            }
        }

        // 恢复旧版本在 pack 已移动、key 尚未写入配置时中断的状态。
        for (File file : files) {
            if (file.getName().endsWith(".key") && file.exists()) {
                String packName = file.getName().substring(0, file.getName().length() - 4);
                File packFile = new File(srcDir, packName);
                if (!packFile.exists()) {
                    File migratedPack = new File(destDir, insertNeteaseSuffix(packName));
                    if (migratedPack.exists()) {
                        KeyMigrationResult keyResult = prepareKeyMigration(
                                file,
                                migratedPack,
                                packConfigFile,
                                packConfig
                        );
                        finishKeyMigration(file, migratedPack, keyResult);
                    } else {
                        log.warn("Found key file without its legacy or migrated pack, keeping for manual recovery: {}", file.getName());
                    }
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
     * 在移动 pack 前将密钥持久化；无法安全持久化时标记为随 pack 保留。
     * <p>
     * Persist the key before moving the pack, or mark it to remain beside the pack as fallback.
     */
    private static KeyMigrationResult prepareKeyMigration(
            File keyFile,
            File packFile,
            File packConfigFile,
            Config packConfig
    ) {
        if (!keyFile.exists()) {
            return KeyMigrationResult.NONE;
        }

        String uuid = readPackUuid(packFile);
        if (uuid == null) {
            log.warn("Failed to read a valid UUID from {}, keeping .key file as fallback", packFile.getName());
            return KeyMigrationResult.PRESERVE_WITH_PACK;
        }

        try {
            String key = Files.readString(keyFile.toPath());
            if (key.isEmpty()) {
                return KeyMigrationResult.PERSISTED;
            }
            File parent = packConfigFile.getParentFile();
            if (parent != null) {
                Files.createDirectories(parent.toPath());
            }
            packConfig.set(uuid + ".key", key);
            if (!savePackConfigAtomically(packConfig, packConfigFile)) {
                log.warn("Failed to save encryption key for pack {} into packs.yml", uuid);
                return KeyMigrationResult.PRESERVE_WITH_PACK;
            }
            log.info("Migrated encryption key for pack {} into packs.yml", uuid);
            return KeyMigrationResult.PERSISTED;
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to migrate key file {}", keyFile, e);
            return KeyMigrationResult.PRESERVE_WITH_PACK;
        }
    }

    private static boolean savePackConfigAtomically(Config packConfig, File packConfigFile) throws IOException {
        Path target = packConfigFile.toPath();
        Path parent = target.getParent();
        if (parent == null) {
            return false;
        }
        Files.createDirectories(parent);
        Path temporary = Files.createTempFile(parent, "packs.yml.", ".tmp");
        try {
            if (!packConfig.save(temporary.toFile())) {
                return false;
            }
            try {
                Files.move(
                        temporary,
                        target,
                        StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE
                );
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    private static void finishKeyMigration(File keyFile, File packFile, KeyMigrationResult result) {
        if (!keyFile.exists()) {
            return;
        }
        if (result == KeyMigrationResult.PERSISTED) {
            if (!keyFile.delete()) {
                log.warn("Failed to delete migrated key file {}", keyFile);
            }
        } else if (result == KeyMigrationResult.PRESERVE_WITH_PACK) {
            preserveKeyFile(keyFile, packFile);
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
        if (packFile.isDirectory()) {
            File manifestFile = new File(packFile, "manifest.json");
            if (!manifestFile.isFile()) {
                manifestFile = new File(packFile, "pack_manifest.json");
            }
            if (!manifestFile.isFile()) {
                return null;
            }
            try (var reader = Files.newBufferedReader(manifestFile.toPath(), StandardCharsets.UTF_8)) {
                return readManifestUuid(JsonParser.parseReader(reader).getAsJsonObject());
            } catch (Exception e) {
                log.warn("Failed to read manifest from {}", packFile.getName(), e);
                return null;
            }
        }

        try (ZipFile zip = new ZipFile(packFile)) {
            ZipEntry entry = zip.getEntry("manifest.json");
            if (entry == null) {
                entry = zip.getEntry("pack_manifest.json");
            }
            if (entry == null) {
                entry = zip.stream()
                        .filter(e -> !e.isDirectory())
                        .filter(e -> {
                            File fe = new File(e.getName());
                            if (!fe.getName().equalsIgnoreCase("manifest.json")
                                    && !fe.getName().equalsIgnoreCase("pack_manifest.json")) {
                                return false;
                            }
                            return fe.getParent() == null || fe.getParentFile().getParent() == null;
                        })
                        .findFirst()
                        .orElse(null);
            }
            if (entry == null) {
                return null;
            }
            JsonObject manifest = JsonParser.parseReader(
                    new InputStreamReader(zip.getInputStream(entry), StandardCharsets.UTF_8)
            ).getAsJsonObject();
            return readManifestUuid(manifest);
        } catch (Exception e) {
            log.warn("Failed to read manifest from {}", packFile.getName(), e);
        }
        return null;
    }

    private static String readManifestUuid(JsonObject manifest) {
        if (!manifest.has("header")) {
            return null;
        }
        JsonObject header = manifest.getAsJsonObject("header");
        if (!header.has("uuid")) {
            return null;
        }
        return UUID.fromString(header.get("uuid").getAsString()).toString();
    }

    private static boolean isPackFile(String fileName) {
        return fileName.toLowerCase(Locale.ROOT).endsWith(".zip") || fileName.toLowerCase(Locale.ROOT).endsWith(".mcpack");
    }

    private static Config loadPackConfig(File packConfigFile) {
        if (!packConfigFile.exists()) {
            return new Config(Config.YAML);
        }
        try {
            Config config = new Config(packConfigFile, Config.YAML);
            if (!hasValidPackConfigStructure(config)) {
                log.warn("Invalid packs.yml structure: every top-level entry must be a pack section");
                return null;
            }
            return config;
        } catch (RuntimeException e) {
            log.warn("Failed to load packs.yml; resource pack migration was skipped before moving files", e);
            return null;
        }
    }

    private static boolean hasValidPackConfigStructure(Config config) {
        for (Object value : config.getRootSection().values()) {
            if (!(value instanceof ConfigSection)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isDirectory(File directory) {
        return directory.exists() && directory.isDirectory();
    }

    private static File findAvailableDestination(File source, File preferredDestination) {
        if (!preferredDestination.exists()) {
            return preferredDestination;
        }
        String name = preferredDestination.getName();
        int extensionIndex = source.isDirectory() ? -1 : name.lastIndexOf('.');
        String baseName = extensionIndex > 0 ? name.substring(0, extensionIndex) : name;
        String extension = extensionIndex > 0 ? name.substring(extensionIndex) : "";
        for (int index = 1; ; index++) {
            File candidate = new File(preferredDestination.getParentFile(), baseName + "." + index + extension);
            if (!candidate.exists()) {
                log.warn("Target {} already exists; migrating {} as {}", preferredDestination, source.getName(), candidate.getName());
                return candidate;
            }
        }
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

    private enum KeyMigrationResult {
        NONE,
        PERSISTED,
        PRESERVE_WITH_PACK
    }
}
