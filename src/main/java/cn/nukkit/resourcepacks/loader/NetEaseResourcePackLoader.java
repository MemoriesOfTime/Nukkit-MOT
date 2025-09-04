package cn.nukkit.resourcepacks.loader;

import cn.nukkit.Server;
import cn.nukkit.resourcepacks.NetEaseResourcePack;
import cn.nukkit.resourcepacks.ResourcePack;
import com.google.common.io.Files;
import lombok.extern.log4j.Log4j2;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Log4j2
public class NetEaseResourcePackLoader implements ResourcePackLoader {

    // 网易资源包文件存放地址 - 默认为服务器根目录下的netease_packs文件夹
    protected final File path;

    public NetEaseResourcePackLoader(File path) {
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
        
        File[] files = path.listFiles();
        if (files == null) {
            return loadedResourcePacks;
        }
        
        for (File pack : files) {
            try {
                ResourcePack resourcePack = null;
                String fileExt = Files.getFileExtension(pack.getName());
                
                if (pack.isDirectory()) {
                    // 网易格式：直接处理文件夹
                    resourcePack = new NetEaseResourcePack(pack);
                } else if (!fileExt.equals("key")) {
                    // 支持压缩包格式
                    switch (fileExt) {
                        case "zip", "mcpack" -> resourcePack = new NetEaseResourcePack(pack);
                        default -> log.warn(baseLang.translateString("nukkit.resources.unknown-format", pack.getName()));
                    }
                }
                
                if (resourcePack != null) {
                    loadedResourcePacks.add(resourcePack);
                    log.info("NetEase resource pack loaded: " + pack.getName());
                }
            } catch (IllegalArgumentException e) {
                log.warn(baseLang.translateString("nukkit.resources.fail", pack.getName(), e.getMessage()), e);
            }
        }
        return loadedResourcePacks;
    }
}