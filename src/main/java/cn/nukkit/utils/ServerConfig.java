package cn.nukkit.utils;

import eu.okaeri.configs.OkaeriConfig;
import eu.okaeri.configs.annotation.Comment;
import eu.okaeri.configs.annotation.Header;
import lombok.Getter;
import lombok.Setter;

/**
 * Nukkit-MOT Server Configuration
 *
 * @author Nukkit-MOT Team
 */
@Getter
@Setter
@Header("########################################")
@Header("# Nukkit-MOT Server Configuration")
@Header("# https://github.com/MemoriesOfTime/Nukkit-MOT")
@Header("########################################")
public class ServerConfig extends OkaeriConfig {

    // ========================================
    // Basic Server Settings
    // ========================================

    @Comment("The server name shown in the server list")
    private String motd = "Minecraft Server";

    @Comment("Sub-MOTD - Additional server description")
    private String subMotd = "Powered by Nukkit-MOT";

    @Comment("Server network port (UDP)")
    private int serverPort = 19132;

    @Comment("Server IP address to bind to (0.0.0.0 = all interfaces)")
    private String serverIp = "0.0.0.0";

    @Comment("View distance for players (in chunks)")
    private int viewDistance = 8;

    @Comment("Maximum number of players allowed on the server")
    private int maxPlayers = 50;

    @Comment("Server language (eng, chs, cht, jpn, etc.)")
    private String language = "eng";

    @Comment("Force all players to use the server language")
    private boolean forceLanguage = false;

    // ========================================
    // Game Mode & Difficulty
    // ========================================

    @Comment("Default game mode (0=Survival, 1=Creative, 2=Adventure, 3=Spectator)")
    private int gamemode = 0;

    @Comment("Force players to use the default game mode")
    private boolean forceGamemode = true;

    @Comment("Game difficulty (0=Peaceful, 1=Easy, 2=Normal, 3=Hard)")
    private int difficulty = 2;

    @Comment("Enable hardcore mode (permanent death)")
    private boolean hardcore = false;

    @Comment("Enable player vs player combat")
    private boolean pvp = true;

    // ========================================
    // World Settings
    // ========================================

    @Comment("Default world name")
    private String levelName = "world";

    @Comment("World seed (leave empty for random)")
    private String levelSeed = "";

    @Comment("World type (default, flat, nether, end)")
    private String levelType = "default";

    @Comment("Generator settings (for custom world generation)")
    private String generatorSettings = "";

    @Comment("Spawn protection radius (in blocks, 0 to disable)")
    private int spawnProtection = 10;

    // ========================================
    // Mob & Entity Spawning
    // ========================================

    @Comment("Allow animal spawning")
    private boolean spawnAnimals = true;

    @Comment("Allow hostile mob spawning")
    private boolean spawnMobs = true;

    @Comment("Allow spawn eggs to work")
    private boolean spawnEggs = true;

    @Comment("Enable mob AI (pathfinding, targeting, etc.)")
    private boolean mobAi = true;

    @Comment("Enable automatic entity spawning task")
    private boolean entityAutoSpawnTask = true;

    @Comment("Enable automatic entity despawn task")
    private boolean entityDespawnTask = true;

    @Comment("Ticks between entity spawn attempts")
    private int ticksPerEntitySpawns = 200;

    @Comment("Ticks between entity despawn checks")
    private int ticksPerEntityDespawns = 12000;

    // ========================================
    // Game Features
    // ========================================

    @Comment("Enable achievements")
    private boolean achievements = true;

    @Comment("Announce player achievements in chat")
    private boolean announcePlayerAchievements = true;

    @Comment("Allow beds to set spawn points")
    private boolean bedSpawnpoints = true;

    @Comment("Allow explosions to break blocks")
    private boolean explosionBreakBlocks = true;

    @Comment("Allow players to fly (useful for creative mode)")
    private boolean allowFlight = false;

    @Comment("Allow spawners to drop when broken")
    private boolean dropSpawners = true;

    @Comment("Enable anvil usage")
    private boolean anvilsEnabled = true;

    // ========================================
    // Whitelist & Security
    // ========================================

    @Comment("Enable whitelist mode")
    private boolean whiteList = false;

    @Comment("Message shown to non-whitelisted players")
    private String whitelistReason = "§cServer is white-listed";

    @Comment("Require Xbox Live authentication")
    private boolean xboxAuth = true;

    @Comment("Enable network encryption")
    private boolean encryption = true;

    @Comment("Enable stricter IP bans")
    private boolean strongIpBans = false;

    @Comment("Temporarily ban players who fail Xbox authentication")
    private boolean tempIpBanFailedXboxAuth = false;

    // ========================================
    // RCON Settings
    // ========================================

    @Comment("Enable RCON (Remote Console)")
    private boolean enableRcon = false;

