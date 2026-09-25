package cn.nukkit.resourcepacks.loader;

import cn.nukkit.resourcepacks.ResourcePack;

import java.io.File;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * 描述一个资源包加载器
 */
public interface ResourcePackLoader {
    /**
     * 加载资源包并返回结果
     * @return 加载的资源包
     */
    List<ResourcePack> loadPacks();

    /**
     * 按文件名排序列出目录内容，使资源包顺序不依赖文件系统。
     * <p>
     * Lists a directory by file name. {@link File#listFiles()} returns directory order, which
     * differs between filesystems and can change after a redeploy, so packs loaded straight from
     * it could swap their place in the client stack between two starts of the same server.
     *
     * @param directory the directory to list
     * @return its entries sorted by name, or an empty array when it cannot be listed
     */
    static File[] listFilesInNameOrder(File directory) {
        File[] files = directory.listFiles();
        if (files == null) {
            return new File[0];
        }
        Arrays.sort(files, Comparator.comparing(File::getName));
        return files;
    }
}
