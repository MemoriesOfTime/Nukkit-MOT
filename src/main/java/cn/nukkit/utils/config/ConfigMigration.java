package cn.nukkit.utils.config;

import cn.nukkit.utils.Config;
import lombok.extern.log4j.Log4j2;

import java.util.function.Consumer;

/**
 * Migrates old non-standard settings from server.properties to nukkit-mot.yml.
 * Runs on startup to support upgrading from older versions
 * that stored all settings in server.properties.
 */
@Log4j2
public class ConfigMigration {

    private final Config properties;
    private final ServerConfig serverConfig;

    public ConfigMigration(Config properties, ServerConfig serverConfig) {
        this.properties = properties;
        this.serverConfig = serverConfig;
    }

    /**
     * Perform the migration. Returns true if any settings were migrated.
     */
    public boolean migrate() {
        boolean migrated = false;

        // Performance settings
        migrated |= migrateString("async-workers", serverConfig.performanceSettings()::asyncWorkers);
        migrated |= migrateBoolean("auto-tick-rate", serverConfig.performanceSettings()::autoTickRate);
        migrated |= migrateInt("auto-tick-rate-limit", serverConfig.performanceSettings()::autoTickRateLimit);
        migrated |= migrateInt("base-tick-rate", serverConfig.performanceSettings()::baseTickRate);
        migrated |= migrateBoolean("always-tick-players", serverConfig.performanceSettings()::alwaysTickPlayers);
        migrated |= migrateBoolean("thread-watchdog", serverConfig.performanceSettings()::threadWatchdog);
        migrated |= migrateInt("thread-watchdog-tick", serverConfig.performanceSettings()::threadWatchdogTick);
        migrated |= migrateBoolean("do-level-gc", serverConfig.performanceSettings()::doLevelGc);
        migrated |= migrateBoolean("level-auto-compaction", serverConfig.performanceSettings()::levelAutoCompaction);
        migrated |= migrateInt("level-auto-compaction-ticks", serverConfig.performanceSettings()::levelAutoCompactionTicks);
        migrated |= migrateInt("leveldb-cache-mb", serverConfig.performanceSettings()::leveldbCacheMb);
        migrated |= migrateBoolean("use-native-leveldb", serverConfig.performanceSettings()::useNativeLeveldb);
        migrated |= migrateBoolean("enable-spark", serverConfig.performanceSettings()::enableSpark);
        migrated |= migrateInt("ticks-per-autosave", serverConfig.performanceSettings()::ticksPerAutosave);

        // Network settings
        migrated |= migrateInt("zlib-provider", serverConfig.networkSettings()::zlibProvider);
        migrated |= migrateInt("compression-level", serverConfig.networkSettings()::compressionLevel);
        migrated |= migrateInt("compression-threshold", serverConfig.networkSettings()::compressionThreshold);
        migrated |= migrateBoolean("use-snappy-compression", serverConfig.networkSettings()::useSnappyCompression);
        migrated |= migrateInt("rak-packet-limit", serverConfig.networkSettings()::rakPacketLimit);
        migrated |= migrateBoolean("enable-rak-send-cookie", serverConfig.networkSettings()::enableRakSendCookie);
        migrated |= migrateInt("timeout-milliseconds", serverConfig.networkSettings()::timeoutMilliseconds);
        migrated |= migrateBoolean("query-plugins", serverConfig.networkSettings()::queryPlugins);
        migrated |= migrateBoolean("use-waterdog", serverConfig.networkSettings()::useWaterdog);
        migrated |= migrateString("viaproxy-username-prefix", serverConfig.networkSettings()::viaProxyUsernamePrefix);

        // Chunk settings
        migrated |= migrateInt("chunk-sending-per-tick", serverConfig.chunkSettings()::sendingPerTick);
        migrated |= migrateInt("chunk-ticking-per-tick", serverConfig.chunkSettings()::tickingPerTick);
        migrated |= migrateInt("chunk-ticking-radius", serverConfig.chunkSettings()::tickingRadius);
        migrated |= migrateInt("chunk-generation-queue-size", serverConfig.chunkSettings()::generationQueueSize);
        migrated |= migrateInt("chunk-generation-population-queue-size", serverConfig.chunkSettings()::generationPopulationQueueSize);
        migrated |= migrateBoolean("light-updates", serverConfig.chunkSettings()::lightUpdates);
        migrated |= migrateBoolean("clear-chunk-tick-list", serverConfig.chunkSettings()::clearChunkTickList);
        migrated |= migrateInt("spawn-threshold", serverConfig.chunkSettings()::spawnThreshold);
        migrated |= migrateBoolean("cache-chunks", serverConfig.chunkSettings()::cacheChunks);
        migrated |= migrateBoolean("async-chunks", serverConfig.chunkSettings()::asyncChunks);

        // Entity settings
        migrated |= migrateBoolean("spawn-eggs", serverConfig.entitySettings()::spawnEggs);
        migrated |= migrateBoolean("mob-ai", serverConfig.entitySettings()::mobAi);
        migrated |= migrateBoolean("entity-auto-spawn-task", serverConfig.entitySettings()::autoSpawnTask);
        migrated |= migrateBoolean("entity-despawn-task", serverConfig.entitySettings()::despawnTask);
        migrated |= migrateInt("ticks-per-entity-spawns", serverConfig.entitySettings()::ticksPerSpawns);
        migrated |= migrateInt("ticks-per-entity-despawns", serverConfig.entitySettings()::ticksPerDespawns);

        // World settings
        migrated |= migrateBoolean("nether", serverConfig.worldSettings()::nether);
        migrated |= migrateBoolean("end", serverConfig.worldSettings()::end);
        migrated |= migrateBoolean("vanilla-portals", serverConfig.worldSettings()::vanillaPortals);
        migrated |= migrateInt("portal-ticks", serverConfig.worldSettings()::portalTicks);
        migrated |= migrateString("multi-nether-worlds", serverConfig.worldSettings()::multiNetherWorlds);
        migrated |= migrateString("anti-xray-worlds", serverConfig.worldSettings()::antiXrayWorlds);
        migrated |= migrateString("do-not-tick-worlds", serverConfig.worldSettings()::doNotTickWorlds);
        migrated |= migrateString("worlds-entity-spawning-disabled", serverConfig.worldSettings()::entitySpawningDisabledWorlds);
        migrated |= migrateBoolean("load-all-worlds", serverConfig.worldSettings()::loadAllWorlds);
        migrated |= migrateString("worlds-level-auto-save-disabled", serverConfig.worldSettings()::autoSaveDisabledWorlds);

        // Player settings
        migrated |= migrateBoolean("save-player-data", serverConfig.playerSettings()::savePlayerData);
        migrated |= migrateBoolean("save-player-data-by-uuid", serverConfig.playerSettings()::savePlayerDataByUuid);
        migrated |= migrateBoolean("persona-skins", serverConfig.playerSettings()::personaSkins);
        migrated |= migrateInt("skin-change-cooldown", serverConfig.playerSettings()::skinChangeCooldown);
        migrated |= migrateBoolean("do-not-limit-skin-geometry", serverConfig.playerSettings()::doNotLimitSkinGeometry);
        migrated |= migrateBoolean("do-not-limit-interactions", serverConfig.playerSettings()::doNotLimitInteractions);
        migrated |= migrateString("space-name-mode", serverConfig.playerSettings()::spaceNameMode);
        migrated |= migrateBoolean("xp-bottles-on-creative", serverConfig.playerSettings()::xpBottlesOnCreative);
        migrated |= migrateBoolean("stop-in-game", serverConfig.playerSettings()::stopInGame);
        migrated |= migrateBoolean("op-in-game", serverConfig.playerSettings()::opInGame);

        // Debug settings
        migrated |= migrateInt("debug-level", serverConfig.debugSettings()::debugLevel);
        migrated |= migrateBoolean("ansi-title", serverConfig.debugSettings()::ansiTitle);
        migrated |= migrateBoolean("deprecated-verbose", serverConfig.debugSettings()::deprecatedVerbose);
        migrated |= migrateBoolean("call-data-pk-send-event", serverConfig.debugSettings()::callDataPkSendEvent);
        migrated |= migrateBoolean("call-batch-pk-send-event", serverConfig.debugSettings()::callBatchPkSendEvent);
        migrated |= migrateBoolean("call-entity-motion-event", serverConfig.debugSettings()::callEntityMotionEvent);
        migrated |= migrateBoolean("block-listener", serverConfig.debugSettings()::blockListener);
        migrated |= migrateBoolean("automatic-bug-report", serverConfig.debugSettings()::automaticBugReport);
        migrated |= migrateBoolean("update-notifications", serverConfig.debugSettings()::updateNotifications);
        migrated |= migrateBoolean("bstats-metrics", serverConfig.debugSettings()::bstatsMetrics);
        migrated |= migrateString("hastebin-token", serverConfig.debugSettings()::hastebinToken);

        // Game feature settings
        migrated |= migrateBoolean("achievements", serverConfig.gameFeatureSettings()::achievements);
        migrated |= migrateBoolean("announce-player-achievements", serverConfig.gameFeatureSettings()::announcePlayerAchievements);
        migrated |= migrateBoolean("bed-spawnpoints", serverConfig.gameFeatureSettings()::bedSpawnpoints);
        migrated |= migrateBoolean("explosion-break-blocks", serverConfig.gameFeatureSettings()::explosionBreakBlocks);
        migrated |= migrateBoolean("drop-spawners", serverConfig.gameFeatureSettings()::dropSpawners);
        migrated |= migrateBoolean("anvils-enabled", serverConfig.gameFeatureSettings()::anvilsEnabled);
        migrated |= migrateBoolean("vanilla-bossbars", serverConfig.gameFeatureSettings()::vanillaBossbars);
        migrated |= migrateBoolean("use-client-spectator", serverConfig.gameFeatureSettings()::useClientSpectator);
        migrated |= migrateBoolean("enable-experiment-mode", serverConfig.gameFeatureSettings()::enableExperimentMode);
        migrated |= migrateInt("multiversion-min-protocol", serverConfig.gameFeatureSettings()::multiversionMinProtocol);
        migrated |= migrateInt("multiversion-max-protocol", serverConfig.gameFeatureSettings()::multiversionMaxProtocol);
        migrated |= migrateBoolean("enable-raw-ores", serverConfig.gameFeatureSettings()::enableRawOres);
        migrated |= migrateBoolean("enable-new-paintings", serverConfig.gameFeatureSettings()::enableNewPaintings);
        migrated |= migrateBoolean("enable-new-chicken-eggs-laying", serverConfig.gameFeatureSettings()::enableNewChickenEggsLaying);
        migrated |= migrateBoolean("forced-safety-enchant", serverConfig.gameFeatureSettings()::forcedSafetyEnchant);
        migrated |= migrateBoolean("enable-vibrant-visuals", serverConfig.gameFeatureSettings()::enableVibrantVisuals);
        migrated |= migrateBoolean("enable-raytracing", serverConfig.gameFeatureSettings()::enableRaytracing);
        migrated |= migrateBoolean("temp-ip-ban-failed-xbox-auth", serverConfig.gameFeatureSettings()::tempIpBanFailedXboxAuth);
        migrated |= migrateBoolean("strong-ip-bans", serverConfig.gameFeatureSettings()::strongIpBans);
        migrated |= migrateBoolean("check-op-movement", serverConfig.gameFeatureSettings()::checkOpMovement);

        // NetEase settings
        migrated |= migrateBoolean("netease-client-support", serverConfig.neteaseSettings()::clientSupport);
        migrated |= migrateBoolean("only-allow-netease-client", serverConfig.neteaseSettings()::onlyAllowNeteaseClient);

        if (migrated) {
            log.info("Migrated advanced settings from server.properties to nukkit-mot.yml");
        }
        return migrated;
    }

    private boolean migrateString(String key, Consumer<String> setter) {
        if (properties.exists(key)) {
            setter.accept(properties.get(key).toString());
            properties.remove(key);
            return true;
        }
        return false;
    }

    private boolean migrateBoolean(String key, Consumer<Boolean> setter) {
        if (properties.exists(key)) {
            String value = String.valueOf(properties.get(key)).trim().toLowerCase();
            setter.accept(switch (value) {
                case "on", "true", "1", "yes" -> true;
                default -> false;
            });
            properties.remove(key);
            return true;
        }
        return false;
    }

    private boolean migrateInt(String key, Consumer<Integer> setter) {
        if (properties.exists(key)) {
            String value = String.valueOf(properties.get(key)).trim();
            setter.accept(value.isEmpty() ? 0 : Integer.parseInt(value));
            properties.remove(key);
            return true;
        }
        return false;
    }
}