    @Comment("RCON password (change this for security!)")
    private String rconPassword = "changeme";

    @Comment("RCON port")
    private int rconPort = 25575;

    // ========================================
    // Query Settings
    // ========================================

    @Comment("Enable server query (for server lists)")
    private boolean enableQuery = true;

    @Comment("Show plugin list in query response")
    private boolean queryPlugins = false;

    // ========================================
    // Resource Packs
    // ========================================

    @Comment("Force players to accept resource packs")
    private boolean forceResources = false;

    @Comment("Allow client-side resource packs when forcing server packs")
    private boolean forceResourcesAllowClientPacks = false;

    // ========================================
    // Auto-Save & Compaction
    // ========================================

    @Comment("Enable automatic world saving")
    private boolean autoSave = true;

    @Comment("Ticks between auto-saves (6000 = 5 minutes)")
    private int ticksPerAutosave = 6000;

    @Comment("Enable automatic level compaction")
    private boolean levelAutoCompaction = true;

    @Comment("Ticks between compaction runs (36000 = 30 minutes)")
    private int levelAutoCompactionTicks = 36000;

    // ========================================
    // Network & Compression
    // ========================================

    @Comment("ZLIB compression provider (2 recommended)")
    private int zlibProvider = 2;

    @Comment("Compression level (1-9, higher = more CPU, smaller packets)")
    private int compressionLevel = 5;

    @Comment("Compression threshold in bytes")
    private String compressionThreshold = "256";

    @Comment("Use Snappy compression instead of ZLIB")
    private boolean useSnappyCompression = false;

    @Comment("RakNet packet limit per tick")
    private int rakPacketLimit = 1000000;

    @Comment("Enable RakNet cookie validation")
    private boolean enableRakSendCookie = true;

    @Comment("Client timeout in milliseconds")
    private int timeoutMilliseconds = 25000;

    // ========================================
    // Tick Performance
    // ========================================

    @Comment("Automatically adjust tick rate based on server load")
    private boolean autoTickRate = true;

    @Comment("Maximum tick rate when auto-adjusting")
    private int autoTickRateLimit = 20;

    @Comment("Base tick rate (1 = normal speed)")
    private int baseTickRate = 1;

    @Comment("Always tick players even when far from spawn")
    private boolean alwaysTickPlayers = false;

    @Comment("Chunks to send per tick")
    private int chunkSendingPerTick = 4;

    @Comment("Chunks to tick per tick")
    private int chunkTickingPerTick = 40;

    @Comment("Chunk ticking radius around players")
    private int chunkTickingRadius = 3;

    @Comment("Chunk generation queue size")
    private int chunkGenerationQueueSize = 8;

    @Comment("Chunk population queue size")
    private int chunkGenerationPopulationQueueSize = 8;

    // ========================================
    // Dimensions
    // ========================================

    @Comment("Enable the Nether dimension")
    private boolean nether = true;

    @Comment("Enable the End dimension")
    private boolean end = true;

    @Comment("Enable vanilla portal mechanics")
    private boolean vanillaPortals = true;

    @Comment("Ticks to wait in portal before teleporting")
    private int portalTicks = 80;

    @Comment("Multiple nether worlds (comma-separated)")
    private String multiNetherWorlds = "";

    // ========================================
    // Anti-Cheat
    // ========================================

    @Comment("Worlds where anti-xray is enabled (comma-separated)")
    private String antiXrayWorlds = "";

    @Comment("Check operator movement for cheating")
    private boolean checkOpMovement = false;

    @Comment("Server authoritative movement mode (server-auth, client-auth, server-auth-with-rewind)")
    private String serverAuthoritativeMovement = "server-auth";

    @Comment("Server authoritative block breaking")
    private boolean serverAuthoritativeBlockBreaking = true;

    // ========================================
    // World Management
    // ========================================

    @Comment("Worlds that should not be ticked (comma-separated)")
    private String doNotTickWorlds = "";

    @Comment("Worlds where entity spawning is disabled (comma-separated)")
    private String worldsEntitySpawningDisabled = "";

    @Comment("Load all worlds on startup")
    private boolean loadAllWorlds = true;

    @Comment("Worlds where auto-save is disabled (comma-separated)")
    private String worldsLevelAutoSaveDisabled = "";

    // ========================================
    // Async & Threading
    // ========================================

    @Comment("Number of async worker threads (auto = CPU cores + 1)")
    private String asyncWorkers = "auto";

    @Comment("Cache chunks in memory")
    private boolean cacheChunks = false;

    @Comment("Use async chunk loading")
    private boolean asyncChunks = true;

    @Comment("Enable thread watchdog")
    private boolean threadWatchdog = true;

    @Comment("Thread watchdog check interval (ms)")
    private int threadWatchdogTick = 60000;

    // ========================================
    // Debug & Logging
    // ========================================

