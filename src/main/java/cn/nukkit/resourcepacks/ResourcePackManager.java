package cn.nukkit.resourcepacks;

import cn.nukkit.GameVersion;
import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;
import cn.nukkit.utils.Utils;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;
import it.unimi.dsi.fastutil.objects.Object2ObjectLinkedOpenHashMap;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.util.*;

import static cn.nukkit.network.protocol.ProtocolInfo.SUPPORTED_PROTOCOLS;

@Log4j2
public class ResourcePackManager {

    private final Map<UUID, ResourcePack> allPacksById = new Object2ObjectLinkedOpenHashMap<>();
    private final Map<UUID, ResourcePack> resourcePacksById = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<ResourcePack> resourcePacks = new LinkedHashSet<>();
    private final Map<UUID, ResourcePack> behaviorPacksById = new Object2ObjectLinkedOpenHashMap<>();
    private final Set<ResourcePack> behaviorPacks = new LinkedHashSet<>();
    private final Set<ResourcePackLoader> loaders;
    private File packConfigFile;

    public ResourcePackManager(ResourcePackLoader... loaders) {
        this(Sets.newHashSet(loaders));
    }

    public ResourcePackManager(Set<ResourcePackLoader> loaders) {
        this(loaders, new File(Nukkit.DATA_PATH, "resource_packs" + File.separator + "packs.yml"));
    }

    public ResourcePackManager(Set<ResourcePackLoader> loaders, File packConfigFile) {
        this.loaders = loaders;
        this.packConfigFile = packConfigFile;
        reloadPacks();
    }

    public ResourcePackManager(File path) {
        this(new ZippedResourcePackLoader(path));
    }

    /**
     * @deprecated use {@link #getResourceStack(GameVersion)}
     */
    @Deprecated
    public ResourcePack[] getResourceStack() {
        return this.resourcePacks.toArray(ResourcePack.EMPTY_ARRAY);
    }

    public ResourcePack[] getResourceStack(GameVersion gameVersion) {
        return this.resourcePacks.stream()
                .filter(pack -> pack.getPackProtocol() <= gameVersion.getProtocol())
                .filter(pack -> pack.getSupportType().isCompatibleWith(gameVersion))
                .toArray(ResourcePack[]::new);
    }

    public ResourcePack[] getBehaviorStack(GameVersion gameVersion) {
        return this.behaviorPacks.stream()
                .filter(pack -> pack.getPackProtocol() <= gameVersion.getProtocol())
                .filter(pack -> pack.getSupportType().isCompatibleWith(gameVersion))
                .toArray(ResourcePack[]::new);
    }

    public ResourcePack getPackById(UUID id) {
        return this.allPacksById.get(id);
    }

    public void registerPackLoader(ResourcePackLoader loader) {
        this.loaders.add(loader);
    }

    /**
     * 重新加载所有资源包。正在下载资源包的预登录玩家不做专门处理：其客户端锁定的
     * 旧实例 size/SHA-256 校验失败后自行报错，重连即按新实例重新下载。
     * <p>
     * Players mid-download (pre-login) are not handled specially: the transfer
     * they pinned fails validation on the client and they re-download from the
     * new instances on rejoin.
     * <p>
     * 必须先关闭旧实例再加载：释放快照文件句柄后，loader 才能物理删除并重建
     * 快照（Windows 上被句柄占用的名字处于 delete-pending，同名重建会以
     * ERROR_ACCESS_DENIED 失败），同时避免旧实例的 fd 与快照磁盘空间泄漏。
     * <p>
     * Old instances are then closed before loading: only with their snapshot
     * handles released can the loaders physically delete and re-create snapshot
     * files (on Windows a pinned name stays delete-pending and re-creation fails
     * with ERROR_ACCESS_DENIED), and it prevents the old instances' file
     * descriptors and snapshot disk space from leaking.
     * <p>
     * 应在主线程调用（与玩家资源包下载同线程，保证关闭时无在途读取）。
     * Must be called on the main thread (same thread as player pack downloads,
     * so no chunk read can be in flight while an old instance is closed).
     */
    public void reloadPacks() {
        for (ResourcePack pack : this.allPacksById.values()) {
            closePackQuietly(pack);
        }
        this.allPacksById.clear();
        this.resourcePacksById.clear();
        this.resourcePacks.clear();
        this.behaviorPacksById.clear();
        this.behaviorPacks.clear();
        this.loaders.forEach(loader -> {
            var loadedPacks = loader.loadPacks();
            loadedPacks.forEach(pack -> {
                this.allPacksById.put(pack.getPackId(), pack);
                if (pack.isBehaviourPack()) {
                    this.behaviorPacksById.put(pack.getPackId(), pack);
                    this.behaviorPacks.add(pack);
                } else {
                    this.resourcePacksById.put(pack.getPackId(), pack);
                    this.resourcePacks.add(pack);
                }
            });

        });

        this.applyPackConfig();

        log.info(Server.getInstance().getLanguage().translateString("nukkit.resources.success", String.valueOf(this.resourcePacks.size())));
    }

