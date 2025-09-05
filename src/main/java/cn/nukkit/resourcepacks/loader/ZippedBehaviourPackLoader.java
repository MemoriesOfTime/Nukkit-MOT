package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Server;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.resourcepacks.ResourcePack;
import cn.nukkit.resourcepacks.ZippedBehaviourPack;
import com.google.common.io.Files;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class ZippedBehaviourPackLoader extends ZippedResourcePackLoader {

    public ZippedBehaviourPackLoader(File path) {
        super(path);
    }

    public ZippedBehaviourPackLoader(File path, boolean isNetEase) {
        super(path, isNetEase);
    }

    @Override
    public List<ResourcePack> loadPacks() {
        BaseLang baseLang = Server.getInstance().getLanguage();
        List<ResourcePack> loadedResourcePacks = new ArrayList<>();
        for (File pack : this.path.listFiles()) {
            try {
                ZippedBehaviourPack resourcePack = null;
                String fileExt = Files.getFileExtension(pack.getName());
                if (pack.isDirectory()) {
                    File file = loadDirectoryPack(pack);
                    if (file != null)
                        resourcePack = new ZippedBehaviourPack(file);
                } else if (!fileExt.equals("key")) {
                    switch (fileExt) {
                        case "zip":
                        case "mcpack":
                            resourcePack = new ZippedBehaviourPack(pack);
                            break;
                        default:
                            log.warn(baseLang.translateString("nukkit.resources.unknown-format", new String[] { pack.getName() }));
                            break;
                    }
                }
                if (resourcePack != null && resourcePack.isBehaviourPack()) {
                    resourcePack.setNetEase(this.isNetEase);
                    loadedResourcePacks.add(resourcePack);
                    log.info(baseLang.translateString("nukkit.resources.zip.loaded", pack.getName()));
                }
            } catch (IllegalArgumentException e) {
                log.warn(baseLang.translateString("nukkit.resources.fail", pack.getName(), e.getMessage()), e);
            }
        }
        return loadedResourcePacks;
    }
}

