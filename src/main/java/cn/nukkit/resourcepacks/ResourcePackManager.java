package cn.nukkit.resourcepacks;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import com.google.common.collect.Sets;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.*;

@Log4j2
public class ResourcePackManager {

    private final Map<UUID, ResourcePack> resourcePacksById = new HashMap<>();
    private final Set<ResourcePack> resourcePacks = new HashSet<>();
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
            this.resourcePacks.addAll(loadedPacks);
        });

        log.info(Server.getInstance().getLanguage().translateString("nukkit.resources.success", String.valueOf(this.resourcePacks.size())));
    }
}