    @Comment("Debug level (1=errors only, 2=warnings, 3=info)")
    private int debugLevel = 1;

    @Comment("Enable ANSI colors in terminal title")
    private boolean ansiTitle = false;

    @Comment("Show verbose deprecation warnings")
    private boolean deprecatedVerbose = true;

    // ========================================
    // Lighting & Updates
    // ========================================

    @Comment("Enable dynamic light updates")
    private boolean lightUpdates = false;

    @Comment("Clear chunk tick list on save")
    private boolean clearChunkTickList = true;

    @Comment("Spawn threshold for chunk loading")
    private int spawnThreshold = 56;

    // ========================================
    // Player Data
    // ========================================

    @Comment("Save player data to disk")
    private boolean savePlayerData = true;

    @Comment("Save player data by UUID instead of name")
    private boolean savePlayerDataByUuid = true;

    @Comment("Allow persona skins (custom player models)")
    private boolean personaSkins = true;

    @Comment("Cooldown for skin changes (seconds)")
    private int skinChangeCooldown = 15;

    // ========================================
    // In-Game Controls
    // ========================================

    @Comment("Allow stopping server from in-game")
    private boolean stopInGame = false;

    @Comment("Allow opping players from in-game")
    private boolean opInGame = true;

    @Comment("How to handle spaces in usernames (ignore, replace, deny)")
    private String spaceNameMode = "ignore";

    @Comment("Allow XP bottles in creative mode")
    private boolean xpBottlesOnCreative = true;

    // ========================================
    // Event Calling
    // ========================================

    @Comment("Call DataPacketSendEvent")
    private boolean callDataPkSendEvent = true;

    @Comment("Call BatchPacketSendEvent")
    private boolean callBatchPkSendEvent = true;

    @Comment("Call EntityMotionEvent")
    private boolean callEntityMotionEvent = true;

    @Comment("Enable block listener")
    private boolean blockListener = true;

    // ========================================
    // Garbage Collection
    // ========================================

    @Comment("Enable level garbage collection")
    private boolean doLevelGc = true;

    // ========================================
    // Interaction Limits
    // ========================================

    @Comment("Don't limit interaction distance")
    private boolean doNotLimitInteractions = false;

    @Comment("Don't limit skin geometry size")
    private boolean doNotLimitSkinGeometry = true;

    // ========================================
    // Monitoring & Metrics
    // ========================================

    @Comment("Enable automatic bug reporting (Sentry)")
    private boolean automaticBugReport = true;

    @Comment("Show update notifications")
    private boolean updateNotifications = true;

    @Comment("Enable bStats metrics")
    private boolean bstatsMetrics = true;

    // ========================================
    // Game Version Features
    // ========================================

    @Comment("Enable vanilla boss bars")
    private boolean vanillaBossbars = false;

    @Comment("Use client-side spectator mode")
    private boolean useClientSpectator = true;

    @Comment("Enable experimental mode features")
    private boolean enableExperimentMode = true;

    // ========================================
    // Multi-Version Support
    // ========================================

    @Comment("Minimum protocol version to allow (0 = no limit)")
    private int multiversionMinProtocol = 0;

    @Comment("Maximum protocol version to allow (-1 = no limit)")
    private int multiversionMaxProtocol = -1;

    // ========================================
    // Proxy
    // ========================================

    @Comment("Enable WaterDog proxy mode")
    private boolean useWaterdog = false;

    // ========================================
    // Performance Tools
    // ========================================

    @Comment("Enable Spark profiler")
    private boolean enableSpark = false;

    @Comment("Hastebin API token for paste uploads")
    private String hastebinToken = "";

    // ========================================
    // Database
    // ========================================

    @Comment("LevelDB cache size in MB")
    private int leveldbCacheMb = 80;

    @Comment("Use native LevelDB library")
    private boolean useNativeLeveldb = false;

    // ========================================
    // Experimental Features
    // ========================================

    @Comment("Enable raw ore items")
    private boolean enableRawOres = true;

    @Comment("Enable new painting variants")
    private boolean enableNewPaintings = true;

    @Comment("Enable new chicken egg laying mechanics")
    private boolean enableNewChickenEggsLaying = true;

    @Comment("Force safety enchantment checks")
    private boolean forcedSafetyEnchant = true;

    @Comment("Enable vibrant visuals feature")
    private boolean enableVibrantVisuals = true;

    @Comment("Enable raytracing support")
    private boolean enableRaytracing = true;

    // ========================================
    // NetEase Client Support
    // ========================================

    @Comment("Enable NetEase client support")
    private boolean neteaseClientSupport = false;

    @Comment("Only allow NetEase clients")
    private boolean onlyAllowNeteaseClient = false;

    // ========================================
    // Shutdown
    // ========================================

    @Comment("Message shown when server shuts down")
    private String shutdownMessage = "§cServer closed";
}
