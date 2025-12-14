package cn.nukkit.config;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record Settings(
        @JsonProperty("server") Server server,
        @JsonProperty("network") Network network,
        @JsonProperty("world") World world,
        @JsonProperty("players") Players players,
        @JsonProperty("features") Features features,
        @JsonProperty("performance") Performance performance,
        @JsonProperty("security") Security security,
        @JsonProperty("debug") Debug debug,
        @JsonProperty("integration") Integration integration,
        @JsonProperty("experimental") Experimental experimental
) {

    @JsonCreator
    public static Settings create(
            @JsonProperty("server") Server server,
            @JsonProperty("network") Network network,
            @JsonProperty("world") World world,
            @JsonProperty("players") Players players,
            @JsonProperty("features") Features features,
            @JsonProperty("performance") Performance performance,
            @JsonProperty("security") Security security,
            @JsonProperty("debug") Debug debug,
            @JsonProperty("integration") Integration integration,
            @JsonProperty("experimental") Experimental experimental
    ) {
        return new Settings(server, network, world, players, features, performance, security, debug, integration, experimental);
    }

    public static Settings defaults() {
        return new Settings(
                Server.defaults(),
                Network.defaults(),
                World.defaults(),
                Players.defaults(),
                Features.defaults(),
                Performance.defaults(),
                Security.defaults(),
                Debug.defaults(),
                Integration.defaults(),
                Experimental.defaults()
        );
    }

    public record Server(
            @JsonProperty("motd") String motd,
            @JsonProperty("sub-motd") String subMotd,
            @JsonProperty("language") String language,
            @JsonProperty("force-language") boolean forceLanguage,
            @JsonProperty("shutdown-message") String shutdownMessage,
            @JsonProperty("ansi-title") boolean ansiTitle,
            @JsonProperty("update-notifications") boolean updateNotifications
    ) {
        public static Server defaults() {
            return new Server(
                    "Minecraft Server",
                    "Powered by Nukkit-MOT",
                    "eng",
                    false,
                    "§cServer closed",
                    false,
                    true
            );
        }
    }

    public record Network(
            @JsonProperty("server-ip") String ip,
            @JsonProperty("server-port") int port,
            @JsonProperty("enable-query") boolean enableQuery,
            @JsonProperty("query-plugins") boolean queryPlugins,
            @JsonProperty("enable-rcon") boolean enableRcon,
            @JsonProperty("rcon.password") String rconPassword,
            @JsonProperty("rcon.port") int rconPort,
            @JsonProperty("multiversion-min-protocol") int minProtocol,
            @JsonProperty("multiversion-max-protocol") int maxProtocol,
            @JsonProperty("use-waterdog") boolean useWaterdog,
            @JsonProperty("timeout-milliseconds") int timeoutMilliseconds,
            @JsonProperty("rak-packet-limit") int rakPacketLimit,
            @JsonProperty("enable-rak-send-cookie") boolean enableRakSendCookie,
            @JsonProperty("compression-threshold") int compressionThreshold,
            @JsonProperty("use-snappy-compression") boolean useSnappyCompression,
            @JsonProperty("compression-level") int compressionLevel,
            @JsonProperty("async-workers") Object asyncWorkers,
            @JsonProperty("zlib-provider") int zlibProvider
    ) {
        public static Network defaults() {
            return new Network(
                    "0.0.0.0",
                    19132,
                    true,
                    false,
                    false,
                    "",
                    25575,
                    0,
                    -1,
                    false,
                    25000,
                    60,
                    true,
                    256,
                    false,
                    5,
                    "auto",
                    2
            );
        }
    }

    public record World(
            @JsonProperty("level-name") String levelName,
            @JsonProperty("level-seed") String levelSeed,
            @JsonProperty("level-type") String levelType,
            @JsonProperty("generator-settings") String generatorSettings,
            @JsonProperty("nether") boolean nether,
            @JsonProperty("end") boolean end,
            @JsonProperty("vanilla-portals") boolean vanillaPortals,
            @JsonProperty("portal-ticks") int portalTicks,
            @JsonProperty("multi-nether-worlds") String multiNetherWorlds,
            @JsonProperty("anti-xray-worlds") String antiXrayWorlds,
            @JsonProperty("do-not-tick-worlds") String noTickingWorlds,
            @JsonProperty("worlds-entity-spawning-disabled") String disabledSpawnWorlds,
            @JsonProperty("worlds-level-auto-save-disabled") String nonAutoSaveWorlds,
            @JsonProperty("load-all-worlds") boolean loadAllWorlds,
            @JsonProperty("level-auto-compaction") boolean levelAutoCompaction,
            @JsonProperty("level-auto-compaction-ticks") int levelAutoCompactionTicks,
            @JsonProperty("async-chunks") boolean asyncChunks,
            @JsonProperty("cache-chunks") boolean cacheChunks,
            @JsonProperty("clear-chunk-tick-list") boolean clearChunkTickList
    ) {
        public static World defaults() {
            return new World(
                    "world",
                    "",
                    "default",
                    "",
                    true,
                    true,
                    true,
                    80,
                    "",
                    "",
                    "",
                    "",
                    "",
                    true,
                    true,
                    36000,
                    true,
                    false,
                    true
            );
        }
    }

    public record Players(
            @JsonProperty("max-players") int maxPlayers,
            @JsonProperty("white-list") boolean whitelist,
            @JsonProperty("whitelist-reason") String whitelistReason,
            @JsonProperty("achievements") boolean achievements,
            @JsonProperty("announce-player-achievements") boolean announceAchievements,
            @JsonProperty("save-player-data") boolean savePlayerData,
            @JsonProperty("save-player-data-by-uuid") boolean savePlayerDataByUuid,
            @JsonProperty("skin-change-cooldown") int skinChangeCooldown,
            @JsonProperty("space-name-mode") String spaceNameMode,
            @JsonProperty("allow-flight") boolean allowFlight,
            @JsonProperty("spawn-eggs") boolean spawnEggs,
            @JsonProperty("xp-bottles-on-creative") boolean xpBottlesOnCreative,
            @JsonProperty("persona-skins") boolean personaSkins,
            @JsonProperty("do-not-limit-skin-geometry") boolean doNotLimitSkinGeometry,
            @JsonProperty("do-not-limit-interactions") boolean doNotLimitInteractions
    ) {
        public static Players defaults() {
            return new Players(
                    50,
                    false,
                    "§cServer is white-listed",
                    true,
                    true,
                    true,
                    true,
                    15,
                    "ignore",
                    false,
                    true,
                    true,
                    true,
                    true,
                    false
            );
        }
    }

    public record Features(
            @JsonProperty("gamemode") int gamemode,
            @JsonProperty("force-gamemode") boolean forceGamemode,
            @JsonProperty("difficulty") String difficulty,
            @JsonProperty("hardcore") boolean hardcore,
            @JsonProperty("pvp") boolean pvp,
            @JsonProperty("spawn-protection") int spawnProtection,
            @JsonProperty("mob-ai") boolean mobAi,
            @JsonProperty("spawn-animals") boolean spawnAnimals,
            @JsonProperty("spawn-mobs") boolean spawnMobs,
            @JsonProperty("explosion-break-blocks") boolean explosionBreakBlocks,
            @JsonProperty("bed-spawnpoints") boolean bedSpawnpoints,
            @JsonProperty("anvils-enabled") boolean anvilsEnabled,
            @JsonProperty("drop-spawners") boolean dropSpawners,
            @JsonProperty("block-listener") boolean blockListener,
            @JsonProperty("enable-raw-ores") boolean enableRawOres,
            @JsonProperty("enable-new-paintings") boolean enableNewPaintings,
            @JsonProperty("enable-new-chicken-eggs-laying") boolean enableNewChickenEggsLaying,
            @JsonProperty("forced-safety-enchant") boolean forcedSafetyEnchant,
            @JsonProperty("stop-in-game") boolean stopInGame,
            @JsonProperty("op-in-game") boolean opInGame
    ) {
        public static Features defaults() {
            return new Features(
                    0,
                    true,
                    "2",
                    false,
                    true,
                    10,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    true,
                    false,
                    true
            );
        }
    }

    public record Performance(
            @JsonProperty("view-distance") int viewDistance,
            @JsonProperty("chunk-sending-per-tick") int chunkSendingPerTick,
            @JsonProperty("chunk-ticking-per-tick") int chunkTickingPerTick,
            @JsonProperty("chunk-ticking-radius") int chunkTickingRadius,
            @JsonProperty("chunk-generation-queue-size") int chunkGenerationQueueSize,
            @JsonProperty("chunk-generation-population-queue-size") int chunkGenerationPopulationQueueSize,
            @JsonProperty("spawn-threshold") int spawnThreshold,
            @JsonProperty("ticks-per-autosave") int ticksPerAutosave,
            @JsonProperty("ticks-per-entity-follow") int ticksPerEntityFollow,
            @JsonProperty("ticks-per-entity-spawns") int ticksPerEntitySpawns,
            @JsonProperty("ticks-per-entity-despawns") int ticksPerEntityDespawns,
            @JsonProperty("auto-save") boolean autoSave,
            @JsonProperty("auto-tick-rate") boolean autoTickRate,
            @JsonProperty("auto-tick-rate-limit") int autoTickRateLimit,
            @JsonProperty("base-tick-rate") int baseTickRate,
            @JsonProperty("always-tick-players") boolean alwaysTickPlayers,
            @JsonProperty("light-updates") boolean lightUpdates,
            @JsonProperty("entity-auto-spawn-task") boolean entityAutoSpawnTask,
            @JsonProperty("entity-despawn-task") boolean entityDespawnTask,
            @JsonProperty("do-level-gc") boolean doLevelGc
    ) {
        public static Performance defaults() {
            return new Performance(
                    8,
                    4,
                    40,
                    3,
                    8,
                    8,
                    56,
                    6000,
                    5,
                    200,
                    12000,
                    true,
                    true,
                    20,
                    1,
                    false,
                    false,
                    true,
                    true,
                    true
            );
        }
    }

    public record Security(
            @JsonProperty("xbox-auth") boolean xboxAuth,
            @JsonProperty("encryption") boolean encryption,
            @JsonProperty("temp-ip-ban-failed-xbox-auth") boolean tempIpBanFailedXboxAuth,
            @JsonProperty("strong-ip-bans") boolean strongIpBans,
            @JsonProperty("check-op-movement") boolean checkOpMovement
    ) {
        public static Security defaults() {
            return new Security(
                    true,
                    true,
                    false,
                    false,
                    false
            );
        }
    }

    public record Debug(
            @JsonProperty("debug-level") int debugLevel,
            @JsonProperty("deprecated-verbose") boolean deprecatedVerbose,
            @JsonProperty("automatic-bug-report") boolean automaticBugReport,
            @JsonProperty("bstats-metrics") boolean bstatsMetrics
    ) {
        public static Debug defaults() {
            return new Debug(
                    1,
                    true,
                    true,
                    true
            );
        }
    }

    public record Integration(
            @JsonProperty("enable-spark") boolean enableSpark,
            @JsonProperty("hastebin-token") String hastebinToken,
            @JsonProperty("netease-client-support") boolean neteaseClientSupport,
            @JsonProperty("only-allow-netease-client") boolean onlyAllowNeteaseClient
    ) {
        public static Integration defaults() {
            return new Integration(
                    false,
                    "",
                    false,
                    false
            );
        }
    }

    public record Experimental(
            @JsonProperty("enable-experiment-mode") boolean enableExperimentMode,
            @JsonProperty("server-authoritative-movement") String serverAuthoritativeMovement,
            @JsonProperty("server-authoritative-block-breaking") boolean serverAuthoritativeBlockBreaking,
            @JsonProperty("use-client-spectator") boolean useClientSpectator,
            @JsonProperty("enable-vibrant-visuals") boolean enableVibrantVisuals,
            @JsonProperty("enable-raytracing") boolean enableRaytracing
    ) {
        public static Experimental defaults() {
            return new Experimental(
                    true,
                    "server-auth",
                    true,
                    true,
                    true,
                    true
            );
        }
    }
}