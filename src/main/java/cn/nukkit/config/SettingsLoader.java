package cn.nukkit.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

public class SettingsLoader {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    private final Path jsonPath;
    private final Path propertiesPath;
    private final SettingsMigrator migrator;

    public SettingsLoader(Path dataDir) {
        this.jsonPath = dataDir.resolve("settings.json");
        this.propertiesPath = dataDir.resolve("server.properties");
        this.migrator = new SettingsMigrator();
    }

    public Settings load() {
        if (Files.exists(jsonPath)) {
            try {
                return MAPPER.readValue(jsonPath.toFile(), Settings.class);
            } catch (IOException e) {
                throw new RuntimeException("Failed to read settings.json", e);
            }
        }

        Settings settings;
        if (Files.exists(propertiesPath)) {
            settings = migrator.migrateFromProperties(propertiesPath);

            migrator.cleanupOldProperties(propertiesPath);
        } else {
            settings = Settings.defaults();
        }

        save(settings);
        return settings;
    }

    public void setProperty(Settings current, String key, Object value) {
        try {
            ObjectNode root = MAPPER.valueToTree(current);

            String[] path = findJsonPath(key);
            if (path == null) {
                return;
            }

            ObjectNode node = root;
            for (int i = 0; i < path.length - 1; i++) {
                node = (ObjectNode) node.get(path[i]);
            }
            String finalKey = path[path.length - 1];
            if (value instanceof String s) {
                node.put(finalKey, s);
            } else if (value instanceof Integer i) {
                node.put(finalKey, i);
            } else if (value instanceof Boolean b) {
                node.put(finalKey, b);
            } else {
                return;
            }

            Settings updated = MAPPER.treeToValue(root, Settings.class);
            save(updated);

        } catch (Exception e) {
            throw new RuntimeException("Failed to set property: " + key, e);
        }
    }

