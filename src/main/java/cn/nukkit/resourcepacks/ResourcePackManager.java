package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import cn.nukkit.network.protocol.ProtocolInfo;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;

import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import com.google.common.collect.Sets;
import com.google.gson.JsonArray;

import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.lang.reflect.Field;
import java.util.*;

import static cn.nukkit.network.protocol.ProtocolInfo.SUPPORTED_PROTOCOLS;

@Log4j2
public class ResourcePackManager {

    private final Map<UUID, ResourcePack> resourcePacksById = new HashMap<>();
    private final Set<ResourcePack> resourcePacks = new HashSet<>();
    private final Set<ResourcePack> behaviorPacks = new HashSet<>();
    private final Set<ResourcePackLoader> loaders;

    public ResourcePackManager(ResourcePackLoader... loaders) {
        this(Sets.newHashSet(loaders));
    }

    public ResourcePackManager(Set<ResourcePackLoader> loaders) {
        this.loaders = loaders;
        reloadPacks();
    }

    public ResourcePackManager(File path) {
        this(new ZippedResourcePackLoader(path));
    }

    public ResourcePack[] getResourceStack() {
        return this.resourcePacks.toArray(ResourcePack.EMPTY_ARRAY);
    }

    public ResourcePack[] getBehaviorPacks() {
        return this.behaviorPacks.toArray(ResourcePack.EMPTY_ARRAY);
    }

    public ResourcePack getPackById(UUID id) {
        return this.resourcePacksById.get(id);
    }

    public void registerPackLoader(ResourcePackLoader loader) {
        this.loaders.add(loader);
    }

    public void reloadPacks() {
        this.resourcePacksById.clear();
        this.resourcePacks.clear();
        this.loaders.forEach(loader -> {
            var loadedPacks = loader.loadPacks();
            loadedPacks.forEach(pack -> resourcePacksById.put(pack.getPackId(), pack));

            loadedPacks.forEach(pack -> {
                if (pack instanceof ZippedResourcePack zippedResourcePack) {
                    if (zippedResourcePack.isBehaviourPack()) {
                        this.behaviorPacks.add(pack);
                    } else {
                        this.resourcePacks.add(pack);
                    }
                }
            });
        });

        log.info(Server.getInstance().getLanguage().translateString("nukkit.resources.success", String.valueOf(this.resourcePacks.size())));
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
                        System.err.println("Error accessing field " + fieldName + ": " + e.getMessage());
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
