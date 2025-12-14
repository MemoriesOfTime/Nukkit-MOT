package cn.nukkit.config;

import cn.nukkit.utils.Config;
import cn.nukkit.utils.ConfigSection;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class SettingsMigrator {
    public Settings migrateFromProperties(Path propertiesPath) {
        Config cfg = new Config(propertiesPath.toString(), Config.PROPERTIES, new DefaultServerProperties());

        java.util.function.Function<String, Integer> parseInt = (value) -> {
            try {
                return Integer.parseInt(value);
            } catch (NumberFormatException e) {
                return 0;
            }
        };

        return new Settings(
                new Settings.Server(
                        cfg.getString("motd", "Minecraft Server"),
                        cfg.getString("sub-motd", "Powered by Nukkit-MOT"),
                        cfg.getString("language", "eng"),
                        cfg.getBoolean("force-language", false),
                        cfg.getString("shutdown-message", "§cServer closed"),
                        cfg.getBoolean("ansi-title", false),
                        cfg.getBoolean("update-notifications", true)
                ),
                new Settings.Network(
                        cfg.getString("server-ip", "0.0.0.0"),
                        parseInt.apply(cfg.getString("server-port", "19132")),
                        cfg.getBoolean("enable-query", true),
                        cfg.getBoolean("query-plugins", false),
                        cfg.getBoolean("enable-rcon", false),
                        cfg.getString("rcon.password", ""),
                        parseInt.apply(cfg.getString("rcon.port", "25575")),
                        parseInt.apply(cfg.getString("multiversion-min-protocol", "0")),
                        parseInt.apply(cfg.getString("multiversion-max-protocol", "-1")),
                        cfg.getBoolean("use-waterdog", false),
                        parseInt.apply(cfg.getString("timeout-milliseconds", "25000")),
                        parseInt.apply(cfg.getString("rak-packet-limit", "60")),
                        cfg.getBoolean("enable-rak-send-cookie", true),
                        parseInt.apply(cfg.getString("compression-threshold", "256")),
                        cfg.getBoolean("use-snappy-compression", false),
                        parseInt.apply(cfg.getString("compression-level", "5")),
                        cfg.get("async-workers", "auto"),
                        parseInt.apply(cfg.getString("zlib-provider", "2"))
                ),
                new Settings.World(
                        cfg.getString("level-name", "world"),
                        cfg.getString("level-seed", ""),
                        cfg.getString("level-type", "default"),
                        cfg.getString("generator-settings", ""),
                        cfg.getBoolean("nether", true),
                        cfg.getBoolean("end", true),
                        cfg.getBoolean("vanilla-portals", true),
                        parseInt.apply(cfg.getString("portal-ticks", "80")),
                        cfg.getString("multi-nether-worlds", ""),
                        cfg.getString("anti-xray-worlds", ""),
                        cfg.getString("do-not-tick-worlds", ""),
                        cfg.getString("worlds-entity-spawning-disabled", ""),
                        cfg.getString("worlds-level-auto-save-disabled", ""),
                        cfg.getBoolean("load-all-worlds", true),
                        cfg.getBoolean("level-auto-compaction", true),
                        parseInt.apply(cfg.getString("level-auto-compaction-ticks", "36000")),
                        cfg.getBoolean("async-chunks", true),
                        cfg.getBoolean("cache-chunks", false),
                        cfg.getBoolean("clear-chunk-tick-list", true)
                ),
                new Settings.Players(
                        parseInt.apply(cfg.getString("max-players", "50")),
                        cfg.getBoolean("white-list", false),
                        cfg.getString("whitelist-reason", "§cServer is white-listed"),
                        cfg.getBoolean("achievements", true),
                        cfg.getBoolean("announce-player-achievements", true),
                        cfg.getBoolean("save-player-data", true),
                        cfg.getBoolean("save-player-data-by-uuid", true),
                        parseInt.apply(cfg.getString("skin-change-cooldown", "15")),
                        cfg.getString("space-name-mode", "ignore"),
                        cfg.getBoolean("allow-flight", false),
                        cfg.getBoolean("spawn-eggs", true),
                        cfg.getBoolean("xp-bottles-on-creative", true),
                        cfg.getBoolean("persona-skins", true),
                        cfg.getBoolean("do-not-limit-skin-geometry", true),
                        cfg.getBoolean("do-not-limit-interactions", false)
                ),
                new Settings.Features(
                        parseGamemode(cfg.get("gamemode", "0")),
                        cfg.getBoolean("force-gamemode", true),
                        cfg.getString("difficulty", "2"),
                        cfg.getBoolean("hardcore", false),
                        cfg.getBoolean("pvp", true),
                        parseInt.apply(cfg.getString("spawn-protection", "10")),
                        cfg.getBoolean("mob-ai", true),
                        cfg.getBoolean("spawn-animals", true),
                        cfg.getBoolean("spawn-mobs", true),
                        cfg.getBoolean("explosion-break-blocks", true),
                        cfg.getBoolean("bed-spawnpoints", true),
                        cfg.getBoolean("anvils-enabled", true),
                        cfg.getBoolean("drop-spawners", true),
                        cfg.getBoolean("block-listener", true),
                        cfg.getBoolean("enable-raw-ores", true),
                        cfg.getBoolean("enable-new-paintings", true),
                        cfg.getBoolean("enable-new-chicken-eggs-laying", true),
                        cfg.getBoolean("forced-safety-enchant", true),
                        cfg.getBoolean("stop-in-game", false),
                        cfg.getBoolean("op-in-game", true)
                ),
                new Settings.Performance(
                        parseInt.apply(cfg.getString("view-distance", "8")),
                        parseInt.apply(cfg.getString("chunk-sending-per-tick", "4")),
                        parseInt.apply(cfg.getString("chunk-ticking-per-tick", "40")),
                        parseInt.apply(cfg.getString("chunk-ticking-radius", "3")),
                        parseInt.apply(cfg.getString("chunk-generation-queue-size", "8")),
                        parseInt.apply(cfg.getString("chunk-generation-population-queue-size", "8")),
                        parseInt.apply(cfg.getString("spawn-threshold", "56")),
                        parseInt.apply(cfg.getString("ticks-per-autosave", "6000")),
                        parseInt.apply(cfg.getString("ticks-per-entity-follow", "5")),
                        parseInt.apply(cfg.getString("ticks-per-entity-spawns", "200")),
                        parseInt.apply(cfg.getString("ticks-per-entity-despawns", "12000")),
                        cfg.getBoolean("auto-save", true),
                        cfg.getBoolean("auto-tick-rate", true),
                        parseInt.apply(cfg.getString("auto-tick-rate-limit", "20")),
                        parseInt.apply(cfg.getString("base-tick-rate", "1")),
                        cfg.getBoolean("always-tick-players", false),
                        cfg.getBoolean("light-updates", false),
                        cfg.getBoolean("entity-auto-spawn-task", true),
                        cfg.getBoolean("entity-despawn-task", true),
                        cfg.getBoolean("do-level-gc", true)
                ),
                new Settings.Security(
                        cfg.getBoolean("xbox-auth", true),
                        cfg.getBoolean("encryption", true),
                        cfg.getBoolean("temp-ip-ban-failed-xbox-auth", false),
                        cfg.getBoolean("strong-ip-bans", false),
                        cfg.getBoolean("check-op-movement", false)
                ),
                new Settings.Debug(
                        parseInt.apply(cfg.getString("debug-level", "1")),
                        cfg.getBoolean("deprecated-verbose", true),
                        cfg.getBoolean("automatic-bug-report", true),
                        cfg.getBoolean("bstats-metrics", true)
                ),
                new Settings.Integration(
                        cfg.getBoolean("enable-spark", false),
                        cfg.getString("hastebin-token", ""),
                        cfg.getBoolean("netease-client-support", false),
                        cfg.getBoolean("only-allow-netease-client", false)
                ),
                new Settings.Experimental(
                        cfg.getBoolean("enable-experiment-mode", true),
                        cfg.getString("server-authoritative-movement", "server-auth"),
                        cfg.getBoolean("server-authoritative-block-breaking", true),
                        cfg.getBoolean("use-client-spectator", true),
                        cfg.getBoolean("enable-vibrant-visuals", true),
                        cfg.getBoolean("enable-raytracing", true)
                )
        );
    }

    private int parseGamemode(Object value) {
        if (value instanceof Integer i) return i;
        String s = value.toString().trim().toLowerCase();
        return switch (s) {
            case "creative", "1" -> 1;
            case "adventure", "2" -> 2;
            case "spectator", "3" -> 3;
            default -> 0;
        };
    }

    public void cleanupOldProperties(Path propertiesPath) {
        try {
            String infoMessage = "# Config of the server now in settings.json\n" +
                    "# This file was migrated to the new configuration format\n" +
                    "# All settings are now stored in settings.json\n" +
                    "# You can safely delete this file";

            Files.write(propertiesPath, infoMessage.getBytes());
        } catch (IOException e) {
            try {
                Files.deleteIfExists(propertiesPath);
            } catch (IOException ignored) {}
        }
    }

    private static class DefaultServerProperties extends ConfigSection {
        {
            put("motd", "Minecraft Server");
            put("sub-motd", "Powered by Nukkit-MOT");
            put("server-port", 19132);
            put("server-ip", "0.0.0.0");
            put("view-distance", 8);
            put("achievements", true);
            put("announce-player-achievements", true);
            put("spawn-protection", 10);
            put("max-players", 50);
            put("drop-spawners", true);
            put("spawn-animals", true);
            put("spawn-mobs", true);
            put("gamemode", 0);
            put("force-gamemode", true);
            put("difficulty", "2");
            put("hardcore", false);
            put("pvp", true);
            put("white-list", false);
            put("whitelist-reason", "§cServer is white-listed");
            put("generator-settings", "");
            put("level-name", "world");
            put("level-seed", "");
            put("level-type", "default");
            put("enable-rcon", false);
            put("rcon.password", "");
            put("rcon.port", 25575);
            put("auto-save", true);
            put("level-auto-compaction", true);
            put("level-auto-compaction-ticks", 36000);
            put("force-resources", false);
            put("force-resources-allow-client-packs", false);
            put("xbox-auth", true);
            put("encryption", true);
            put("bed-spawnpoints", true);
            put("explosion-break-blocks", true);
            put("stop-in-game", false);
            put("op-in-game", true);
            put("space-name-mode", "ignore");
            put("xp-bottles-on-creative", true);
            put("spawn-eggs", true);
            put("mob-ai", true);
            put("entity-auto-spawn-task", true);
            put("entity-despawn-task", true);
            put("language", "eng");
            put("force-language", false);
            put("shutdown-message", "§cServer closed");
            put("save-player-data", true);
            put("enable-query", true);
            put("query-plugins", false);
            put("debug-level", 1);
            put("async-workers", "auto");
            put("zlib-provider", 2);
            put("compression-level", 5);
            put("compression-threshold", 256);
            put("use-snappy-compression", false);
            put("rak-packet-limit", 60);
            put("enable-rak-send-cookie", true);
            put("timeout-milliseconds", 25000);
            put("auto-tick-rate", true);
            put("auto-tick-rate-limit", 20);
            put("base-tick-rate", 1);
            put("always-tick-players", false);
            put("light-updates", false);
            put("clear-chunk-tick-list", true);
            put("spawn-threshold", 56);
            put("chunk-sending-per-tick", 4);
            put("chunk-ticking-per-tick", 40);
            put("chunk-ticking-radius", 3);
            put("chunk-generation-queue-size", 8);
            put("chunk-generation-population-queue-size", 8);
            put("ticks-per-autosave", 6000);
            put("ticks-per-entity-follow", 5);
            put("ticks-per-entity-spawns", 200);
            put("ticks-per-entity-despawns", 12000);
            put("thread-watchdog", true);
            put("thread-watchdog-tick", 60000);
            put("nether", true);
            put("end", true);
            put("vanilla-portals", true);
            put("portal-ticks", 80);
            put("multi-nether-worlds", "");
            put("anti-xray-worlds", "");
            put("do-not-tick-worlds", "");
            put("worlds-entity-spawning-disabled", "");
            put("worlds-level-auto-save-disabled", "");
            put("load-all-worlds", true);
            put("ansi-title", false);
            put("block-listener", true);
            put("allow-flight", false);
            put("multiversion-min-protocol", 0);
            put("multiversion-max-protocol", -1);
            put("vanilla-bossbars", false);
            put("strong-ip-bans", false);
            put("temp-ip-ban-failed-xbox-auth", false);
            put("call-data-pk-send-event", true);
            put("call-batch-pk-send-event", true);
            put("do-level-gc", true);
            put("skin-change-cooldown", 15);
            put("check-op-movement", false);
            put("do-not-limit-interactions", false);
            put("do-not-limit-skin-geometry", true);
            put("automatic-bug-report", true);
            put("anvils-enabled", true);
            put("save-player-data-by-uuid", true);
            put("persona-skins", true);
            put("call-entity-motion-event", true);
            put("update-notifications", true);
            put("bstats-metrics", true);
            put("cache-chunks", false);
            put("async-chunks", true);
            put("deprecated-verbose", true);
            put("server-authoritative-movement", "server-auth");
            put("server-authoritative-block-breaking", true);
            put("use-client-spectator", true);
            put("enable-experiment-mode", true);
            put("use-waterdog", false);
            put("enable-spark", false);
            put("hastebin-token", "");
            put("leveldb-cache-mb", 80);
            put("use-native-leveldb", false);
            put("enable-raw-ores", true);
            put("enable-new-paintings", true);
            put("enable-new-chicken-eggs-laying", true);
            put("forced-safety-enchant", true);
            put("enable-vibrant-visuals", true);
            put("enable-raytracing", true);
            put("netease-client-support", false);
            put("only-allow-netease-client", false);
        }
    }
}