    private static final Map<String, String[]> KEY_PATHS = Map.<String, String[]>ofEntries(
            Map.entry("motd", new String[]{"server", "motd"}),
            Map.entry("sub-motd", new String[]{"server", "subMotd"}),
            Map.entry("language", new String[]{"server", "language"}),
            Map.entry("force-language", new String[]{"server", "forceLanguage"}),
            Map.entry("shutdown-message", new String[]{"server", "shutdownMessage"}),
            Map.entry("ansi-title", new String[]{"server", "ansiTitle"}),
            Map.entry("update-notifications", new String[]{"server", "updateNotifications"}),
            Map.entry("server-ip", new String[]{"network", "ip"}),
            Map.entry("server-port", new String[]{"network", "port"}),
            Map.entry("enable-query", new String[]{"network", "enableQuery"}),
            Map.entry("query-plugins", new String[]{"network", "queryPlugins"}),
            Map.entry("enable-rcon", new String[]{"network", "enableRcon"}),
            Map.entry("rcon.password", new String[]{"network", "rconPassword"}),
            Map.entry("rcon.port", new String[]{"network", "rconPort"}),
            Map.entry("multiversion-min-protocol", new String[]{"network", "minProtocol"}),
            Map.entry("multiversion-max-protocol", new String[]{"network", "maxProtocol"}),
            Map.entry("use-waterdog", new String[]{"network", "useWaterdog"}),
            Map.entry("timeout-milliseconds", new String[]{"network", "timeoutMilliseconds"}),
            Map.entry("rak-packet-limit", new String[]{"network", "rakPacketLimit"}),
            Map.entry("enable-rak-send-cookie", new String[]{"network", "enableRakSendCookie"}),
            Map.entry("compression-threshold", new String[]{"network", "compressionThreshold"}),
            Map.entry("use-snappy-compression", new String[]{"network", "useSnappyCompression"}),
            Map.entry("compression-level", new String[]{"network", "compressionLevel"}),
            Map.entry("async-workers", new String[]{"network", "asyncWorkers"}),
            Map.entry("zlib-provider", new String[]{"network", "zlibProvider"}),
            Map.entry("level-name", new String[]{"world", "levelName"}),
            Map.entry("level-seed", new String[]{"world", "levelSeed"}),
            Map.entry("level-type", new String[]{"world", "levelType"}),
            Map.entry("generator-settings", new String[]{"world", "generatorSettings"}),
            Map.entry("nether", new String[]{"world", "nether"}),
            Map.entry("end", new String[]{"world", "end"}),
            Map.entry("vanilla-portals", new String[]{"world", "vanillaPortals"}),
            Map.entry("portal-ticks", new String[]{"world", "portalTicks"}),
            Map.entry("multi-nether-worlds", new String[]{"world", "multiNetherWorlds"}),
            Map.entry("anti-xray-worlds", new String[]{"world", "antiXrayWorlds"}),
            Map.entry("do-not-tick-worlds", new String[]{"world", "noTickingWorlds"}),
            Map.entry("worlds-entity-spawning-disabled", new String[]{"world", "disabledSpawnWorlds"}),
            Map.entry("worlds-level-auto-save-disabled", new String[]{"world", "nonAutoSaveWorlds"}),
            Map.entry("load-all-worlds", new String[]{"world", "loadAllWorlds"}),
            Map.entry("level-auto-compaction", new String[]{"world", "levelAutoCompaction"}),
            Map.entry("level-auto-compaction-ticks", new String[]{"world", "levelAutoCompactionTicks"}),
            Map.entry("async-chunks", new String[]{"world", "asyncChunks"}),
            Map.entry("cache-chunks", new String[]{"world", "cacheChunks"}),
            Map.entry("clear-chunk-tick-list", new String[]{"world", "clearChunkTickList"}),
            Map.entry("max-players", new String[]{"players", "maxPlayers"}),
            Map.entry("white-list", new String[]{"players", "whitelist"}),
            Map.entry("whitelist-reason", new String[]{"players", "whitelistReason"}),
            Map.entry("achievements", new String[]{"players", "achievements"}),
            Map.entry("announce-player-achievements", new String[]{"players", "announceAchievements"}),
            Map.entry("save-player-data", new String[]{"players", "savePlayerData"}),
            Map.entry("save-player-data-by-uuid", new String[]{"players", "savePlayerDataByUuid"}),
            Map.entry("skin-change-cooldown", new String[]{"players", "skinChangeCooldown"}),
            Map.entry("space-name-mode", new String[]{"players", "spaceNameMode"}),
            Map.entry("allow-flight", new String[]{"players", "allowFlight"}),
            Map.entry("spawn-eggs", new String[]{"players", "spawnEggs"}),
            Map.entry("xp-bottles-on-creative", new String[]{"players", "xpBottlesOnCreative"}),
            Map.entry("persona-skins", new String[]{"players", "personaSkins"}),
            Map.entry("do-not-limit-skin-geometry", new String[]{"players", "doNotLimitSkinGeometry"}),
            Map.entry("do-not-limit-interactions", new String[]{"players", "doNotLimitInteractions"}),
            Map.entry("gamemode", new String[]{"features", "gamemode"}),
            Map.entry("force-gamemode", new String[]{"features", "forceGamemode"}),
            Map.entry("difficulty", new String[]{"features", "difficulty"}),
            Map.entry("hardcore", new String[]{"features", "hardcore"}),
            Map.entry("pvp", new String[]{"features", "pvp"}),
            Map.entry("spawn-protection", new String[]{"features", "spawnProtection"}),
            Map.entry("mob-ai", new String[]{"features", "mobAi"}),
            Map.entry("spawn-animals", new String[]{"features", "spawnAnimals"}),
            Map.entry("spawn-mobs", new String[]{"features", "spawnMobs"}),
            Map.entry("explosion-break-blocks", new String[]{"features", "explosionBreakBlocks"}),
            Map.entry("bed-spawnpoints", new String[]{"features", "bedSpawnpoints"}),
            Map.entry("anvils-enabled", new String[]{"features", "anvilsEnabled"}),
            Map.entry("drop-spawners", new String[]{"features", "dropSpawners"}),
            Map.entry("block-listener", new String[]{"features", "blockListener"}),
            Map.entry("enable-raw-ores", new String[]{"features", "enableRawOres"}),
            Map.entry("enable-new-paintings", new String[]{"features", "enableNewPaintings"}),
            Map.entry("enable-new-chicken-eggs-laying", new String[]{"features", "enableNewChickenEggsLaying"}),
            Map.entry("forced-safety-enchant", new String[]{"features", "forcedSafetyEnchant"}),
            Map.entry("stop-in-game", new String[]{"features", "stopInGame"}),
            Map.entry("op-in-game", new String[]{"features", "opInGame"}),
            Map.entry("view-distance", new String[]{"performance", "viewDistance"}),
            Map.entry("chunk-sending-per-tick", new String[]{"performance", "chunkSendingPerTick"}),
            Map.entry("chunk-ticking-per-tick", new String[]{"performance", "chunkTickingPerTick"}),
            Map.entry("chunk-ticking-radius", new String[]{"performance", "chunkTickingRadius"}),
            Map.entry("chunk-generation-queue-size", new String[]{"performance", "chunkGenerationQueueSize"}),
            Map.entry("chunk-generation-population-queue-size", new String[]{"performance", "chunkGenerationPopulationQueueSize"}),
            Map.entry("spawn-threshold", new String[]{"performance", "spawnThreshold"}),
            Map.entry("ticks-per-autosave", new String[]{"performance", "ticksPerAutosave"}),
            Map.entry("ticks-per-entity-follow", new String[]{"performance", "ticksPerEntityFollow"}),
            Map.entry("ticks-per-entity-spawns", new String[]{"performance", "ticksPerEntitySpawns"}),
            Map.entry("ticks-per-entity-despawns", new String[]{"performance", "ticksPerEntityDespawns"}),
            Map.entry("auto-save", new String[]{"performance", "autoSave"}),
            Map.entry("auto-tick-rate", new String[]{"performance", "autoTickRate"}),
            Map.entry("auto-tick-rate-limit", new String[]{"performance", "autoTickRateLimit"}),
            Map.entry("base-tick-rate", new String[]{"performance", "baseTickRate"}),
            Map.entry("always-tick-players", new String[]{"performance", "alwaysTickPlayers"}),
            Map.entry("light-updates", new String[]{"performance", "lightUpdates"}),
            Map.entry("entity-auto-spawn-task", new String[]{"performance", "entityAutoSpawnTask"}),
            Map.entry("entity-despawn-task", new String[]{"performance", "entityDespawnTask"}),
            Map.entry("do-level-gc", new String[]{"performance", "doLevelGC"}),
            Map.entry("xbox-auth", new String[]{"security", "xboxAuth"}),
            Map.entry("encryption", new String[]{"security", "encryption"}),
            Map.entry("temp-ip-ban-failed-xbox-auth", new String[]{"security", "tempIpBanFailedXboxAuth"}),
            Map.entry("strong-ip-bans", new String[]{"security", "strongIpBans"}),
            Map.entry("check-op-movement", new String[]{"security", "checkOpMovement"}),
            Map.entry("debug-level", new String[]{"debug", "debugLevel"}),
            Map.entry("deprecated-verbose", new String[]{"debug", "deprecatedVerbose"}),
            Map.entry("automatic-bug-report", new String[]{"debug", "automaticBugReport"}),
            Map.entry("bstats-metrics", new String[]{"debug", "bstatsMetrics"}),
            Map.entry("enable-spark", new String[]{"integration", "enableSpark"}),
            Map.entry("hastebin-token", new String[]{"integration", "hastebinToken"}),
            Map.entry("netease-client-support", new String[]{"integration", "neteaseClientSupport"}),
            Map.entry("only-allow-netease-client", new String[]{"integration", "onlyAllowNeteaseClient"}),
            Map.entry("enable-experiment-mode", new String[]{"experimental", "enableExperimentMode"}),
            Map.entry("server-authoritative-movement", new String[]{"experimental", "serverAuthoritativeMovement"}),
            Map.entry("server-authoritative-block-breaking", new String[]{"experimental", "serverAuthoritativeBlockBreaking"}),
            Map.entry("use-client-spectator", new String[]{"experimental", "useClientSpectator"}),
            Map.entry("enable-vibrant-visuals", new String[]{"experimental", "enableVibrantVisuals"}),
            Map.entry("enable-raytracing", new String[]{"experimental", "enableRaytracing"})
    );

    static String[] findJsonPath(String key) {
        return KEY_PATHS.get(key);
    }

    public void save(Settings settings) {
        try {
            MAPPER.writerWithDefaultPrettyPrinter().writeValue(jsonPath.toFile(), settings);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write settings.json", e);
        }
    }
}