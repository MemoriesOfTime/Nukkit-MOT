package cn.nukkit.utils;

import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.net.URL;
import java.nio.file.Files;
import java.security.MessageDigest;

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

        if (sparkFile.exists()) {
            try {
                String sha1 = getFileSha1(sparkFile);
                URL url = new URL("https://sparkapi.lucko.me/download/nukkit/sha1");
                try (InputStream in = url.openStream()) {
                    byte[] sha1Remote = new byte[40];
                    in.read(sha1Remote);
                    if (!sha1.equals(new String(sha1Remote))) {
                        download = true;
                        try {
                            if (spark != null) {
                                server.getPluginManager().disablePlugin(spark);
                                System.gc();
                            }
                            Files.delete(sparkFile.toPath());
                        } catch (IOException e) {
                            download = false;
                            log.warn("Failed to delete spark: " + e.getMessage(), e);
                        }
                    }
                }
            } catch (Exception e) {
                download = false;
                log.warn("Failed to check spark update: " + e.getMessage(), e);
            }
        } else {
            download = true;
        }

        if (download) {
            try (InputStream in = new URL("https://sparkapi.lucko.me/download/nukkit").openStream()) {
                Files.copy(in, sparkFile.toPath());
                server.getPluginManager().loadPlugin(sparkFile);
                log.info("Spark has been installed.");
            } catch (IOException e) {
                log.warn("Failed to download spark: " + e.getMessage(), e);
            }
        }

        return download;
    }

    private static String getFileSha1(File file) throws Exception {
        return String.format("%040x", new BigInteger(1,
                MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file.toPath()))));
    }
}