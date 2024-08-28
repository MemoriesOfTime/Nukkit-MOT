package cn.nukkit.utils;

import cn.nukkit.Nukkit;
import cn.nukkit.Server;
import cn.nukkit.plugin.Plugin;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
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
                }
            } catch (Exception e) {
                download = false;
                log.warn("Failed to check spark update: {}", e.getMessage(), e);
            }
        } else {
            download = true;
        }

        if (download) {
            log.info("Downloading spark...");
            log.info("If the download fails, please download it manually from https://sparkapi.lucko.me/download/nukkit");
            log.info("Or set enable-spark=off in server.properties to disable automatic download.");
            try (InputStream in = new URL("https://sparkapi.lucko.me/download/nukkit").openStream()) {
                try (OutputStream out = Files.newOutputStream(sparkFile.toPath())) {
                    byte[] buff = new byte[1024 * 10];
                    int len;
                    while ((len = in.read(buff)) != -1) {
                        out.write(buff, 0, len);
                        out.flush();
                    }
                }
                //Files.copy(in, sparkFile.toPath());
                server.getPluginManager().loadPlugin(sparkFile);
                log.info("Spark has been installed.");
            } catch (Throwable e) {
                log.warn("Failed to download spark: {}", e.getMessage(), e);
            }
        }

        return download;
    }

    private static String getFileSha1(File file) throws Exception {
        return String.format("%040x", new BigInteger(1,
                MessageDigest.getInstance("SHA-1").digest(Files.readAllBytes(file.toPath()))));
    }
}