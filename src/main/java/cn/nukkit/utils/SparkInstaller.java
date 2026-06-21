package cn.nukkit.utils;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.nio.file.Files;
import java.util.Locale;

@Log4j2
public class SparkInstaller {

    public static boolean initSpark(@NotNull Server server) {
        boolean download = false;

        Plugin spark = server.getPluginManager().getPlugin("spark");

        File sparkFile;
        if (spark != null) {
            sparkFile = spark.getFile();
        } else {
            sparkFile = new File(server.getPluginPath() + "/spark.jar");
        }

        JsonObject artifact = getLatestInfo();
        if (artifact == null) {
            log.error("Failed to get spark update info! skip spark update.");
            return false;
        }
        String fileName = artifact.get("fileName").getAsString();
        String relativePath = artifact.get("relativePath").getAsString();
        String downloadUrl = "https://ci.lucko.me/job/spark-extra-platforms/lastSuccessfulBuild/artifact/" + relativePath;

        if (sparkFile.exists()) {
            try {
                if (!fileName.equalsIgnoreCase(sparkFile.getName())) {
                    download = true;
                    try {
                        if (spark != null) {
                            PluginManager pluginManager = server.getPluginManager();
                            pluginManager.disablePlugin(spark);
                            pluginManager.getPlugins().remove(spark.getName());
                            spark.getPluginLoader().unloadPlugin(spark);
                        }
                        System.gc();
                        Thread.sleep(100);
                        Files.delete(sparkFile.toPath());
                    } catch (Exception e) {
                        //download = false;
                        if (Nukkit.DEBUG > 1) {
                            log.warn("Failed to delete spark: {}", e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                download = false;
                log.error("Failed to check spark update: {}", e.getMessage(), e);
            }
        } else {
            download = true;
        }

        if (download) {
            log.info("Downloading spark...");
            log.info("If the download fails, please download it manually from https://ci.lucko.me/job/spark-extra-platforms/");
            // Key mirrors PerformanceSettings (@CustomKey "enable-spark" in nukkit-mot.yml).
            log.info("Or set \"enable-spark: false\" under performance-settings in nukkit-mot.yml to disable automatic download.");
            File newFile = new File(server.getPluginPath() + "/" + fileName);
            try {
                HttpUtils.downloadFile(downloadUrl, newFile.toPath());
                server.getPluginManager().loadPlugin(newFile);
                log.info("Spark has been installed.");
            } catch (Throwable e) {
                log.warn("Failed to download spark: {}", e.getMessage(), e);
            }
        }

        return download;
    }

    private static JsonObject getLatestInfo() {
        try {
            String apiUrl = "https://ci.lucko.me/job/spark-extra-platforms/lastBuild/api/json?tree=number%2Cartifacts%5BfileName%2CrelativePath%5D%2Cresult%2Cdescription%2Ctimestamp";
            String json = HttpUtils.fetchString(apiUrl);
            JsonObject object = JsonParser.parseString(json).getAsJsonObject();
            JsonArray artifacts = object.getAsJsonArray("artifacts");
            for (int i = 0; i < artifacts.size(); i++) {
                JsonObject artifact = artifacts.get(i).getAsJsonObject();
                String fileName = artifact.get("fileName").getAsString();
                if (fileName.toLowerCase(Locale.ROOT).contains("nukkit")) {
                    return artifact;
                }
            }
        } catch (Exception e) {
            log.error("Failed to get spark update info: {}", e.getMessage(), e);
        }
        return null;
    }
}