    private static void closePackQuietly(ResourcePack pack) {
        if (pack instanceof AutoCloseable closeable) {
            try {
                closeable.close();
            } catch (Exception e) {
                log.debug("Failed to close resource pack {}", pack.getPackId(), e);
            }
        }
    }

    /**
     * 读取并应用 packs.yml 配置（如 CDN URL）
     * <p>
     * Load and apply packs.yml configuration (e.g. CDN URLs)
     */
    private void applyPackConfig() {
        if (this.packConfigFile == null) {
            return;
        }
        if (!this.packConfigFile.exists()) {
            File parent = this.packConfigFile.getParentFile();
            if (parent != null) {
                parent.mkdirs();
            }
            try (InputStream in = Server.class.getClassLoader().getResourceAsStream("packs.yml")) {
                if (in != null) {
                    Utils.writeFile(this.packConfigFile, in);
                }
            } catch (IOException e) {
                log.warn("Failed to create default packs.yml", e);
                return;
            }
        }
        Config config;
        try {
            config = new Config(this.packConfigFile, Config.YAML);
        } catch (RuntimeException e) {
            log.warn("Failed to load packs.yml; pack-specific configuration was ignored", e);
            return;
        }
        if (!hasValidPackConfigStructure(config)) {
            log.warn("Invalid packs.yml structure: every top-level entry must be a pack section");
            return;
        }
        for (String packId : config.getSections("").keySet()) {
            ResourcePack pack;
            try {
                pack = this.getPackById(UUID.fromString(packId));
            } catch (IllegalArgumentException e) {
                log.warn("Invalid UUID in packs.yml: {}", packId);
                continue;
            }
            if (pack == null) {
                log.warn("Pack in packs.yml not found on disk: {}", packId);
                continue;
            }
            String cdn = config.getString(packId + ".cdn");
            if (!cdn.isEmpty()) {
                pack.setCDNUrl(cdn);
            }
            String keyPath = packId + ".key";
            if (config.exists(keyPath)) {
                pack.setEncryptionKey(config.getString(keyPath));
            }
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

    protected static class ProtocolConverter {
        private static final Map<String, Integer> PROTOCOL_MAP = new HashMap<>();

        static {
            Field[] fields = ProtocolInfo.class.getDeclaredFields();

            for (Field field : fields) {
                String fieldName = field.getName();
                if (fieldName.startsWith("v") && SUPPORTED_PROTOCOLS.contains(getFieldValue(field))) {
                    try {
                        String versionKey = fieldName.substring(1).replace("_", ".");
                        PROTOCOL_MAP.put(versionKey, (Integer) field.get(null));
                    } catch (IllegalAccessException e) {
                        log.error("Error accessing field {}: {}", fieldName, e.getMessage());
                    }
                }
            }
        }

        private static int getFieldValue(Field field) {
            try {
                return field.getInt(null);
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to get field value: " + field.getName(), e);
            }
        }

        public static int convertToProtocol(JsonArray minEngineVersion) {
            if (minEngineVersion == null || minEngineVersion.size() < 3) {
                throw new IllegalArgumentException("Invalid minEngineVersion array");
            }

            int major = minEngineVersion.get(0).getAsInt();
            int minor = minEngineVersion.get(1).getAsInt();
            int patch = minEngineVersion.get(2).getAsInt();

            String baseKey = major + "." + minor + "." + patch;

            if (minEngineVersion.size() >= 4) {
                int extra = minEngineVersion.get(3).getAsInt();
                String fullKey = baseKey + "." + extra;
                if (PROTOCOL_MAP.containsKey(fullKey)) {
                    return PROTOCOL_MAP.get(fullKey);
                }
            }

            if (PROTOCOL_MAP.containsKey(baseKey)) {
                return PROTOCOL_MAP.get(baseKey);
            }

            return findClosestProtocol(major, minor, patch);
        }

        private static int findClosestProtocol(int major, int minor, int patch) {
            for (int p = patch; p >= 0; p--) {
                String key = major + "." + minor + "." + p;
                if (PROTOCOL_MAP.containsKey(key)) {
                    return PROTOCOL_MAP.get(key);
                }
            }

            String minorKey = major + "." + minor + ".0";
            if (PROTOCOL_MAP.containsKey(minorKey)) {
                return PROTOCOL_MAP.get(minorKey);
            }

            return SUPPORTED_PROTOCOLS.get(0);
        }
    }
}
