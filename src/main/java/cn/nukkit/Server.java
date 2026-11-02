package cn.nukkit;

import cn.nukkit.block.Block;
import cn.nukkit.block.custom.CustomBlockManager;
import cn.nukkit.blockentity.*;
import cn.nukkit.command.*;
import cn.nukkit.console.NukkitConsole;
import cn.nukkit.dispenser.DispenseBehaviorRegister;
import cn.nukkit.entity.Attribute;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityHuman;
import cn.nukkit.entity.data.Skin;
import cn.nukkit.entity.data.profession.Profession;
import cn.nukkit.entity.data.property.EntityProperty;
import cn.nukkit.entity.item.*;
import cn.nukkit.entity.mob.*;
import cn.nukkit.entity.passive.*;
import cn.nukkit.entity.projectile.*;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.event.HandlerList;
import cn.nukkit.event.level.LevelInitEvent;
import cn.nukkit.event.level.LevelLoadEvent;
import cn.nukkit.event.server.*;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.InventoryMutationGuard;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.level.*;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.LevelProviderManager;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.generator.*;
import cn.nukkit.level.generator.Void;
import cn.nukkit.level.tickingarea.manager.SimpleTickingAreaManager;
import cn.nukkit.level.tickingarea.manager.TickingAreaManager;
import cn.nukkit.level.tickingarea.storage.JSONTickingAreaStorage;
import cn.nukkit.math.NukkitMath;
import cn.nukkit.metadata.EntityMetadataStore;
import cn.nukkit.metadata.LevelMetadataStore;
import cn.nukkit.metadata.PlayerMetadataStore;
import cn.nukkit.metrics.NukkitMetrics;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.DoubleTag;
import cn.nukkit.nbt.tag.FloatTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.network.NetherNetInterface;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.encryption.EncryptionUtils;
import cn.nukkit.network.encryption.LoginChainVerifier;
import cn.nukkit.network.protocol.*;
import cn.nukkit.network.protocol.types.auth.AuthType;
import cn.nukkit.network.query.QueryHandler;
import cn.nukkit.network.rcon.RCON;
import cn.nukkit.permission.BanEntry;
import cn.nukkit.permission.BanList;
import cn.nukkit.permission.DefaultPermissions;
import cn.nukkit.permission.Permissible;
import cn.nukkit.plugin.*;
import cn.nukkit.plugin.service.NKServiceManager;
import cn.nukkit.plugin.service.ServiceManager;
import cn.nukkit.potion.Effect;
import cn.nukkit.potion.Potion;
import cn.nukkit.resourcepacks.ResourcePackManager;
import cn.nukkit.resourcepacks.loader.JarPluginResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ResourcePackLoader;
import cn.nukkit.resourcepacks.loader.ZippedBehaviourPackLoader;
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.Task;
import cn.nukkit.scoreboard.manager.IScoreboardManager;
import cn.nukkit.scoreboard.manager.ScoreboardManager;
import cn.nukkit.scoreboard.storage.JSONScoreboardStorage;
import cn.nukkit.utils.*;
import cn.nukkit.utils.bugreport.ExceptionHandler;
import cn.nukkit.utils.serverconfig.ConfigComments;
import cn.nukkit.utils.serverconfig.ConfigMigration;
import cn.nukkit.utils.serverconfig.ResourcePackMigration;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.NetherNetSettings;
import cn.nukkit.utils.serverconfig.category.WorldEntry;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.MapMaker;
import com.google.gson.JsonParser;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import io.netty.buffer.ByteBuf;
import io.sentry.Sentry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.config.RakServerCookieMode;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.jetbrains.annotations.NotNull;
import org.jose4j.jwt.consumer.InvalidJwtException;

import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.LongSupplier;
import java.util.function.BooleanSupplier;
import java.util.regex.Pattern;

/**
 * The main server class
 *
 * @author MagicDroidX
 * @author Box
 */
@Log4j2
public class Server {

    public static final String BROADCAST_CHANNEL_ADMINISTRATIVE = "nukkit.broadcast.admin";
    public static final String BROADCAST_CHANNEL_USERS = "nukkit.broadcast.user";

    private static Server instance;

    private final BanList banByName;
    private final BanList banByIP;
    private final Config operators;
    private final Config whitelist;

    private final AtomicBoolean isRunning = new AtomicBoolean(true);
    private volatile boolean hasStopped;
    private volatile String requestedShutdownReason;
    private boolean shutdownAdvancing;
    private boolean shutdownAdvanceRequested;
    private boolean shutdownStopEventDispatched;
    private boolean shutdownPlayerClosingStarted;
    private volatile Thread shutdownHookThread;
    private volatile Thread shutdownHookWorker;
    private volatile long shutdownDeadlineNanos;
    private volatile boolean shutdownTimedOut;
    private final Object deferredShutdownIdentity = new Object();
    private final CompletableFuture<Void> shutdownCompletion = new CompletableFuture<>();
    private final ConcurrentMap<Player, Runnable> pendingPlayerCloses = new ConcurrentHashMap<>();
    private volatile long shutdownProgressAt = Long.MAX_VALUE;

    private final PluginManager pluginManager;
    private final ServerScheduler scheduler;

    private int tickCounter;
    private long nextTick;
    private final float[] tickAverage = {20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20, 20};
    private final float[] useAverage = {0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0};
    private float maxTick = 20;
    private float maxUse = 0;

    private final NukkitConsole console;
    private final ConsoleThread consoleThread;

    private final SimpleCommandMap commandMap;
    private final CraftingManager craftingManager;
    private final ResourcePackManager resourcePackManager;
    private final ConsoleCommandSender consoleSender;
    private final IScoreboardManager scoreboardManager;

    private final TickingAreaManager tickingAreaManager;

    private int maxPlayers;
    private boolean autoSave = true;
    /**
     * Automatic compression of the world
     */
    private boolean autoCompaction = true;
    private int autoCompactionTicks = 60 * 30 * 20;

    private RCON rcon;

    private final EntityMetadataStore entityMetadata;
    private final PlayerMetadataStore playerMetadata;
    private final LevelMetadataStore levelMetadata;
    private final Network network;

    private boolean autoTickRate;
    private int autoTickRateLimit;
    private boolean alwaysTickPlayers;
    private int baseTickRate;
    private int difficulty;
    private int defaultGameMode = Integer.MAX_VALUE;
    int c_s_spawnThreshold;

    private int autoSaveTicker;
    private int autoSaveTicks;

    private final BaseLang baseLang;
    private boolean forceLanguage;

    private final String filePath;
    private final String dataPath;
    private final String pluginPath;

    @NotNull
    private String ip = "0.0.0.0";
    private int port;
    private boolean ipv6Enabled = false;
    @NotNull
    private String ipv6Address = "::";
    private int ipv6Port = -1;
    private QueryHandler queryHandler;
    private QueryRegenerateEvent queryRegenerateEvent;
    private final UUID serverID;
    private final Config properties;
    private volatile ServerConfig serverConfig;

    private final Map<InetSocketAddress, Player> players = new HashMap<>();
    final Map<UUID, Player> playerList = new HashMap<>();

    /**
     * Worlds where automatic mob spawning is disabled.
     */
    public static final List<String> disabledSpawnWorlds = new ArrayList<>();
    /**
     * Worlds where automatic saving is disabled.
     */
    public static final List<String> nonAutoSaveWorlds = new ArrayList<>();
    /**
     * Worlds that have their own nether worlds.
     */
    public static final List<String> multiNetherWorlds = new ArrayList<>();
    public static final List<String> antiXrayWorlds = new ArrayList<>();
    /**
     * Worlds where random block ticking is disabled.
     */
    public static final List<String> noTickingWorlds = new ArrayList<>();

    private static final Pattern uuidPattern = Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}.dat$");

    private final Map<Integer, Level> levels = new ConcurrentHashMap<>() {
        @Override
        public Level put(@NotNull Integer key, @NotNull Level value) {
            Level result = super.put(key, value);
            levelArray = levels.values().toArray(new Level[0]);
            return result;
        }

        @Override
        public boolean remove(Object key, Object value) {
            boolean result = super.remove(key, value);
            levelArray = levels.values().toArray(new Level[0]);
            return result;
        }

        @Override
        public Level remove(@NotNull Object key) {
            Level result = super.remove(key);
            levelArray = levels.values().toArray(new Level[0]);
            return result;
        }
    };

    private Level[] levelArray = new Level[0];
    private final ServiceManager serviceManager = new NKServiceManager();
    private Level defaultLevel;
    private ClientCacheBlobStore clientCacheBlobStore;
    private final Thread currentThread;
    private Watchdog watchdog;
    private NukkitMetrics nukkitMetrics;
    private final DB nameLookup;
    /**
     * Trimmed lower-case names whose padded profiles were not folded at startup, with the reason.
     * Their offline logins are refused: the profile a login would open may not be the latest.
     */
    private Map<String, String> profileFoldBlockedNames = Map.of();
    private PlayerDataSerializer playerDataSerializer;
    private SpawnerTask spawnerTask;

    /**
     * 玩家数据 IO 的按身份锁，key 为数据路径标识（UUID 字符串或小写名）。
     * <p>
     * Per-identity locks for player data IO, keyed by the data path identity (UUID string or
     * lowercased name).
     */
    private final ConcurrentMap<String, ReentrantLock> playerDataLocks = createPlayerDataLocks();
    private PlayerDataSaveQueue playerDataSaves = new PlayerDataSaveQueue();
    private Map<Player, LoginProfileOwner> pendingProfileLogins = new IdentityHashMap<>();
    private ConcurrentLinkedQueue<Runnable> pendingProfileIO = new ConcurrentLinkedQueue<>();
    private AtomicInteger profileCaptureHandoffs = new AtomicInteger();

    /** The terminated MAIN may be replaced only by this server's designated shutdown owner. */
    boolean isProfileOwnerThread() {
        return isPrimaryThread() || Thread.currentThread() == shutdownHookWorker && currentThread != null
                && currentThread.getState() == Thread.State.TERMINATED && InventoryMutationGuard.isIdle();
    }

    PlayerDataSaveQueue profileQueue() {
        // Constructor-bypassing integrations still receive one server-owned queue.
        synchronized (this) {
            if (playerDataSaves == null) playerDataSaves = new PlayerDataSaveQueue();
            return playerDataSaves;
        }
    }

    private record LoginProfileOwner(String name, UUID uuid) { }

    /** MAIN-only, bounded native login ownership through final profile application. */
    boolean reserveLoginProfile(Player player, String name, UUID uuid) {
        if (pendingProfileLogins == null) pendingProfileLogins = new IdentityHashMap<>();
        for (var entry : pendingProfileLogins.entrySet()) {
            if (entry.getKey() != player && (entry.getValue().name().equals(name) || entry.getValue().uuid().equals(uuid))) return false;
        }
        if (!pendingProfileLogins.containsKey(player) && pendingProfileLogins.size() >= 256) return false;
        pendingProfileLogins.put(player, new LoginProfileOwner(name, uuid));
        return true;
    }

    void releaseLoginProfile(Player player) {
        if (pendingProfileLogins != null) pendingProfileLogins.remove(player);
    }

    /**
     * The server's MOTD. Remember to call network.setName() when updated.
     */
    public String motd;
    /**
     * Disconnection message shown to players who are not allowed to join due to whitelist.
     */
    public String whitelistReason;
    /**
     * Mob AI enabled.
     */
    public boolean mobAiEnabled;
    /**
     * Default player data saving enabled.
     */
    public boolean shouldSavePlayerData;
    /**
     * Anti fly checks enabled.
     */
    public boolean flyChecks;
    /**
     * Hardcore mode enabled.
     */
    public boolean isHardcore;
    /**
     * Force resource packs.
     */
    public boolean forceResources;
    /**
     * Allow clients to use their own resource packs when enabling mandatory resource packs
     */
    public boolean forceResourcesAllowOwnPacks;
    /**
     * Force player gamemode to default on every join.
     */
    public boolean forceGamemode;
    /**
     * The nether dimension and portals enabled.
     */
    public boolean netherEnabled;
    /**
     * Level garbage collection enabled.
     */
    public boolean doLevelGC;
    /**
     * Call BatchPacketsEvent on batch packet sending.
     */
    public boolean callBatchPkEv;
    /**
     * Whitelist enabled.
     */
    public boolean whitelistEnabled;
    /**
     * Xbox authentication enabled.
     */
    public boolean xboxAuth;

    /**
     * When true a duplicate login refuses the newcomer instead of closing the session that is
     * already in the world. Defaults to false, the historical behaviour.
     */
    private boolean keepExistingSessionOnDuplicateLogin;
    /**
     * Spawn eggs enabled.
     */
    public boolean spawnEggsEnabled;
    /**
     * XP bottles can be used on creative.
     */
    public boolean xpBottlesOnCreative;
    /**
     * Call DataPacketSendEvent on data packet sending.
     */
    public boolean callDataPkSendEv;
    /**
     * Bed spawnpoints enabled.
     */
    public boolean bedSpawnpoints;
    /**
     * Server side achievements enabled.
     */
    public boolean achievementsEnabled;
    /**
     * Temporary ban player on failed Xbox authentication.
     */
    public boolean banXBAuthFailed;
    /**
     * The end dimension and portals enabled.
     */
    public boolean endEnabled;
    /**
     * Pvp enabled. Can be changed per world using game rules.
     */
    public boolean pvpEnabled;
    /**
     * Announce server side announcements to all players.
     */
    public boolean announceAchievements;
    /**
     * Enable movement checks for OPs.
     */
    public boolean checkOpMovement;
    /**
     * Disable player interaction spam limiter.
     */
    public boolean doNotLimitInteractions;
    /**
     * After how many ticks mobs are despawned.
     */
    public int mobDespawnTicks;
    /**
     * How many chunks are sent to player per tick.
     */
    public int chunksPerTick;
    /**
     * How many chunks needs to be sent before the player can spawn.
     */
    public int spawnThreshold;
    /**
     * Zlib compression level for packets
     */
    public int networkCompressionLevel;
    /**
     * Zlib compression level for chuck packets
     */
    public int chunkCompressionLevel;
    /**
     * Maximum view distance.
     */
    public int viewDistance;
    /**
     * Server's default gamemode.
     */
    public int gamemode;
    /**
     * Minimum amount of time between player skin changes.
     */
    public int skinChangeCooldown;
    /**
     * Spawn protection radius.
     */
    public int spawnRadius;
    /**
     * Minimum allowed protocol version.
     */
    public int minimumProtocol;
    /**
     * Maximum allowed protocol version.
     */
    public int maximumProtocol;
    /**
     * Do not limit the maximum size of player skins.
     */
    public boolean doNotLimitSkinGeometry;
    /**
     * Mob spawning from blocks and items enabled.
     */
    public boolean mobsFromBlocks;
    /**
     * Explosions breaking blocks enabled.
     */
    public boolean explosionBreakBlocks;
    /**
     * Boss bars enabled for wither and ender dragon.
     */
    public boolean vanillaBossBar;
    /**
     * Stop command allowed in game.
     */
    public boolean stopInGame;
    /**
     * OP command allowed in game.
     */
    public boolean opInGame;
    /**
     * Handling player names with spaces.
        [0] "disabled" - Players with names containing spaces are prohibited from entering the server.
        [1] "ignore" - Ignore names with spaces (default).
        [2] "replacing" - Replace spaces in player names with "_".
     */
    public int spaceMode;
    /**
     * Sky light updates enabled.
     */
    public boolean lightUpdates;
    /**
     * Showing plugins in query enabled.
     */
    public boolean queryPlugins;
    /**
     * Mob despawning enabled.
     */
    public boolean despawnMobs;
    /**
     * Squared horizontal distance past which mobs are updated once per second (entity activation).
     * Zero or less disables the throttle.
     */
    public double entityActivationRangeSquared;
    /** Ticks between target searches for target-less mobs; 1 = per-tick search. See {@code BaseEntity.shouldRunTargetCheck()}. */
    public int entityTargetSearchInterval = 10;
    /**
     * Strong RakNet level IP bans enabled.
     */
    public boolean strongIPBans;
    /**
     * Auto spawning of animals enabled.
     */
    public boolean spawnAnimals;
    /**
     * Auto spawning of monsters enabled.
     */
    public boolean spawnMonsters;
    /**
     * Anvils enabled.
     */
    public boolean anvilsEnabled;
    /**
     * Player data is saved by player uuid instead of by player name.
     */
    public boolean savePlayerDataByUuid;
    /**
     * More vanilla like portal logics enabled.
     */
    public boolean vanillaPortals;
    /**
     * Ticks required for the player to trigger the portal.
     */
    public int portalTicks;
    /**
     * Persona skins allowed.
     */
    public boolean personaSkins;
    /**
     * Chunk caching enabled.
     */
    public boolean cacheChunks;
    /**
     * Call EntityMotionEvent on entity movement.
     */
    public boolean callEntityMotionEv;
    /**
     * Whatever allow spawner drops.
     */
    public boolean dropSpawners;
    /**
     * Check for new releases automatically.
     */
    public boolean updateChecks;
    /**
     * Enable experimental mode
     */
    public boolean enableExperimentMode;
    /**
     * Asynchronous chunk sending, loading and saving (Experiment)
     * <p>
     * 异步区块发送、加载与保存(实验性)
     */
    public volatile boolean asyncChunkSending;
    /**
     * 每世界挂起区块写上限,超限暂停卸载(背压)
     * <p>
     * Max pending chunk writes per world before unloading pauses (backpressure)
     */
    public int maxPendingChunkWrites = 128;
    /**
     * Show a console message when a plugin uses deprecated API methods
     */
    public boolean deprecatedVerbose;
    /**
     * Enable automatic bug reporting
     */
    public boolean automaticBugReport;
    /**
     * Player movement processing mode
     */
    public int serverAuthoritativeMovementMode;
    /**
     * Server authority block destruction
     */
    public boolean serverAuthoritativeBlockBreaking;
    /**
     * Server authoritative inventory mode
     * When enabled, server has final authority over inventory changes
     * @since v1.16.100 (protocol 407+)
     */
    public boolean serverAuthoritativeInventory;
    /**
     * Network encryption
     */
    public boolean encryptionEnabled;
    /**
     * Using WaterdogPE Proxy
     */
    public boolean useWaterdog;
    /**
     * Username prefix for ViaProxy Java Edition clients (empty to disable)
     */
    public String viaProxyUsernamePrefix;
    /**
     * Using Snappy compression
     */
    public boolean useSnappy;
    /**
     * 1.19.30+ Using Client Spectator Mode
     * Because some servers may require the use of the inventory in spectator mode
     * so we have prepared this option for server owners to choose for themselves
     */
    public boolean useClientSpectator;
    /**
     * Network Compression Threshold
     */
    public int networkCompressionThreshold;
    /**
     * Enable Spark Plugin
     */
    public boolean enableSpark;
    /**
     * This is needed for structure generation
     */
    public final ForkJoinPool computeThreadPool;
    /**
     * 全世界共享的区块序列化池(CPU lane):全区块/子区块网络序列化。
     * 懒建以支持配置热重载后打开 async-chunks;async-chunks 关闭时为 null。
     * <p>
     * Shared by all worlds: chunk and sub-chunk network serialization (CPU lane).
     * Created lazily so a config reload can still turn async-chunks on; null when it is off.
     */
    private volatile ForkJoinPool chunkSerializationPool;
    /**
     * 全世界共享的世界 I/O 池:异步区块读取(后续接入 LevelDB 写)。懒建,语义同上。
     * <p>
     * Shared by all worlds: async chunk reads (LevelDB writes later). Lazily created; same semantics as above.
     */
    private volatile ForkJoinPool worldChunkLoadPool;
    /**
     * Set LevelDB cache size.
     */
    public int levelDbCache;
    /**
     * Use native LevelDB implementation for better performance.
     */
    public boolean useNativeLevelDB;
    /**
     * Enable Raw Drop of Iron and Gold
     */
    public boolean enableRawOres;
    /**
     * Enable 1.21 paintings
     */
    public boolean enableNewPaintings;
    /**
     * Enable chicken egg laying from 1.21.70
     */
    public boolean enableNewChickenEggsLaying;
    /**
     * A number of datagram packets each address can send within one RakNet tick (10ms)
     */
    public int rakPacketLimit;
    /**
     * Login stage timeout in milliseconds.
     */
    public int networkLoginTimeoutMilliseconds;
    /**
     * Temporary disable world saving to allow safe backup of leveldb worlds.
     */
    public boolean holdWorldSave;
    /**
     * RakNet cookie mode
     */
    public RakServerCookieMode rakCookieMode;
    /**
     * Enable Proxy Protocol v2 to get real client IP behind proxies like FRP
     */
    public boolean enableProxyProtocol;
    /**
     * Whitelist of proxy IPv4/IPv6 CIDRs allowed to send Proxy Protocol headers
     * (e.g. "127.0.0.1", "10.0.0.0/8", "2001:db8::/32")
     */
    public List<String> proxyProtocolWhitelist;
    /**
     * Enable forced safety enchantments (up max lvl)
     */
    public boolean forcedSafetyEnchant;
    /**
     * Enable vibrant visuals
     * @since 1.21.80
     */
    public boolean enableVibrantVisuals;
    /**
     * Enable raytracing
     */
    public boolean enableRaytracing;

    /**
     * Enable NetEase Client Support
     */
    public boolean netEaseMode;
    /**
     * Only allow NetEase clients to join the server
     */
    public boolean onlyNetEaseMode;

    Server(final String filePath, String dataPath, String pluginPath, boolean loadPlugins, boolean debug) {
        Preconditions.checkState(instance == null, "Already initialized!");
        currentThread = Thread.currentThread(); // Saves the current thread instance as a reference, used in Server#isPrimaryThread()
        instance = this;

        this.filePath = filePath;
        if (!new File(dataPath + "worlds/").exists()) {
            new File(dataPath + "worlds/").mkdirs();
        }

        if (!new File(pluginPath).exists()) {
            new File(pluginPath).mkdirs();
        }

        this.dataPath = new File(dataPath).getAbsolutePath() + '/';
        this.pluginPath = new File(pluginPath).getAbsolutePath() + '/';

        this.playerDataSerializer = new DefaultPlayerDataSerializer(this);

        this.console = new NukkitConsole();
        this.consoleThread = new ConsoleThread();
        this.console.setExecutingCommands(true);

        // Load server.properties (standard MC settings)
        log.info("Loading server properties...");
        NukkitConsole.takeOverTerminalClose();
        this.properties = new Config(this.dataPath + "server.properties", Config.PROPERTIES, new ServerProperties());
        this.properties.setHeader("Nukkit-MOT Server Properties\n"
                + "For advanced settings, see nukkit-mot.yml\n"
                + "Documentation: https://www.nukkit-mot.com/docs/user-guide/server-config/server-properties");

        // Load nukkit-mot.yml (advanced MOT settings)
        log.info("Loading server configuration (YAML)...");
        this.loadServerConfig();

        ConfigMigration migration = new ConfigMigration(this.properties, this.serverConfig);
        if (migration.migrate()) {
            this.properties.save();
            this.saveServerConfig();
        }

        // Apply localized comments to server.properties based on its language setting
        this.applyPropertiesComments();

        if (!this.serverConfig.debugSettings().ansiTitle()) {
            Nukkit.TITLE = false;
        }

        int debugLvl = NukkitMath.clamp(this.serverConfig.debugSettings().debugLevel(), 1, 3);
        if (debug && debugLvl < 2) {
            debugLvl = 2;
        }
        Nukkit.DEBUG = debugLvl;

        this.loadSettings();

        this.automaticBugReport = this.serverConfig.debugSettings().automaticBugReport();
        if (this.automaticBugReport) {
            ExceptionHandler.registerExceptionHandler();
            Sentry.init(options -> {
                options.setDsn("https://b61b4bfc0057480e9644111aa4e78844@o4504694990700544.ingest.sentry.io/4504694992535552");
                options.setTracesSampleRate(0.8); //错误报告率 0.0-1.0
                options.setDebug(false);
                options.setTag("nukkit_version", Nukkit.VERSION);
                options.setTag("branch", Nukkit.getBranch());
            });
        }

        if (!new File(dataPath + "players/").exists() && this.shouldSavePlayerData) {
            new File(dataPath + "players/").mkdirs();
        }

        this.baseLang = new BaseLang(this.getPropertyString("language", "eng"));

        computeThreadPool = new ForkJoinPool(Math.min(0x7fff, Runtime.getRuntime().availableProcessors()), new ComputeThreadPoolThreadFactory(), null, false);

        String asyncWorkers = this.serverConfig.performanceSettings().asyncWorkers();
        int poolSize;
        if ("auto".equals(asyncWorkers)) {
            poolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1, 4);
        } else {
            try {
                poolSize = Integer.parseInt(asyncWorkers);
            } catch (NumberFormatException e) {
                poolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1, 4);
            }
        }

        ServerScheduler.WORKERS = poolSize;

        Zlib.setProvider(this.serverConfig.networkSettings().zlibProvider());

        this.scheduler = new ServerScheduler(this.serverConfig.performanceSettings().virtualThreads());
        log.info("Virtual threads for async tasks: {}", this.scheduler.isVirtualThreadsEnabled() ? "enabled" : "disabled");

        if (this.getPropertyBoolean("enable-rcon", false)) {
            try {
                String randomPassword = Base64.getEncoder().encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes()).substring(3, 13);
                this.rcon = new RCON(
                        this,
                        this.getPropertyString("rcon.password", randomPassword),
                        (this.getIp().isBlank()) ? "0.0.0.0" : this.getIp(),
                        this.getPropertyInt("rcon.port", 25575)
                );
            } catch (IllegalArgumentException e) {
                log.error(baseLang.translateString(e.getMessage(), e.getCause().getMessage()));
            }
        }

        this.entityMetadata = new EntityMetadataStore();
        this.playerMetadata = new PlayerMetadataStore();
        this.levelMetadata = new LevelMetadataStore();
        this.scoreboardManager = new ScoreboardManager(new JSONScoreboardStorage(this.dataPath + "scoreboard.json"));
        this.tickingAreaManager = new SimpleTickingAreaManager(new JSONTickingAreaStorage(this.dataPath + "worlds/"));

        this.operators = new Config(this.dataPath + "ops.txt", Config.ENUM);
        this.whitelist = new Config(this.dataPath + "white-list.txt", Config.ENUM);
        this.banByName = new BanList(this.dataPath + "banned-players.json");
        this.banByName.load();
        this.banByIP = new BanList(this.dataPath + "banned-ips.json");
        this.banByIP.load();

        this.maxPlayers = this.getPropertyInt("max-players", 50);
        this.setAutoSave(this.getPropertyBoolean("auto-save", true));

        this.autoCompaction = this.serverConfig.performanceSettings().levelAutoCompaction();
        this.autoCompactionTicks = Math.max(60 * 20, this.serverConfig.performanceSettings().levelAutoCompactionTicks());

        if (this.isHardcore && this.difficulty < 3) {
            this.setDifficulty(3);
        } else {
            this.setDifficulty(getDifficultyFromString(this.getPropertyString("difficulty", "2")));
        }

        org.apache.logging.log4j.Level currentLevel = Nukkit.getLogLevel();
        for (org.apache.logging.log4j.Level level : org.apache.logging.log4j.Level.values()) {
            if (level.intLevel() == (Nukkit.DEBUG + 3) * 100  && level.intLevel() > currentLevel.intLevel()) {
                Nukkit.setLogLevel(level);
                break;
            }
        }

        log.info("\u00A7b-- \u00A7cNukkit \u00A7dMOT \u00A7b--");

        this.consoleSender = new ConsoleCommandSender();
        this.commandMap = new SimpleCommandMap(this);

        registerEntities();
        registerProfessions();
        registerBlockEntities();

        Block.init();
        Enchantment.init();
        GlobalBlockPalette.init();
        RuntimeItems.init();
        Item.init();
        //noinspection ResultOfMethodCallIgnored
        EnumBiome.values();
        Effect.init();
        Potion.init();
        Attribute.init();
        DispenseBehaviorRegister.init();
        GlobalBlockPalette.setUseHashedBlockNetworkIds(this.serverConfig.customBlockSettings().useHashedBlockNetworkIds());
        CustomBlockManager.init(this);
        GlobalBlockPalette.getOrCreateRuntimeId(GameVersion.getLastVersion(), 0, 0);
        BiomeDefinitionListPacket.getCachedPacket(GameVersion.getLastVersion());
        //noinspection ResultOfMethodCallIgnored
        TrimDataPacket.getCachedPacket(GameVersion.getLastVersion());

        // Convert legacy data before plugins get the chance to mess with it
        try {
            nameLookup = Iq80DBFactory.factory.open(new File(dataPath, "players"), new Options()
                            .createIfMissing(true)
                            .compressionType(CompressionType.ZLIB_RAW));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (this.savePlayerDataByUuid) {
            convertLegacyPlayerData();
            // Before the network opens: a login trimmed to "Name" must find the profile that a
            // padded "Name " used to save under, and never an older twin of it.
            if (this.spaceMode == 2) {
                // Replace mode turns "Name " into "Name_" and does not trim, so padded keys are
                // not twins of the trimmed name there.
                log.warn("Not folding player data saved under padded names: space-name-mode is replace");
            } else {
                try {
                    PlayerNameEdgeWhitespaceMigration.Report fold = PlayerNameEdgeWhitespaceMigration.run(nameLookup,
                            new File(dataPath, "players"),
                            new File(dataPath, PlayerNameEdgeWhitespaceMigration.QUARANTINE_DIRECTORY));
                    this.profileFoldBlockedNames = fold.blocked();
                } catch (RuntimeException failure) {
                    // Each name is handled inside the run; this only keeps an unexpected fault from
                    // stopping the whole server.
                    log.error("Could not fold player data saved under padded names", failure);
                }
            }
        }

        this.serverID = UUID.randomUUID();

        this.craftingManager = new CraftingManager();
        ResourcePackMigration.migrate(new File(Nukkit.DATA_PATH));
        // Registration order is the stack order among packs of equal priority: resource packs first,
        // then behaviour packs, then packs shipped inside plugin jars.
        Set<ResourcePackLoader> packLoaders = new LinkedHashSet<>();
        packLoaders.add(new ZippedResourcePackLoader(new File(Nukkit.DATA_PATH, "resource_packs")));
        packLoaders.add(new ZippedBehaviourPackLoader(new File(Nukkit.DATA_PATH, "behaviour_packs")));
        packLoaders.add(new JarPluginResourcePackLoader(new File(this.pluginPath)));
        this.resourcePackManager = new ResourcePackManager(packLoaders);

        this.pluginManager = new PluginManager(this, this.commandMap);
        this.pluginManager.subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this.consoleSender);

        this.pluginManager.registerInterface(JavaPluginLoader.class);

        this.queryRegenerateEvent = new QueryRegenerateEvent(this, 5);

        log.info(this.baseLang.translateString("nukkit.server.networkStart", new String[]{this.getIp().isBlank() ? "0.0.0.0" : this.getIp(), String.valueOf(this.getPort())}));
        if (this.ipv6Enabled) {
            // IPv6 地址用方括号包裹，与 IPv4 行复用同一本地化文案以保持风格统一
            String ipv6Host = "[" + (this.ipv6Address.isBlank() ? "::" : this.ipv6Address) + "]";
            log.info(this.baseLang.translateString("nukkit.server.networkStart", new String[]{ipv6Host, String.valueOf(this.ipv6Port)}));
        }
        this.network = new Network(this);
        this.network.setName(this.getMotd());
        this.network.setSubName(this.getSubMotd());
        RakNetInterface rakNetInterface = new RakNetInterface(this);
        this.network.registerInterface(rakNetInterface);

        // NetherNet (WebRTC) 与 RakNet 并行：旧客户端走 RakNet，受限网络的 1.21.90+ 客户端走 HTTP 信令 + WebRTC；
        // server-udp-ports 把媒体映射到某个 RakNet 监听端口（server-port/IPv6）时，媒体经监听 socket 进程内中继
        // Runs alongside RakNet: legacy clients keep RakNet, restricted-network 1.21.90+ clients join over WebRTC;
        // with server-udp-ports mapping media onto a RakNet listener (server-port/IPv6) it is relayed in-process
        NetherNetSettings netherNetSettings = this.serverConfig != null
                ? this.serverConfig.networkSettings().netherNetSettings() : null;
        if (netherNetSettings != null && netherNetSettings.enabled()) {
            try {
                this.network.registerInterface(new NetherNetInterface(this, netherNetSettings, rakNetInterface));
            } catch (Throwable t) {
                // NetherNet 启动失败（原生库缺失、端口配置错误等）按致命错误处理：提示根因与关闭方式后中止启动，
                // 不带残缺的网络栈继续运行（与下方 defaultLevel 加载失败同路）
                // A NetherNet startup failure (missing native lib, bad port config, ...) is fatal:
                // report the root cause and the disable hint, then abort like a defaultLevel load failure
                Throwable cause = t;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                }
                this.getLogger().emergency(this.baseLang.translateString("nukkit.nethernet.startFailed",
                        cause.getMessage() != null ? cause.getMessage() : cause.toString()));
                this.getLogger().emergency(this.baseLang.translateString("nukkit.nethernet.startFailed.hint"));
                log.debug("NetherNet startup aborted", t);
                this.forceShutdown();
                return;
            }
        }

        EntityProperty.init();

        this.pluginManager.loadInternalPlugin();
        if (loadPlugins) {
            this.pluginManager.loadPlugins(this.pluginPath);
            if (this.enableSpark) {
                SparkInstaller.initSpark(this);
            }
            this.enablePlugins(PluginLoadOrder.STARTUP);
        }

        try {
            if (CustomBlockManager.get().closeRegistry()) {
                for (RuntimeItemMapping runtimeItemMapping : RuntimeItems.values()) {
                    runtimeItemMapping.generatePalette();
                }
            }

            Item.initCreativeItems();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to init custom blocks", e);
        }

        LevelProviderManager.addProvider(this, Anvil.class);
        LevelProviderManager.addProvider(this, LevelDBProvider.class);

        Generator.addGenerator(Flat.class, "flat", Generator.TYPE_FLAT);
        Generator.addGenerator(Normal.class, "normal", Generator.TYPE_INFINITE);
        Generator.addGenerator(Normal.class, "default", Generator.TYPE_INFINITE);
        Generator.addGenerator(OldNormal.class, "oldnormal", Generator.TYPE_INFINITE);
        Generator.addGenerator(Nether.class, "nether", Generator.TYPE_NETHER);
        Generator.addGenerator(End.class, "the_end", Generator.TYPE_THE_END);
        Generator.addGenerator(Void.class, "void", Generator.TYPE_VOID);

        if (this.defaultLevel == null) {
            String defaultName = this.getPropertyString("level-name", "world");
            if (defaultName == null || defaultName.trim().isEmpty()) {
                this.getLogger().warning("level-name cannot be null, using default");
                defaultName = "world";
                this.setPropertyString("level-name", defaultName);
            }

            if (!this.loadLevel(defaultName)) {
                long seed;
                String seedString = this.getPropertyString("level-seed", "");
                if (seedString.isEmpty()) {
                    seedString = String.valueOf(System.currentTimeMillis());
                }
                try {
                    seed = Long.parseLong(seedString);
                } catch (NumberFormatException e) {
                    seed = seedString.hashCode();
                }
                this.generateLevel(defaultName, seed == 0 ? System.currentTimeMillis() : seed);
            }

            this.setDefaultLevel(this.getLevelByName(defaultName));
        }

        // Save server configuration
        this.saveServerConfig();

        if (this.defaultLevel == null) {
            this.getLogger().emergency(this.baseLang.translateString("nukkit.level.defaultError"));
            this.forceShutdown();
            return;
        }

        for (Map.Entry<Integer, Level> entry : this.getLevels().entrySet()) {
            Level level = entry.getValue();
            this.getLogger().debug("Preparing spawn region for level " + level.getName());
            Position spawn = level.getSpawnLocation();
            level.populateChunk(spawn.getChunkX(), spawn.getChunkZ(), true);
        }

        // Load levels
        if (this.serverConfig.worldSettings().loadAllWorlds()) {
            try {
                for (File fs : new File(new File("").getCanonicalPath() + "/worlds/").listFiles()) {
                    if ((fs.isDirectory() && !this.isLevelLoaded(fs.getName()))) {
                        this.loadLevel(fs.getName());
                    }
                }
            } catch (Exception e) {
                this.getLogger().error("Unable to load levels", e);
            }
        }

        // Load custom worlds from config
        this.loadCustomWorlds();

        try {
            EnumLevel.initLevels();
        } catch (Exception e) {
            this.getLogger().error("Unable to init levels", e);
        }

        if (loadPlugins) {
            this.enablePlugins(PluginLoadOrder.POSTWORLD);
        }

        EntityProperty.buildPacket();
        EntityProperty.buildPlayerProperty();

        if (this.serverConfig.performanceSettings().threadWatchdog()) {
            this.watchdog = new Watchdog(this, this.serverConfig.performanceSettings().threadWatchdogTick());
            this.watchdog.start();
        }

        disabledSpawnWorlds.clear();
        disabledSpawnWorlds.addAll(this.serverConfig.worldSettings().entitySpawningDisabledWorlds());

        nonAutoSaveWorlds.clear();
        nonAutoSaveWorlds.addAll(this.serverConfig.worldSettings().autoSaveDisabledWorlds());

        if (this.serverConfig.entitySettings().autoSpawnTask()) {
            int spawnerTicks = Math.max(this.serverConfig.entitySettings().ticksPerSpawns(), 2) >> 1; // Run the spawner on 2x speed but spawn only either monsters or animals
            this.spawnerTask = new SpawnerTask(spawnerTicks);
            // Every tick, one spawner at a time: see SpawnerTask#tick
            this.scheduler.scheduleDelayedRepeatingTask(InternalPlugin.INSTANCE, this.spawnerTask::tick, 1, 1);
        }

        if (this.serverConfig.debugSettings().bstatsMetrics()) {
            this.nukkitMetrics = new NukkitMetrics(this);
        }

        // 触发一次，加载JwtConsumerHolder
        if (this.xboxAuth) {
            try {
                EncryptionUtils.validateToken(AuthType.FULL, "");
            } catch (InvalidJwtException ignored) {

            } catch (Exception e) {
                this.getLogger().error("EncryptionUtils initialization error, xbox verification will not work!", e);
            }
        }

        // Check for updates
        CompletableFuture.runAsync(() -> {
            try {
                URLConnection request = URI.create(Nukkit.BRANCH).toURL().openConnection();
                request.connect();
                InputStreamReader content = new InputStreamReader((InputStream) request.getContent());
                String latest = "git-" + JsonParser.parseReader(content).getAsJsonObject().get("sha").getAsString().substring(0, 7);
                content.close();

                boolean isMaster = Nukkit.getBranch().equals("master");
                if (isMaster && !this.getNukkitVersion().equals(latest) && !this.getNukkitVersion().equals("git-null")) {
                    this.getLogger().info("§c[Nukkit-MOT][Update] §eThere is a new build of §cNukkit§3-§dMOT §eavailable! Current: " + this.getNukkitVersion() + " Latest: " + latest);
                    this.getLogger().info("§c[Nukkit-MOT][Update] §eYou can download the latest build from https://github.com/MemoriesOfTime/Nukkit-MOT/");
                } else if (!isMaster) {
                    this.getLogger().warning("§c[Nukkit-MOT] §eYou are running a dev build! Do not use in production! Branch: " + Nukkit.getBranch());
                }
            } catch (Exception ignore) {
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::runShutdownHook, "Server shutdown hook"));

        this.start();
    }

    public int broadcastMessage(String message) {
        return this.broadcast(message, BROADCAST_CHANNEL_USERS);
    }

    public int broadcastMessage(TextContainer message) {
        return this.broadcast(message, BROADCAST_CHANNEL_USERS);
    }

    public int broadcastMessage(String message, CommandSender[] recipients) {
        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }

        return recipients.length;
    }

    public int broadcastMessage(String message, Collection<? extends CommandSender> recipients) {
        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }

        return recipients.size();
    }

    public int broadcastMessage(TextContainer message, Collection<? extends CommandSender> recipients) {
        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }

        return recipients.size();
    }

    /**
     * 收集订阅了任一指定权限频道的去重 {@link CommandSender} 集合。
     * <p>
     * Collect the de-duplicated {@link CommandSender}s subscribed to at least one given permission channel.
     *
     * @param permissions {@code ;} 分隔的权限频道列表 a {@code ;}-separated permission channel list
     */
    private Set<CommandSender> getBroadcastRecipients(String permissions) {
        Set<CommandSender> recipients = new HashSet<>();
        for (String permission : permissions.split(";")) {
            for (Permissible permissible : this.pluginManager.getPermissionSubscriptions(permission)) {
                if (permissible instanceof CommandSender sender && permissible.hasPermission(permission)) {
                    recipients.add(sender);
                }
            }
        }
        return recipients;
    }

    /**
     * 向全体用户频道广播标题（默认淡入/停留/淡出 20/20/5）。
     * <p>
     * Broadcast a title with subtitle to the default user channel (default timings 20/20/5).
     *
     * @return 实际显示标题的玩家数 number of players that displayed the title
     */
    public int broadcastTitle(String title, String subtitle) {
        return this.broadcastTitle(title, subtitle, BROADCAST_CHANNEL_USERS);
    }

    /**
     * 按权限频道广播标题。Broadcast a title to the subscribers of the given permission channels.
     *
     * @param permissions {@code ;} 分隔的权限频道列表 {@code ;}-separated permission channels
     * @return 实际显示标题的玩家数 number of players that displayed the title
     */
    public int broadcastTitle(String title, String subtitle, String permissions) {
        return this.broadcastTitle(title, subtitle, getBroadcastRecipients(permissions));
    }

    /**
     * 向指定接收者集合广播标题（默认淡入/停留/淡出 20/20/5）。
     * <p>
     * Broadcast a title to the given recipients with default timings; only {@link Player} recipients display it.
     *
     * @return 实际显示标题的玩家数 number of players that displayed the title
     */
    public int broadcastTitle(String title, String subtitle, Collection<? extends CommandSender> recipients) {
        return this.broadcastTitle(title, subtitle, 20, 20, 5, recipients);
    }

    /**
     * 向全体用户频道广播标题，可自定义淡入/停留/淡出（tick）。
     * <p>
     * Broadcast a title with custom fade-in/stay/fade-out (in ticks) to the default user channel.
     */
    public int broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        return this.broadcastTitle(title, subtitle, fadeIn, stay, fadeOut, BROADCAST_CHANNEL_USERS);
    }

    /**
     * 按权限频道广播标题，可自定义淡入/停留/淡出（tick）。
     * <p>
     * Broadcast a title with custom timings to the subscribers of the given permission channels.
     */
    public int broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut, String permissions) {
        return this.broadcastTitle(title, subtitle, fadeIn, stay, fadeOut, getBroadcastRecipients(permissions));
    }

    /**
     * 向指定接收者集合广播标题，可自定义淡入/停留/淡出（tick）；只有 {@link Player} 会显示。
     * <p>
     * Broadcast a title with custom timings to the given recipients; only {@link Player} recipients display it.
     *
     * @return 实际显示标题的玩家数 number of players that displayed the title
     */
    public int broadcastTitle(String title, String subtitle, int fadeIn, int stay, int fadeOut, Collection<? extends CommandSender> recipients) {
        int count = 0;
        for (CommandSender recipient : recipients) {
            if (recipient instanceof Player player) {
                player.sendTitle(title, subtitle, fadeIn, stay, fadeOut);
                count++;
            }
        }
        return count;
    }

    /**
     * 向全体用户频道广播 Tip 消息。
     * <p>
     * Broadcast a tip to the default user channel; only {@link Player} recipients display it.
     *
     * @return 实际显示 Tip 的玩家数 number of players that displayed the tip
     */
    public int broadcastTip(String message) {
        return this.broadcastTip(message, BROADCAST_CHANNEL_USERS);
    }

    /**
     * 按权限频道广播 Tip 消息。Broadcast a tip to the subscribers of the given permission channels.
     *
     * @param permissions {@code ;} 分隔的权限频道列表 {@code ;}-separated permission channels
     * @return 实际显示 Tip 的玩家数 number of players that displayed the tip
     */
    public int broadcastTip(String message, String permissions) {
        return this.broadcastTip(message, getBroadcastRecipients(permissions));
    }

    /**
     * 向指定接收者集合广播 Tip 消息；只有 {@link Player} 会显示。
     * <p>
     * Broadcast a tip to the given recipients; only {@link Player} recipients display it.
     *
     * @return 实际显示 Tip 的玩家数 number of players that displayed the tip
     */
    public int broadcastTip(String message, Collection<? extends CommandSender> recipients) {
        int count = 0;
        for (CommandSender recipient : recipients) {
            if (recipient instanceof Player player) {
                player.sendTip(message);
                count++;
            }
        }
        return count;
    }

    /**
     * 向全体用户频道广播 Action Bar 消息（默认淡入/停留/淡出 1/0/1）。
     * <p>
     * Broadcast an action bar to the default user channel (default timings 1/0/1).
     *
     * @return 实际显示 Action Bar 的玩家数 number of players that displayed the action bar
     */
    public int broadcastActionBar(String message) {
        return this.broadcastActionBar(message, BROADCAST_CHANNEL_USERS);
    }

    /**
     * 按权限频道广播 Action Bar 消息。Broadcast an action bar to the subscribers of the given permission channels.
     *
     * @param permissions {@code ;} 分隔的权限频道列表 {@code ;}-separated permission channels
     * @return 实际显示 Action Bar 的玩家数 number of players that displayed the action bar
     */
    public int broadcastActionBar(String message, String permissions) {
        return this.broadcastActionBar(message, getBroadcastRecipients(permissions));
    }

    /**
     * 向指定接收者集合广播 Action Bar 消息（默认淡入/停留/淡出 1/0/1）；只有 {@link Player} 会显示。
     * <p>
     * Broadcast an action bar to the given recipients with default timings; only {@link Player} recipients display it.
     *
     * @return 实际显示 Action Bar 的玩家数 number of players that displayed the action bar
     */
    public int broadcastActionBar(String message, Collection<? extends CommandSender> recipients) {
        return this.broadcastActionBar(message, 1, 0, 1, recipients);
    }

    /**
     * 向全体用户频道广播 Action Bar 消息，可自定义淡入/停留/淡出（tick）。
     * <p>
     * Broadcast an action bar with custom fade-in/duration/fade-out (in ticks) to the default user channel.
     */
    public int broadcastActionBar(String message, int fadeIn, int duration, int fadeOut) {
        return this.broadcastActionBar(message, fadeIn, duration, fadeOut, BROADCAST_CHANNEL_USERS);
    }

    /**
     * 按权限频道广播 Action Bar 消息，可自定义淡入/停留/淡出（tick）。
     * <p>
     * Broadcast an action bar with custom timings to the subscribers of the given permission channels.
     */
    public int broadcastActionBar(String message, int fadeIn, int duration, int fadeOut, String permissions) {
        return this.broadcastActionBar(message, fadeIn, duration, fadeOut, getBroadcastRecipients(permissions));
    }

    /**
     * 向指定接收者集合广播 Action Bar 消息，可自定义淡入/停留/淡出（tick）；只有 {@link Player} 会显示。
     * <p>
     * Broadcast an action bar with custom timings to the given recipients; only {@link Player} recipients display it.
     *
     * @return 实际显示 Action Bar 的玩家数 number of players that displayed the action bar
     */
    public int broadcastActionBar(String message, int fadeIn, int duration, int fadeOut, Collection<? extends CommandSender> recipients) {
        int count = 0;
        for (CommandSender recipient : recipients) {
            if (recipient instanceof Player player) {
                player.sendActionBar(message, fadeIn, duration, fadeOut);
                count++;
            }
        }
        return count;
    }

    public int broadcast(String message, String permissions) {
        Set<CommandSender> recipients = getBroadcastRecipients(permissions);
        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }
        return recipients.size();
    }

    public int broadcast(TextContainer message, String permissions) {
        Set<CommandSender> recipients = getBroadcastRecipients(permissions);
        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }
        return recipients.size();
    }


    public static void broadcastPacket(Collection<Player> players, DataPacket packet) {
        for (Player player : players) {
            player.dataPacket(packet);
        }
    }

    public static void broadcastPacket(Player[] players, DataPacket packet) {
        for (Player player : players) {
            player.dataPacket(packet);
        }
    }

    public static void broadcastPackets(Player[] players, DataPacket[] packets) {
        for (Player player : players) {
            for (DataPacket packet : packets) {
                player.dataPacket(packet);
            }
        }
    }

    @Deprecated
    public void batchPackets(Player[] players, DataPacket[] packets) {
        if (players == null || packets == null || players.length == 0 || packets.length == 0) {
            return;
        }

        if (this.callBatchPkEv) {
            BatchPacketsEvent ev = new BatchPacketsEvent(players, packets);
            ev.call();
            if (ev.isCancelled()) {
                return;
            }
        }

        for (DataPacket packet : packets) {
            packet.isEncoded = false; // prevent plugins from being encoded in advance
            for (Player player : players) {
                player.dataPacket(packet);
            }
        }
    }

    @Deprecated(forRemoval = true)
    public void batchPackets(Player[] players, DataPacket[] packets, boolean forceSync) {
        this.batchPackets(players, packets);
    }

    public void enablePlugins(PluginLoadOrder type) {
        for (Plugin plugin : new ArrayList<>(this.pluginManager.getPlugins().values())) {
            if (!plugin.isEnabled() && type == plugin.getDescription().getOrder()) {
                this.enablePlugin(plugin);
            }
        }

        if (type == PluginLoadOrder.POSTWORLD) {
            DefaultPermissions.registerCorePermissions();
        }
    }

    public void enablePlugin(Plugin plugin) {
        this.pluginManager.enablePlugin(plugin);
    }

    public void disablePlugins() {
        this.pluginManager.disablePlugins();
    }

    public boolean dispatchCommand(CommandSender sender, String commandLine) throws ServerException {
        // First we need to check if this command is on the main thread or not, if not, warn the user
        if (!this.isPrimaryThread()) {
            getLogger().warning("Command Dispatched Async: " + commandLine);
        }
        if (sender == null) {
            throw new ServerException("CommandSender is not valid");
        }

        return this.commandMap.dispatch(sender, commandLine);
    }

    public ConsoleCommandSender getConsoleSender() {
        return consoleSender;
    }

    public IScoreboardManager getScoreboardManager() {
        return scoreboardManager;
    }

    public void reload() {
        log.info("Reloading...");

        log.info("Saving levels...");

        for (Level level : this.levelArray) {
            level.save();
        }

        this.pluginManager.clearPlugins();
        this.commandMap.clearCommands();

        // Reload server.properties
        log.info("Reloading server properties...");
        this.properties.reload();
        this.applyPropertiesComments();

        // Reload nukkit-mot.yml
        log.info("Reloading server configuration (YAML)...");
        this.loadServerConfig(false);

        this.maxPlayers = this.getPropertyInt("max-players", 50);

        if (this.isHardcore && this.difficulty < 3) {
            this.setDifficulty(3);
        }

        this.loadSettings();

        this.banByIP.load();
        this.banByName.load();
        this.reloadWhitelist();
        this.operators.reload();

        for (BanEntry entry : this.banByIP.getEntires().values()) {
            try {
                this.network.blockAddress(InetAddress.getByName(entry.getName()), -1);
            } catch (UnknownHostException ignore) {}
        }

        this.pluginManager.registerInterface(JavaPluginLoader.class);
        this.pluginManager.loadPlugins(this.pluginPath);
        if (this.enableSpark) {
            SparkInstaller.initSpark(this);
        }
        this.enablePlugins(PluginLoadOrder.STARTUP);
        this.enablePlugins(PluginLoadOrder.POSTWORLD);
    }

    public void shutdown() {
        isRunning.compareAndSet(true, false);
    }

    /**
     * 懒建取池:热重载打开 async-chunks 后新建世界仍能取到;关闭时为 null。
     * <p>
     * Lazily created so levels made after a reload turns async-chunks on still get a pool; null while it is off.
     */
    public ExecutorService getChunkSerializationPool() {
        ForkJoinPool pool = this.chunkSerializationPool;
        if (pool != null || !this.asyncChunkSending) {
            return pool;
        }
        synchronized (this) {
            if (this.chunkSerializationPool == null) {
                this.chunkSerializationPool = createWorldIOPool(
                        Math.max(2, Math.min(8, Runtime.getRuntime().availableProcessors() - 1)), "ChunkSerialization");
            }
            return this.chunkSerializationPool;
        }
    }

    /**
     * 懒建,语义同上 / Lazily created; same semantics as above.
     */
    public ExecutorService getWorldChunkLoadPool() {
        ForkJoinPool pool = this.worldChunkLoadPool;
        if (pool != null || !this.asyncChunkSending) {
            return pool;
        }
        synchronized (this) {
            if (this.worldChunkLoadPool == null) {
                // I/O 密集只取半数核,留核给主线程与序列化 lane / I/O-bound: half the cores, saving the rest for the main thread and serialization
                this.worldChunkLoadPool = createWorldIOPool(
                        Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() >> 1)), "ChunkLoad");
            }
            return this.worldChunkLoadPool;
        }
    }

    /**
     * 弹性共享 lane:asyncMode 的 ForkJoinPool,线程按负载 0→上限 增长、空闲自动收缩;
     * daemon 保证卡死的原生 I/O 不阻塞进程退出。上限按核数自动确定(弹性池错配代价低,不设配置项)。
     * <p>
     * Elastic shared lane: an async-mode ForkJoinPool whose workers grow 0→parallelism with
     * load and idle out when quiet; daemon threads keep a wedged native task from blocking exit.
     * The cap is auto-sized from the core count (an elastic mis-cap costs little; no config knob).
     */
    static ForkJoinPool createWorldIOPool(int parallelism, String laneName) {
        AtomicInteger workerCount = new AtomicInteger();
        return new ForkJoinPool(parallelism,
                pool -> {
                    ForkJoinWorkerThread worker = new ForkJoinWorkerThread(pool) {};
                    worker.setName(laneName + " Worker-" + workerCount.incrementAndGet());
                    worker.setDaemon(true);
                    return worker;
                },
                (thread, ex) -> log.error("Exception in " + thread.getName(), ex),
                true);
    }

    /**
     * 有界关闭共享 lane:先优雅排空,超时 shutdownNow 再等一次;卡在不可中断原生 I/O 的
     * 任务由 daemon 属性兜底,不阻塞进程退出。
     * <p>
     * Bounded shutdown of a shared lane: graceful drain first, then shutdownNow with one more
     * bounded wait; a task wedged in uninterruptible native I/O is covered by the daemon flag.
     */
    private static void shutdownWorldIOPool(ExecutorService pool, String laneName) {
        if (pool == null) {
            return;
        }
        pool.shutdown();
        try {
            if (!pool.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("{} pool did not terminate in time, forcing shutdown", laneName);
                pool.shutdownNow();
                if (!pool.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("{} pool did not terminate even after forced shutdown; abandoning drain", laneName);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            pool.shutdownNow();
        }
    }

    public void forceShutdown() {
        this.forceShutdown(this.getPropertyString("shutdown-message", "§cServer closed").replace("§n", "\n"));
    }

    /** A foreign caller requests the complete owner-thread sequence, before any dependency teardown. */
    public void forceShutdown(String reason) {
        if (!this.requestShutdown(reason)) return;
        if (!this.isPrimaryThread() && this.currentThread.getState() != Thread.State.TERMINATED) return;
        synchronized (this) {
            if (this.hasStopped || this.shutdownTimedOut) return;
            if (this.shutdownAdvancing) {
                this.shutdownAdvanceRequested = true;
                return;
            }
            this.shutdownAdvancing = true;
        }
        long shutdownStart = System.currentTimeMillis();
        try {
            if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity,
                    this::resumeShutdown)) return;
            this.drainPlayerCloses();
            if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity,
                    this::resumeShutdown)) return;
            reason = this.requestedShutdownReason;
            this.touchShutdownProgress();
            if (!this.shutdownStopEventDispatched) {
                this.shutdownStopEventDispatched = true;
                pluginManager.callEvent(new ServerStopEvent());
            }
            // A stop listener can retain a new native claim; resume without repeating its event.
            if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity,
                    this::resumeShutdown)) return;
            if (this.shutdownTimedOut) return;
            if (!this.shutdownPlayerClosingStarted) {
                this.shutdownPlayerClosingStarted = true;

            if (this.holdWorldSave) {
                this.getLogger().warning("World save hold was not released! Any backup currently being taken may be invalid");
            }

            if (this.rcon != null) {
                this.getLogger().debug("Closing RCON...");
                this.rcon.close();
            }

            LoginChainVerifier.shared().shutdown();
            }
            this.getLogger().debug("Disconnecting all players...");
            for (Player player : new ArrayList<>(this.players.values())) {
                if (this.shutdownTimedOut) return;
                player.close(player.getLeaveMessage(), reason);
                this.touchShutdownProgress();
            }

            // Quit/serialize listeners can retain another claim; pending players resume their captured close phase.
            if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity,
                    this::resumeShutdown)) return;
            if (this.shutdownTimedOut) return;
            // An unadmitted final snapshot is still owned by its Player, not by the IO queue.
            while (this.players.values().stream().anyMatch(Player::hasPendingProfileRetirement)) {
                drainProfileIO(); drainPlayerCloses();
                if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity, this::resumeShutdown)) return;
                if (System.nanoTime() >= this.shutdownDeadlineNanos || Thread.currentThread().isInterrupted()) {
                    this.expireShutdown(); return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            }
            // Only admitted profile continuations run here; plugin heartbeat work stays untouched.
            while (!profileQueue().flushAll().toCompletableFuture().isDone()) {
                drainProfileIO();
                if (System.nanoTime() >= this.shutdownDeadlineNanos || Thread.currentThread().isInterrupted()) {
                    this.expireShutdown(); return;
                }
                java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(1));
            }
            drainProfileIO();
            if (!profileQueue().close(java.time.Duration.ofNanos(Math.max(0, this.shutdownDeadlineNanos - System.nanoTime())))) {
                this.expireShutdown();
                return;
            }
            this.hasStopped = true;
            this.getLogger().debug("Disabling all plugins...");
            this.disablePlugins();
            this.touchShutdownProgress();

            this.getLogger().debug("Unloading all levels...");
            for (Level level : this.levelArray) {
                this.unloadLevel(level, true);
                this.touchShutdownProgress();
            }

            this.getLogger().debug("Removing event handlers...");
            HandlerList.unregisterAll();

            this.getLogger().debug("Stopping all tasks...");
            this.scheduler.cancelAllTasks();
            this.scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);
            this.scheduler.shutdown();

            this.getLogger().debug("Shutting down world I/O pools...");
            // 世界只排空各自 in-flight,池在此统一关闭 / Levels drain their own in-flight work; the shared pools close here
            shutdownWorldIOPool(this.chunkSerializationPool, "chunk serialization");
            shutdownWorldIOPool(this.worldChunkLoadPool, "world I/O");

            this.getLogger().debug("Closing console...");
            this.consoleThread.interrupt();

            this.getLogger().debug("Stopping network interfaces...");
            // unregisterInterface 会边迭代边从 getInterfaces() 的活集合删除，拷贝防 CME
            // unregisterInterface mutates the live set mid-iteration; copy to avoid CME
            for (SourceInterface interfaz : new ArrayList<>(this.network.getInterfaces())) {
                interfaz.shutdown();
                this.network.unregisterInterface(interfaz);
            }

            if (nameLookup != null) {
                this.getLogger().debug("Closing name lookup DB...");
                nameLookup.close();
            }

            if (this.nukkitMetrics != null) {
                this.getLogger().debug("Stopping metrics...");
                this.nukkitMetrics.shutdown();
            }

            if (this.watchdog != null) {
                this.getLogger().debug("Stopping Watchdog...");
                this.watchdog.kill();
            }

            this.getLogger().info(this.baseLang.translateString("nukkit.server.stopped",
                    String.valueOf(NukkitMath.round((System.currentTimeMillis() - shutdownStart) / 1000d, 1))));
            this.shutdownCompletion.complete(null);
        } catch (Exception e) {
            this.hasStopped = true;
            this.shutdownCompletion.completeExceptionally(e);
            log.fatal("Exception happened while shutting down, exiting the process", e);
            if (Thread.currentThread() != this.shutdownHookThread && Thread.currentThread() != this.shutdownHookWorker) {
                Nukkit.shutdownLogging();
                System.exit(1);
            }
        } catch (Error failure) {
            this.hasStopped = true;
            this.shutdownCompletion.completeExceptionally(failure);
            throw failure;
        } finally {
            boolean retry;
            synchronized (this) {
                this.shutdownAdvancing = false;
                retry = this.shutdownAdvanceRequested && !this.hasStopped && !this.shutdownTimedOut;
                this.shutdownAdvanceRequested = false;
            }
            if (retry) this.forceShutdown(this.requestedShutdownReason);
        }
    }

    private boolean requestShutdown(String reason) {
        synchronized (this) {
            if (this.hasStopped || this.shutdownTimedOut) return false;
            if (this.requestedShutdownReason == null) {
                this.requestedShutdownReason = reason;
                this.shutdownDeadlineNanos = System.nanoTime() + TimeUnit.SECONDS.toNanos(30);
                this.touchShutdownProgress();
            }
        }
        this.isRunning.set(false);
        return true;
    }

    private void resumeShutdown() {
        if (this.shutdownHookThread != null && this.currentThread.getState() == Thread.State.TERMINATED)
            this.startShutdownHookFallback();
        else this.forceShutdown(this.requestedShutdownReason);
    }

    private void startShutdownHookFallback() {
        if (this.currentThread.getState() != Thread.State.TERMINATED || this.shutdownTimedOut) return;
        if (InventoryMutationGuard.deferUntilIdle(this.deferredShutdownIdentity, this::startShutdownHookFallback)) return;
        Thread worker;
        synchronized (this) {
            if (this.shutdownHookWorker != null || this.shutdownCompletion.isDone() || this.shutdownTimedOut) return;
            worker = new Thread(() -> {
                try { this.completeShutdownOnOwner(); }
                finally { this.shutdownHookWorker = null; }
            }, "Terminated owner shutdown");
            worker.setDaemon(true);
            this.shutdownHookWorker = worker;
        }
        worker.start();
    }

    private void expireShutdown() {
        synchronized (this) {
            if (this.shutdownCompletion.isDone()) return;
            this.shutdownTimedOut = true;
            this.shutdownCompletion.completeExceptionally(new TimeoutException("Shutdown owner completion deadline"));
        }
    }

    /** Only shutdown continuations run here; plugins, levels and scheduled work remain intact while claimed. */
    private boolean completeShutdownOnOwner() {
        while (!this.shutdownCompletion.isDone()) {
            if (System.nanoTime() >= this.shutdownDeadlineNanos || Thread.currentThread().isInterrupted()) {
                this.expireShutdown();
                return false;
            }
            this.forceShutdown(this.requestedShutdownReason);
            if (this.shutdownCompletion.isDone()) break;
            long remaining = this.shutdownDeadlineNanos - System.nanoTime();
            if (remaining <= 0) { this.expireShutdown(); return false; }
            try {
                this.shutdownCompletion.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
            } catch (TimeoutException retry) {
                // A foreign release may only request another owner continuation. Recheck on this owner.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt(); this.expireShutdown(); return false;
            } catch (ExecutionException failure) { return false; }
        }
        return !this.shutdownCompletion.isCompletedExceptionally();
    }

    /** Internal: off-thread disconnects remain pending until their inventory owner can close them. */
    public void deferPlayerClose(Player player, Runnable close) {
        this.pendingPlayerCloses.putIfAbsent(player, close);
    }

    private void drainPlayerCloses() {
        Throwable failure = null;
        for (var entry : new ArrayList<>(this.pendingPlayerCloses.entrySet())) {
            if (!this.pendingPlayerCloses.remove(entry.getKey(), entry.getValue())) continue;
            try { entry.getValue().run(); } catch (RuntimeException | Error closeFailure) {
                if (failure == null) failure = closeFailure;
                else if (failure != closeFailure) failure.addSuppressed(closeFailure);
            }
        }
        if (failure instanceof RuntimeException exception) throw exception;
        if (failure instanceof Error error) throw error;
    }

    void runShutdownHook() {
        this.shutdownHookThread = Thread.currentThread();
        try {
            if (this.currentThread.getState() == Thread.State.TERMINATED) {
                if (this.requestShutdown(this.getPropertyString("shutdown-message", "§cServer closed").replace("§n", "\n")))
                    this.startShutdownHookFallback();
            } else this.forceShutdown();
        } finally {
            if (!this.awaitShutdownCompletion(30_000)) {
                log.warn("Shutdown owner did not complete before the hook deadline; only previously admitted profiles are available");
            }
            this.shutdownHookThread = null;
        }
    }

    boolean awaitShutdownCompletion(long timeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(0, timeoutMillis));
        if (this.shutdownDeadlineNanos != 0) deadline = Math.min(deadline, this.shutdownDeadlineNanos);
        while (!this.shutdownCompletion.isDone()) {
            // Runtime.exit holds the owner until hooks finish. Waiting for it would deadlock the hook.
            for (StackTraceElement frame : this.currentThread.getStackTrace()) {
                if ((frame.getClassName().equals("java.lang.Shutdown")
                        && (frame.getMethodName().equals("exit") || frame.getMethodName().equals("shutdown")))
                        || (frame.getClassName().equals("java.lang.Runtime") && frame.getMethodName().equals("exit"))) return false;
            }
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) { this.expireShutdown(); return false; }
            try {
                this.shutdownCompletion.get(Math.min(remaining, TimeUnit.MILLISECONDS.toNanos(100)), TimeUnit.NANOSECONDS);
            } catch (TimeoutException retry) {
                // Recheck the owner exit boundary and the finite deadline.
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                this.expireShutdown();
                return false;
            } catch (ExecutionException failed) {
                return false;
            }
        }
        return !this.shutdownCompletion.isCompletedExceptionally();
    }

    public void start() {
        if (this.getPropertyBoolean("enable-query", true)) {
            this.queryHandler = new QueryHandler();
        }

        for (BanEntry entry : this.banByIP.getEntires().values()) {
            try {
                this.network.blockAddress(InetAddress.getByName(entry.getName()), -1);
            } catch (UnknownHostException ignore) {}
        }

        this.tickCounter = 0;

        log.info(this.baseLang.translateString("nukkit.server.startFinished", String.valueOf((double) (System.currentTimeMillis() - Nukkit.START_TIME) / 1000)));

        // 控制台线程推迟到服务器就绪后启动：此前读到的命令依赖尚未创建的对象会被静默丢弃
        // （如引导下载期间管道送达的 stop），留在 stdin 等就绪后读取
        // Console thread starts after the server is ready; earlier commands would be silently dropped
        this.consoleThread.start();

        this.scheduler.scheduleDelayedTask(InternalPlugin.INSTANCE, System::gc, 20);

        this.tickProcessor();
        this.forceShutdown();
        if (!this.completeShutdownOnOwner()) {
            log.warn("Shutdown did not complete before owner exit; only previously admitted profiles are available");
        }
    }

    private static final byte[] QUERY_PREFIX = {(byte) 0xfe, (byte) 0xfd};

    /**
     * Internal: Handle query
     * @param address sender address
     * @param payload payload
     */
    public void handlePacket(InetSocketAddress address, ByteBuf payload) {
        try {
            if (this.queryHandler == null || !payload.isReadable(3)) {
                return;
            }
            byte[] prefix = new byte[2];
            payload.readBytes(prefix);
            if (Arrays.equals(prefix, QUERY_PREFIX)) {
                this.queryHandler.handle(address, payload);
            }
        } catch (Exception e) {
            log.error("Error whilst handling packet", e);

            this.network.blockAddress(address.getAddress(), -1);
        }
    }

    private int lastLevelGC;

    /**
     * Internal: Tick the server
     */
    public void tickProcessor() {
        this.nextTick = System.currentTimeMillis();
        try {
            while (this.isRunning.get()) {
                try {
                    this.tick();

                    long next = this.nextTick;
                    long current = System.currentTimeMillis();

                    if (next - 0.1 > current) {
                        long allocated = next - current - 1;

                        if (doLevelGC) { // Instead of wasting time, do something potentially useful
                            int offset = 0;
                            for (int i = 0; i < levelArray.length; i++) {
                                offset = (i + lastLevelGC) % levelArray.length;
                                Level level = levelArray[offset];
                                if (!level.isBeingConverted) {
                                    level.doGarbageCollection(allocated - 1);
                                }
                                allocated = next - System.currentTimeMillis();
                                if (allocated <= 0) break;
                            }
                            lastLevelGC = offset + 1;
                        }

                        if (allocated > 0 || !doLevelGC) {
                            try {
                                //noinspection BusyWait
                                Thread.sleep(allocated, 900000);
                            } catch (Exception e) {
                                this.getLogger().logException(e);
                            }
                        }
                    }
                } catch (RuntimeException e) {
                    log.error("A RuntimeException happened while ticking the server", e);
                }
            }
        } catch (Throwable e) {
            log.fatal("Exception happened while ticking server\n{}", Utils.getAllThreadDumps(), e);
        }
    }

    @Deprecated
    public void onPlayerCompleteLoginSequence(Player player) {
        this.playerList.put(player.getUniqueId(), player);
        this.updatePlayerListData(player.getUniqueId(), player.getId(), player.getDisplayName(), player.getSkin(), player.getLoginChainData().getXUID());
    }

    public void addPlayer(InetSocketAddress socketAddress, Player player) {
        this.players.put(socketAddress, player);
    }

    public void addOnlinePlayer(Player player) {
        this.playerList.put(player.getUniqueId(), player);
        this.releaseLoginProfile(player);
        PlayerListPacket.Entry entry = new PlayerListPacket.Entry(player.getUniqueId(), player.getId(), player.getDisplayName(), player.getSkin(), player.getLoginChainData().getXUID(), player.getLocatorBarColor());
        Player[] viewers = this.playerList.values().stream()
                .filter(viewer -> !viewer.sentSkins.contains(player.getUniqueId()))
                .toArray(Player[]::new);
        if (viewers.length > 0) {
            this.updatePlayerListData(entry, viewers);
        }
    }

    public void removeOnlinePlayer(Player player) {
        if (player.getUniqueId() == null) {
            return;
        }
        if (this.playerList.containsKey(player.getUniqueId())) {
            this.playerList.remove(player.getUniqueId());

            PlayerListPacket pk = new PlayerListPacket();
            pk.type = PlayerListPacket.TYPE_REMOVE;
            pk.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(player.getUniqueId())};

            Server.broadcastPacket(this.playerList.values(), pk);
        }
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin) {
        this.updatePlayerListData(uuid, entityId, name, skin, "", this.playerList.values());
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, String xboxUserId) {
        this.updatePlayerListData(uuid, entityId, name, skin, xboxUserId, this.playerList.values());
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, Player[] players) {
        this.updatePlayerListData(uuid, entityId, name, skin, "", players);
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, String xboxUserId, Collection<Player> players) {
        this.updatePlayerListData(uuid, entityId, name, skin, xboxUserId, players.toArray(Player.EMPTY_ARRAY));
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, String xboxUserId, Player[] players) {
        this.updatePlayerListData(new PlayerListPacket.Entry(uuid, entityId, name, skin, xboxUserId, Color.WHITE), players);
    }

    public void updatePlayerListData(PlayerListPacket.Entry playerListEntry, Player[] players) {
        for (Player viewer : players) {
            if (viewer.getGameVersion().isNetEase() && viewer.sentSkins.contains(playerListEntry.uuid)) {
                PlayerListPacket remove = new PlayerListPacket();
                remove.type = PlayerListPacket.TYPE_REMOVE;
                remove.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(playerListEntry.uuid)};
                viewer.dataPacket(remove);
                viewer.confirmedSkins.remove(playerListEntry.uuid);
            }
            viewer.sentSkins.add(playerListEntry.uuid);
        }

        PlayerListPacket pk = new PlayerListPacket();
        pk.type = PlayerListPacket.TYPE_ADD;
        pk.entries = new PlayerListPacket.Entry[]{playerListEntry};
        Server.broadcastPacket(players, pk); // This is sent "directly" so it always gets thru before possible TYPE_REMOVE packet for NPCs etc.
    }

    public void removePlayerListData(UUID uuid) {
        this.removePlayerListData(uuid, this.playerList.values());
    }

    public void removePlayerListData(UUID uuid, Player[] players) {
        PlayerListPacket pk = new PlayerListPacket();
        pk.type = PlayerListPacket.TYPE_REMOVE;
        pk.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(uuid)};
        for (Player player : players) {
            player.dataPacket(pk);
            player.sentSkins.remove(uuid);
            player.confirmedSkins.remove(uuid);
        }
    }

    public void removePlayerListData(UUID uuid, Collection<Player> players) {
        this.removePlayerListData(uuid, players.toArray(Player.EMPTY_ARRAY));
    }

    public void removePlayerListData(UUID uuid, Player player) {
        this.removePlayerListData(uuid, new Player[]{player});
    }

    public void sendFullPlayerListData(Player player) {
        PlayerListPacket.Entry[] array = this.playerList.values().stream()
                .filter(p -> player.sentSkins.add(p.getUniqueId()))
                .map(p -> new PlayerListPacket.Entry(
                        p.getUniqueId(),
                        p.getId(),
                        p.getDisplayName(),
                        p.getSkin(),
                        p.getLoginChainData().getXUID(),
                        p.getLocatorBarColor()))
                .toArray(PlayerListPacket.Entry[]::new);
        Object[][] splitArray = Utils.splitArray(array, 50);
        if (splitArray != null) {
            for (Object[] a : splitArray) {
                PlayerListPacket pk = new PlayerListPacket();
                pk.type = PlayerListPacket.TYPE_ADD;
                pk.entries = (PlayerListPacket.Entry[]) a;
                player.dataPacket(pk);
            }
        }
    }

    public void sendRecipeList(Player player) {
        BatchPacket cachedPacket = this.craftingManager.getCachedPacket(player.getGameVersion());
        if (cachedPacket != null) { // Don't send recipes if they wouldn't work anyways
            player.dataPacket(cachedPacket);
        }
    }

    private void checkTickUpdates(int currentTick) {
        if (this.alwaysTickPlayers) {
            for (Object p : this.players.values().toArray()) {
                ((Player) p).onUpdate(currentTick);
            }
        }

        for (Player p : this.getOnlinePlayers().values()) {
            p.resetPacketCounters();
        }

        // Do level ticks
        for (Level level : this.levelArray) {
            if (level.isBeingConverted) {
                continue;
            }
            if (level.getTickRate() > this.baseTickRate && --level.tickRateCounter > 0) {
                if (!this.alwaysTickPlayers) {
                    for (Player player : new ArrayList<>(level.getPlayers().values())) {
                        if (!player.spawned || player.closed || player.level != level) {
                            continue;
                        }
                        try {
                            player.onUpdate(currentTick);
                        } catch (Exception e) {
                            log.error(this.baseLang.translateString("nukkit.level.tickError",
                                    new String[]{level.getFolderName(), Utils.getExceptionMessage(e)}));
                        }
                    }
                }
                continue;
            }

            try {
                long levelTime = System.currentTimeMillis();
                level.providerLock.readLock().lock();
                if (level.getProvider() == null) {//世界在其他线程上卸载
                    continue;
                }
                level.doTick(currentTick);
                int tickMs = (int) (System.currentTimeMillis() - levelTime);
                level.tickRateTime = tickMs;

                if (this.autoTickRate) {
                    if (tickMs < 50 && level.getTickRate() > this.baseTickRate) {
                        int r;
                        level.setTickRate(r = level.getTickRate() - 1);
                        if (r > this.baseTickRate) {
                            level.tickRateCounter = level.getTickRate();
                        }
                        this.getLogger().debug("Raising level \"" + level.getName() + "\" tick rate to " + level.getTickRate() + " ticks");
                    } else if (tickMs >= 50) {
                        if (level.getTickRate() == this.baseTickRate) {
                            level.setTickRate(Math.max(this.baseTickRate + 1, Math.min(this.autoTickRateLimit, tickMs / 50)));
                            this.getLogger().debug("Level \"" + level.getName() + "\" took " + tickMs + "ms, setting tick rate to " + level.getTickRate() + " ticks");
                        } else if ((tickMs / level.getTickRate()) >= 50 && level.getTickRate() < this.autoTickRateLimit) {
                            level.setTickRate(level.getTickRate() + 1);
                            this.getLogger().debug("Level \"" + level.getName() + "\" took " + tickMs + "ms, setting tick rate to " + level.getTickRate() + " ticks");
                        }
                        level.tickRateCounter = level.getTickRate();
                    }
                }
            } catch (Exception e) {
                log.error(this.baseLang.translateString("nukkit.level.tickError", new String[]{level.getFolderName(), Utils.getExceptionMessage(e)}));
            } finally {
                level.providerLock.readLock().unlock();
            }
        }
    }

    public void doAutoSave() {
        if (this.autoSave) {
            for (Player player : new ArrayList<>(this.players.values())) {
                if (player.isOnline()) {
                    player.save(true);
                } else if (!player.isConnected()) {
                    this.removePlayer(player);
                }
            }

            for (Level level : this.levelArray) {
                if (!nonAutoSaveWorlds.contains(level.getName())) {
                    level.save();
                }
            }
        }
    }

    private void tick() {
        tick(System.currentTimeMillis(), System::nanoTime);
    }

    // The actual tick body also accepts a monotonic clock for deterministic boundary tests.
    void tick(long tickTime, LongSupplier nanoTime) {

        this.drainPlayerCloses();
        long time = tickTime - this.nextTick;
        if (time < -25) {
            try {
                Thread.sleep(Math.max(5, -time - 25));
            } catch (InterruptedException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }

        if ((tickTime - this.nextTick) < -25) {
            return;
        }

        long tickTimeNano = nanoTime.getAsLong();
        long tickId = ++this.tickCounter;
        Throwable tickFailure = null;
        try {
            this.pluginManager.callEvent(new ServerTickStartEvent(tickId));
            this.network.processInterfaces();

            if (this.rcon != null) {
                this.rcon.check();
            }

            this.scheduler.mainThreadHeartbeat(this.tickCounter);

            this.checkTickUpdates(this.tickCounter);

            for (Object player : this.players.values().toArray()) {
                ((Player) player).checkNetwork();
            }

            if ((this.tickCounter & 0b1111) == 0) {
                this.titleTick();

                this.network.resetStatistics();
                this.maxTick = 20;
                this.maxUse = 0;

                if ((this.tickCounter & 0b111111111) == 0) {
                    try {
                        this.pluginManager.callEvent(this.queryRegenerateEvent = new QueryRegenerateEvent(this, 5));
                        if (this.queryHandler != null) {
                            this.queryHandler.regenerateInfo();
                        }
                    } catch (Exception e) {
                        log.error(e);
                    }
                }

                this.network.updateName();
            }

            if (++this.autoSaveTicker >= this.autoSaveTicks) {
                this.autoSaveTicker = 0;
                this.doAutoSave();
            }

            for (Level level : this.levelArray) {
                if (!level.isBeingConverted && level.isChunkGarbageCollectionTick(this.tickCounter)) {
                    level.doChunkGarbageCollection();
                }
            }

            long nowNano = nanoTime.getAsLong();

            float tick = (float) Math.min(20, 1000000000 / Math.max(1000000, ((double) nowNano - tickTimeNano)));
            float use = (float) Math.min(1, ((double) (nowNano - tickTimeNano)) / 50000000);

            if (this.maxTick > tick) {
                this.maxTick = tick;
            }

            if (this.maxUse < use) {
                this.maxUse = use;
            }

            System.arraycopy(this.tickAverage, 1, this.tickAverage, 0, this.tickAverage.length - 1);
            this.tickAverage[this.tickAverage.length - 1] = tick;

            System.arraycopy(this.useAverage, 1, this.useAverage, 0, this.useAverage.length - 1);
            this.useAverage[this.useAverage.length - 1] = use;

            if ((this.nextTick - tickTime) < -1000) {
                this.nextTick = tickTime;
            } else {
                this.nextTick += 50;
            }
        } catch (RuntimeException | Error failure) {
            tickFailure = failure;
            throw failure;
        } finally {
            // Capture before dispatch: observers must not inflate the value they receive.
            long durationNanos = nanoTime.getAsLong() - tickTimeNano;
            try {
                this.pluginManager.callEvent(new ServerTickEndEvent(tickId, durationNanos));
            } catch (RuntimeException | Error eventFailure) {
                if (tickFailure != null) {
                    if (eventFailure != tickFailure) {
                        tickFailure.addSuppressed(eventFailure);
                    }
                } else {
                    throw eventFailure;
                }
            }
        }
    }

    public long getNextTick() {
        return nextTick;
    }

    /** A timed-out shutdown may retain admitted snapshots, but cannot start another live checkpoint. */
    public boolean isShutdownTimedOut() { return this.shutdownTimedOut; }

    public boolean isShutdownInProgress() {
        return this.hasStopped || this.requestedShutdownReason != null;
    }

    public long getShutdownProgressAt() {
        return this.shutdownProgressAt;
    }

    public void touchShutdownProgress() {
        this.shutdownProgressAt = System.currentTimeMillis();
    }

    private void titleTick() {
        if (!Nukkit.TITLE) return;

        Runtime runtime = Runtime.getRuntime();
        double used = NukkitMath.round((double) (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024, 2);
        double max = NukkitMath.round(((double) runtime.maxMemory()) / 1024 / 1024, 2);
        System.out.print((char) 0x1b + "]0;" + Nukkit.NUKKIT +
                " | Online " + this.players.size() + '/' + this.maxPlayers +
                " | Memory " + Math.round(used / max * 100) + '%' +
                /*" | U " + NukkitMath.round((this.network.getUpload() / 1024 * 1000), 2) +
                " D " + NukkitMath.round((this.network.getDownload() / 1024 * 1000), 2) + " kB/s" +*/
                " | TPS " + this.getTicksPerSecond() +
                " | Load " + this.getTickUsage() + '%' + (char) 0x07);
    }

    public QueryRegenerateEvent getQueryInformation() {
        return this.queryRegenerateEvent;
    }

    public String getName() {
        return Nukkit.NUKKIT;
    }

    public boolean isRunning() {
        return isRunning.get();
    }

    public String getNukkitVersion() {
        return Nukkit.VERSION;
    }

    public String getCodename() {
        return Nukkit.CODENAME;
    }

    public String getVersion() {
        return ProtocolInfo.MINECRAFT_VERSION;
    }

    public String getApiVersion() {
        return Nukkit.API_VERSION;
    }

    public String getFilePath() {
        return filePath;
    }

    public String getDataPath() {
        return dataPath;
    }

    public String getPluginPath() {
        return pluginPath;
    }

    public int getMaxPlayers() {
        return maxPlayers;
    }

    public void setMaxPlayers(int maxPlayers) {
        this.maxPlayers = maxPlayers;
    }

    public int getPort() {
        return port;
    }

    public int getViewDistance() {
        return viewDistance;
    }

    @NotNull
    public String getIp() {
        return ip;
    }

    public boolean isIpv6Enabled() {
        return ipv6Enabled;
    }

    @NotNull
    public String getIpv6Address() {
        return ipv6Address;
    }

    public int getIpv6Port() {
        return ipv6Port;
    }

    public UUID getServerUniqueId() {
        return this.serverID;
    }

    public boolean getAutoSave() {
        return this.autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
        for (Level level : this.levelArray) {
            level.setAutoSave(this.autoSave);
        }
    }

    public boolean isAutoCompactionEnabled() {
        return this.autoCompaction;
    }

    public void setAutoCompactionTicks(int ticks) {
        Preconditions.checkArgument(ticks > 0, "ticks");
        this.autoCompactionTicks = ticks;
    }

    public int getAutoCompactionTicks() {
        return this.autoCompactionTicks;
    }

    public String getLevelType() {
        return this.getPropertyString("level-type", "default");
    }

    public int getGamemode() {
        return gamemode;
    }

    public boolean getForceGamemode() {
        return this.forceGamemode;
    }

    public static String getGamemodeString(int mode) {
        return getGamemodeString(mode, false);
    }

    public static String getGamemodeString(int mode, boolean direct) {
        switch (mode) {
            case Player.SURVIVAL:
                return direct ? "Survival" : "%gameMode.survival";
            case Player.CREATIVE:
                return direct ? "Creative" : "%gameMode.creative";
            case Player.ADVENTURE:
                return direct ? "Adventure" : "%gameMode.adventure";
            case Player.SPECTATOR:
                return direct ? "Spectator" : "%gameMode.spectator";
        }
        return "UNKNOWN";
    }

    public static int getGamemodeFromString(String str) {
        return switch (str.trim().toLowerCase(Locale.ROOT)) {
            case "0", "survival", "s" -> Player.SURVIVAL;
            case "1", "creative", "c" -> Player.CREATIVE;
            case "2", "adventure", "a" -> Player.ADVENTURE;
            case "3", "spectator", "spc", "view", "v" -> Player.SPECTATOR;
            default -> -1;
        };
    }

    public static int getDifficultyFromString(String str) {
        return switch (str.trim().toLowerCase(Locale.ROOT)) {
            case "0", "peaceful", "p" -> 0;
            case "1", "easy", "e" -> 1;
            case "2", "normal", "n" -> 2;
            case "3", "hard", "h" -> 3;
            default -> -1;
        };
    }

    public int getDifficulty() {
        return this.difficulty;
    }

    public void setDifficulty(int difficulty) {
        int value = difficulty;
        if (value < 0) value = 0;
        if (value > 3) value = 3;
        this.difficulty = value;
        this.setPropertyInt("difficulty", value);
    }

    public boolean hasWhitelist() {
        return this.whitelistEnabled;
    }

    public int getSpawnRadius() {
        return spawnRadius;
    }

    public boolean getAllowFlight() {
        return flyChecks;
    }

    public boolean isHardcore() {
        return this.isHardcore;
    }

    public int getDefaultGamemode() {
        if (this.defaultGameMode == Integer.MAX_VALUE) {
            this.defaultGameMode = this.getGamemode();
        }
        return this.defaultGameMode;
    }

    public String getMotd() {
        return motd;
    }

    public String getSubMotd() {
        return this.getPropertyString("sub-motd", "Powered by Nukkit-MOT");
    }

    public boolean getForceResources() {
        return this.forceResources;
    }

    public boolean getMobAiEnabled() {
        return this.mobAiEnabled;
    }

    public MainLogger getLogger() {
        return MainLogger.getLogger();
    }

    public EntityMetadataStore getEntityMetadata() {
        return entityMetadata;
    }

    public PlayerMetadataStore getPlayerMetadata() {
        return playerMetadata;
    }

    public LevelMetadataStore getLevelMetadata() {
        return levelMetadata;
    }

    public PluginManager getPluginManager() {
        return this.pluginManager;
    }

    public CraftingManager getCraftingManager() {
        return craftingManager;
    }

    public ResourcePackManager getResourcePackManager() {
        return resourcePackManager;
    }

    public ServerScheduler getScheduler() {
        return scheduler;
    }

    /**
     * Get current tick
     *
     * @return current tick
     */
    public int getTick() {
        return tickCounter;
    }

    /**
     * Get ticks per second
     *
     * @return TPS
     */
    public float getTicksPerSecond() {
        return ((float) Math.round(this.maxTick * 100)) / 100;
    }

    /**
     * Get average ticks per second
     *
     * @return average TPS
     */
    public float getTicksPerSecondAverage() {
        float sum = 0;
        int count = this.tickAverage.length;
        for (float aTickAverage : this.tickAverage) {
            sum += aTickAverage;
        }
        return (float) NukkitMath.round(sum / count, 2);
    }

    public float getTickUsage() {
        return (float) NukkitMath.round(this.maxUse * 100, 2);
    }

    public float getTickUsageAverage() {
        float sum = 0;
        for (float aUseAverage : this.useAverage) {
            sum += aUseAverage;
        }
        return ((float) Math.round(sum / this.useAverage.length * 100)) / 100;
    }

    public SimpleCommandMap getCommandMap() {
        return commandMap;
    }

    public Map<UUID, Player> getOnlinePlayers() {
        return ImmutableMap.copyOf(playerList);
    }

    public int getOnlinePlayersCount() {
        return this.playerList.size();
    }

    public void addRecipe(Recipe recipe) {
        this.craftingManager.registerRecipe(recipe);
    }

    public Optional<Player> getPlayer(UUID uuid) {
        Preconditions.checkNotNull(uuid, "uuid");
        return Optional.ofNullable(playerList.get(uuid));
    }

    /**
     * 名称条目的写入来源，用于阻止离线登录认领 Xbox 认证账户的数据。
     * <p>
     * Provenance of a name entry, used to keep offline logins from claiming an Xbox
     * authenticated account's data.
     */
    enum NameProvenance {
        /** 旧版 16 字节条目，无来源记录 Legacy 16-byte entry without a provenance record */
        LEGACY_UNKNOWN,
        XBOX_AUTHED,
        OFFLINE
    }

    record NameEntry(UUID uuid, NameProvenance provenance) {
    }

    private static final int NAME_ENTRY_UUID_BYTES = 16;
    private static final byte PROVENANCE_XBOX_AUTHED = 0x01;
    private static final byte PROVENANCE_OFFLINE = 0x02;

    static byte[] encodeNameEntry(UUID uuid, NameProvenance provenance) {
        ByteBuffer buffer = ByteBuffer.allocate(NAME_ENTRY_UUID_BYTES + 1);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());
        buffer.put(switch (provenance) {
            case XBOX_AUTHED -> PROVENANCE_XBOX_AUTHED;
            case OFFLINE -> PROVENANCE_OFFLINE;
            case LEGACY_UNKNOWN -> throw new IllegalArgumentException("legacy entries are never written");
        });
        return buffer.array();
    }

    /**
     * 解码名称条目：17 字节为现行格式（UUID + 来源），16 字节为旧格式（来源未知），其余无效。
     * <p>
     * Decodes a name entry: 17 bytes is the current format (UUID + provenance), 16 bytes the
     * legacy format (unknown provenance); anything else is invalid.
     */
    static NameEntry decodeNameEntry(byte[] bytes) {
        if (bytes == null) {
            return null;
        }
        if (bytes.length == NAME_ENTRY_UUID_BYTES) {
            ByteBuffer buffer = ByteBuffer.wrap(bytes);
            return new NameEntry(new UUID(buffer.getLong(), buffer.getLong()), NameProvenance.LEGACY_UNKNOWN);
        }
        if (bytes.length != NAME_ENTRY_UUID_BYTES + 1) {
            return null;
        }
        ByteBuffer buffer = ByteBuffer.wrap(bytes);
        UUID uuid = new UUID(buffer.getLong(), buffer.getLong());
        return switch (buffer.get()) {
            case PROVENANCE_XBOX_AUTHED -> new NameEntry(uuid, NameProvenance.XBOX_AUTHED);
            case PROVENANCE_OFFLINE -> new NameEntry(uuid, NameProvenance.OFFLINE);
            default -> null;
        };
    }

    public Optional<UUID> lookupName(String name) {
        return lookupNameEntry(name).map(NameEntry::uuid);
    }

    Optional<NameEntry> lookupNameEntry(String name) {
        byte[] nameBytes = name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] entryBytes = nameLookup.get(nameBytes);
        if (entryBytes == null) {
            return Optional.empty();
        }

        NameEntry entry = decodeNameEntry(entryBytes);
        if (entry == null) {
            log.warn("Invalid uuid in name lookup database detected! Removing...");
            nameLookup.delete(nameBytes);
            return Optional.empty();
        }

        return Optional.of(entry);
    }

    /**
     * @param lowerCaseName login name after trimming, lower case
     * @return why startup left this name's padded profiles unfolded, or {@code null} when offline
     * logins with it may proceed
     */
    String profileFoldBlockReason(String lowerCaseName) {
        return lowerCaseName == null ? null : profileFoldBlockedNames.get(lowerCaseName);
    }

    void updateName(UUID uuid, String name, boolean xboxAuthed) {
        byte[] nameBytes = name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        nameLookup.put(nameBytes, encodeNameEntry(uuid,
                xboxAuthed ? NameProvenance.XBOX_AUTHED : NameProvenance.OFFLINE));
    }

    public IPlayer getOfflinePlayer(final String name) {
        IPlayer result = this.getPlayerExact(name.toLowerCase(Locale.ROOT));
        if (result != null) {
            return result;
        }

        return lookupName(name).map(uuid -> new OfflinePlayer(this, uuid, name))
                .orElse(new OfflinePlayer(this, name));
    }

    public IPlayer getOfflinePlayer(UUID uuid) {
        Preconditions.checkNotNull(uuid, "uuid");
        Optional<Player> onlinePlayer = getPlayer(uuid);
        if (onlinePlayer.isPresent()) {
            return onlinePlayer.get();
        }

        return new OfflinePlayer(this, uuid);
    }

    public CompoundTag getOfflinePlayerData(UUID uuid) {
        return getOfflinePlayerData(uuid, false);
    }

    public CompoundTag getOfflinePlayerData(UUID uuid, boolean create) {
        return getOfflinePlayerDataInternal(uuid.toString(), true, create);
    }

    public CompoundTag getOfflinePlayerData(String name) {
        return getOfflinePlayerData(name, false);
    }

    public CompoundTag getOfflinePlayerData(String name, boolean create) {
        if (this.savePlayerDataByUuid) {
            Optional<UUID> uuid = lookupName(name);
            return getOfflinePlayerDataInternal(uuid.map(UUID::toString).orElse(name), true, create);
        } else {
            return getOfflinePlayerDataInternal(name.toLowerCase(Locale.ROOT), true, create);
        }
    }

    /**
     * 按名直读遗留数据文件，不经查找表解析：条目指向认证账户或塌缩身份时，解析会把被守卫的
     * 数据发给当前登录者。
     * <p>
     * Reads the legacy name-keyed file directly, without lookup resolution: resolving would hand
     * out guarded data when the entry points at an authenticated account or a collapsed identity.
     */
    CompoundTag getLegacyPlayerDataByName(String name) {
        return getOfflinePlayerDataInternal(name, true, false);
    }

    private CompoundTag getOfflinePlayerDataInternal(String name, boolean runEvent, boolean create) {
        Preconditions.checkNotNull(name, "name");

        PlayerDataSerializeEvent event = new PlayerDataSerializeEvent(name, playerDataSerializer);
        if (runEvent) {
            pluginManager.callEvent(event);
        }

        awaitProfileIO(profileQueue().flush(name), true);
        Optional<InputStream> dataStream = Optional.empty();
        try {
            ReentrantLock dataLock = playerDataLock(name);
            dataLock.lock();
            try {
                dataStream = event.getSerializer().read(name, event.getUuid().orElse(null));
                if (dataStream.isPresent()) {
                    return NBTIO.readCompressed(dataStream.get());
                }
            } finally {
                dataLock.unlock();
            }
        } catch (IOException e) {
            log.warn(this.getLanguage().translateString("nukkit.data.playerCorrupted", name));
            log.throwing(e);
            return null;
        } finally {
            if (dataStream.isPresent()) {
                try {
                    dataStream.get().close();
                } catch (IOException e) {
                    log.throwing(e);
                }
            }
        }
        CompoundTag nbt = null;
        if (create) {
            nbt = newPlayerData();

            this.saveOfflinePlayerData(name, nbt, true, runEvent);
        }
        return nbt;
    }

    CompoundTag newPlayerData() {
            Position spawn = this.getDefaultLevel().getSafeSpawn();
            long time = System.currentTimeMillis();
            return new CompoundTag()
                    .putLong("firstPlayed", time / 1000)
                    .putLong("lastPlayed", time / 1000)
                    .putList(new ListTag<DoubleTag>("Pos")
                            .add(new DoubleTag("0", spawn.x))
                            .add(new DoubleTag("1", spawn.y))
                            .add(new DoubleTag("2", spawn.z)))
                    .putString("Level", this.getDefaultLevel().getName())
                    .putList(new ListTag<>("Inventory"))
                    .putCompound("Achievements", new CompoundTag())
                    .putInt("playerGameType", this.getGamemode())
                    .putList(new ListTag<DoubleTag>("Motion")
                            .add(new DoubleTag("0", 0))
                            .add(new DoubleTag("1", 0))
                            .add(new DoubleTag("2", 0)))
                    .putList(new ListTag<FloatTag>("Rotation")
                            .add(new FloatTag("0", 0))
                            .add(new FloatTag("1", 0)))
                    .putFloat("FallDistance", 0)
                    .putShort("Fire", 0)
                    .putShort("Air", 300)
                    .putBoolean("OnGround", true)
                    .putBoolean("Invulnerable", false);

    }

    record LoginProfileFiles(boolean uuidExists, boolean nameExists, Optional<NameEntry> previous) { }

    PlayerDataSaveQueue.ReadHandle<LoginProfileFiles> inspectLoginProfile(UUID uuid, String name, boolean xboxAuthed) {
        return profileQueue().read(Set.of(uuid.toString(), name), current -> {
            boolean uuidExists = profileFileExists(uuid.toString());
            boolean nameExists = profileFileExists(name);
            // Read only: invalid lookup bytes must not become an unfenced async DB mutation.
            Optional<NameEntry> previous = savePlayerDataByUuid && !xboxAuthed && !uuidExists && nameLookup != null
                    ? Optional.ofNullable(decodeNameEntry(nameLookup.get(name.getBytes(StandardCharsets.UTF_8)))) : Optional.empty();
            return new LoginProfileFiles(uuidExists, nameExists, previous);
        });
    }

    private boolean profileFileExists(String identity) throws IOException {
        try {
            java.nio.file.Files.readAttributes(java.nio.file.Path.of(getDataPath(), "players", identity + ".dat"),
                    java.nio.file.attribute.BasicFileAttributes.class);
            return true;
        } catch (java.nio.file.NoSuchFileException missing) { return false; }
    }

    /** Legacy readers execute on MAIN only after their FIFO predecessors have acknowledged. */
    final class LoginProfileRead implements AutoCloseable {
        final PlayerDataSaveQueue.ReadHandle<CompoundTag> observation;
        final PlayerDataSerializer serializer;
        final String key;
        final UUID storageUuid;
        final boolean legacy;
        boolean taken;
        CompoundTag value;
        LoginProfileRead(PlayerDataSaveQueue.ReadHandle<CompoundTag> observation, PlayerDataSerializer serializer,
                         String key, UUID storageUuid, boolean legacy) {
            this.observation = observation; this.serializer = serializer; this.key = key;
            this.storageUuid = storageUuid; this.legacy = legacy;
        }
        CompletableFuture<CompoundTag> ready() { return observation.completion().toCompletableFuture(); }
        boolean isCurrent() { return observation.isCurrent(); }
        boolean isSuperseded() { return observation.isSuperseded(); }
        CompoundTag take() throws IOException {
            if (!ready().isDone()) throw new IllegalStateException("Profile read is pending");
            CompoundTag result = ready().join();
            if (!legacy) return result;
            if (!taken) { taken = true; value = readSelectedProfile(serializer, key, storageUuid); }
            return value;
        }
        @Override public void close() { observation.close(); }
    }

    LoginProfileRead readLoginProfile(String key, UUID uuid, String name, BooleanSupplier current) {
        PlayerDataSerializeEvent event = new PlayerDataSerializeEvent(key, playerDataSerializer);
        pluginManager.callEvent(event);
        if (!current.getAsBoolean()) throw new CancellationException("Profile read listener superseded login");
        PlayerDataSerializer selected = event.getSerializer();
        UUID storageUuid = event.getUuid().orElse(null);
        boolean legacy = !selected.supportsConcurrentReads();
        var observation = profileQueue().read(Set.of(uuid.toString(), name), observationCurrent ->
                legacy ? null : readSelectedProfile(selected, key, storageUuid));
        return new LoginProfileRead(observation, selected, key, storageUuid, legacy);
    }

    private CompoundTag readSelectedProfile(PlayerDataSerializer selected, String key, UUID uuid) throws IOException {
        Optional<InputStream> source = selected.getClass() == DefaultPlayerDataSerializer.class
                ? ((DefaultPlayerDataSerializer) selected).readStrict(key, uuid) : selected.read(key, uuid);
        if (source.isEmpty()) return null;
        try (InputStream input = source.get()) { return NBTIO.readCompressed(input); }
    }

    /**
     * 迁移旧身份数据。仅离线登录可调用（调用方已做认证门控），故 xboxAuthed 恒为 false。
     * <p>
     * Migrates the previous identity's data. Only reachable from an offline login (the caller has
     * already applied the auth gates), so xboxAuthed is always false here.
     */
    PlayerDataMigrator.Result migratePlayerData(UUID previous, UUID current) {
        return awaitProfileIO(migratePlayerDataForLogin(previous, current, current.toString(), () -> true), true);
    }

    CompletionStage<PlayerDataMigrator.Result> migratePlayerDataForLogin(UUID previous, UUID current, String name, BooleanSupplier token) {
        if (previous == null || current == null || previous.equals(current))
            return CompletableFuture.completedStage(PlayerDataMigrator.Result.SKIPPED);
        String source = previous.toString(), target = current.toString();
        PlayerDataSerializeEvent oldEvent = new PlayerDataSerializeEvent(source, playerDataSerializer);
        pluginManager.callEvent(oldEvent);
        if (!token.getAsBoolean()) throw new CancellationException("Source serializer listener superseded migration");
        PlayerDataSerializeEvent newEvent = new PlayerDataSerializeEvent(target, playerDataSerializer);
        pluginManager.callEvent(newEvent);
        if (!token.getAsBoolean()) throw new CancellationException("Target serializer listener superseded migration");
        PlayerDataSerializer oldSerializer = oldEvent.getSerializer(), newSerializer = newEvent.getSerializer();
        if (!token.getAsBoolean()) throw new CancellationException("Migration owner changed before admission");
        return profileQueue().migrateVerified(source, new HashSet<>(List.of(source, target, name)), recovering -> withProfileLocks(Set.of(source, target), () -> {
            boolean eligible = PlayerDataMigrator.isSourceIdentityEligible(previous, current, false);
            if (!eligible) {
                if (recovering) throw new PlayerDataSaveQueue.MigrationException("Protected identity cannot acknowledge recovery");
                return PlayerDataMigrator.migrate(migrationSerializer(oldSerializer), migrationSerializer(newSerializer), previous, current, false);
            }
            // Missing is legitimate; corrupt/unreadable eligible source is never a fresh-profile signal.
            CompoundTag authoritativeSource = readSelectedProfile(oldSerializer, source, previous);
            if (recovering) {
                acknowledgeMigrationRecovery(oldSerializer, newSerializer, authoritativeSource, target, current, previous);
                return PlayerDataMigrator.Result.MIGRATED;
            }
            PlayerDataMigrator.Result result = PlayerDataMigrator.migrate(
                    migrationSerializer(oldSerializer), migrationSerializer(newSerializer), previous, current, false);
            if (result == PlayerDataMigrator.Result.FAILED) throw new PlayerDataSaveQueue.MigrationException("Profile migration was not acknowledged");
            if (result == PlayerDataMigrator.Result.MIGRATED && oldSerializer == newSerializer
                    && oldSerializer.getClass() == DefaultPlayerDataSerializer.class) {
                // Existing mover may use its platform fallback. No atomic-publication receipt is claimed here.
                try { ((DefaultPlayerDataSerializer) oldSerializer).syncDirectory(); }
                catch (IOException error) { throw new PlayerDataSaveQueue.MigrationException("Unknown profile migration ACK", error); }
            }
            return result;
        }), profileMigrationExecutor(oldSerializer, newSerializer)).completion();
    }

    CompletionStage<PlayerDataMigrator.Result> migrateLegacyPlayerDataForLogin(String name, UUID current, BooleanSupplier token) {
        PlayerDataSerializeEvent oldEvent = new PlayerDataSerializeEvent(name, playerDataSerializer);
        pluginManager.callEvent(oldEvent);
        if (!token.getAsBoolean()) throw new CancellationException("Source serializer listener superseded migration");
        PlayerDataSerializeEvent newEvent = new PlayerDataSerializeEvent(current.toString(), playerDataSerializer);
        pluginManager.callEvent(newEvent);
        if (!token.getAsBoolean()) throw new CancellationException("Target serializer listener superseded migration");
        PlayerDataSerializer oldSerializer = oldEvent.getSerializer(), newSerializer = newEvent.getSerializer();
        if (!token.getAsBoolean()) throw new CancellationException("Migration owner changed before admission");
        return profileQueue().migrateVerified(name, Set.of(name, current.toString()), recovering -> withProfileLocks(Set.of(name, current.toString()), () -> {
            CompoundTag existing = readSelectedProfile(newSerializer, current.toString(), current);
            if (recovering) {
                CompoundTag authoritativeSource = readSelectedProfile(oldSerializer, name, oldEvent.getUuid().orElse(null));
                acknowledgeMigrationRecovery(oldSerializer, newSerializer, authoritativeSource, current.toString(), current, null);
                return PlayerDataMigrator.Result.MIGRATED;
            }
            if (existing != null) return PlayerDataMigrator.Result.SKIPPED;
            CompoundTag legacy = readSelectedProfile(oldSerializer, name, oldEvent.getUuid().orElse(null));
            if (legacy == null) throw new PlayerDataSaveQueue.MigrationException("Legacy profile disappeared");
            // Publish and validate the target before deleting the only legacy copy.
            writeSelectedProfile(newSerializer, legacy, current.toString(), newEvent.getUuid().orElse(null));
            if (readSelectedProfile(newSerializer, current.toString(), current) == null)
                throw new PlayerDataSaveQueue.MigrationException("Legacy target was not readable");
            // Custom deletion failure leaves a duplicate old source, never a missing only copy.
            oldSerializer.delete(name, oldEvent.getUuid().orElse(null));
            if (oldSerializer.getClass() == DefaultPlayerDataSerializer.class)
                ((DefaultPlayerDataSerializer) oldSerializer).syncDirectory();
            return PlayerDataMigrator.Result.MIGRATED;
        }), profileMigrationExecutor(oldSerializer, newSerializer)).completion();
    }

    private void acknowledgeMigrationRecovery(PlayerDataSerializer previous, PlayerDataSerializer canonical,
                                               CompoundTag source, String target, UUID uuid, UUID handoverSource) throws IOException {
        CompoundTag published = readSelectedProfile(canonical, target, uuid);
        if (handoverSource != null && (source != null && !PlayerDataMigrator.isSourcePayloadEligible(source, handoverSource)
                || published != null && !PlayerDataMigrator.isSourcePayloadEligible(published, handoverSource)))
            throw new PlayerDataSaveQueue.MigrationException("Authenticated payload cannot acknowledge offline migration recovery");
        if (published == null) {
            if (source == null) throw new PlayerDataSaveQueue.MigrationException("Migration recovery has neither authoritative source nor canonical target");
            // A prior strict-read/encode failure may have published nothing. The repaired source is authoritative.
            writeSelectedProfile(canonical, source, target, uuid);
            CompoundTag verified = readSelectedProfile(canonical, target, uuid);
            if (!source.equals(verified)) throw new PlayerDataSaveQueue.MigrationException("Recovered migration target failed payload verification");
            return;
        }
        // Native rename can leave the sole valid copy at the exact target after a failed directory ACK.
        // A custom provider needs the still-authoritative source; parseable partial output alone is not proof.
        if (source != null && !source.equals(published))
            throw new PlayerDataSaveQueue.MigrationException("Canonical migration payload differs from its authoritative source");
        if (source == null && (previous != canonical || canonical.getClass() != DefaultPlayerDataSerializer.class))
            throw new PlayerDataSaveQueue.MigrationException("Custom migration recovery has no authoritative source");
        // Re-publish the verified canonical payload: same-channel file force, atomic rename and qualified directory ACK.
        // The selected custom provider instead must successfully acknowledge a new full stream write.
        writeSelectedProfile(canonical, published, target, uuid);
    }

    private <T> T withProfileLocks(Set<String> keys, PlayerDataSaveQueue.Operation<T> operation) throws Exception {
        List<ReentrantLock> locks = keys.stream().sorted().map(this::playerDataLock).toList();
        locks.forEach(ReentrantLock::lock);
        try { return operation.run(); }
        finally { for (int i = locks.size() - 1; i >= 0; i--) locks.get(i).unlock(); }
    }

    private Executor profileMigrationExecutor(PlayerDataSerializer first, PlayerDataSerializer second) {
        if (first.supportsConcurrentReads() && second.supportsConcurrentReads()) return null;
        return task -> {
            if (isProfileOwnerThread()) task.run();
            else {
                synchronized (this) {
                    if (pendingProfileIO == null) pendingProfileIO = new ConcurrentLinkedQueue<>();
                    pendingProfileIO.add(task);
                }
                try { getScheduler().scheduleTask(InternalPlugin.INSTANCE, this::drainProfileIO); }
                catch (RuntimeException | Error rejected) { pendingProfileIO.remove(task); throw rejected; }
            }
        };
    }

    private void drainProfileIO() {
        if (pendingProfileIO == null) return;
        Runnable continuation;
        while ((continuation = pendingProfileIO.poll()) != null) continuation.run();
    }

    /** A Default subclass still owns its virtual hooks; do not select the migrator's native path. */
    private static PlayerDataSerializer migrationSerializer(PlayerDataSerializer selected) {
        if (!(selected instanceof DefaultPlayerDataSerializer) || selected.getClass() == DefaultPlayerDataSerializer.class)
            return selected;
        return new PlayerDataSerializer() {
            public Optional<InputStream> read(String name, UUID uuid) throws IOException { return selected.read(name, uuid); }
            public OutputStream write(String name, UUID uuid) throws IOException { return selected.write(name, uuid); }
            public boolean delete(String name, UUID uuid) throws IOException { return selected.delete(name, uuid); }
        };
    }

    /**
     * A lock remains strongly reachable in every holder/waiter's local variable until unlock.
     * Weak values therefore release retired identities without replacing any live lock.
     * Never use weak keys or size/age eviction: equal identities must share a live lock.
     */
    static ConcurrentMap<String, ReentrantLock> createPlayerDataLocks() {
        return new MapMaker().weakValues().makeMap();
    }

    private ReentrantLock playerDataLock(String key) {
        return playerDataLocks.computeIfAbsent(key, k -> new ReentrantLock());
    }

    public void saveOfflinePlayerData(UUID uuid, CompoundTag tag) {
        this.saveOfflinePlayerData(uuid, tag, false);
    }

    public void saveOfflinePlayerData(String name, CompoundTag tag) {
        this.saveOfflinePlayerData(name, tag, false);
    }

    public void saveOfflinePlayerData(UUID uuid, CompoundTag tag, boolean async) {
        this.saveOfflinePlayerData(uuid.toString(), tag, async);
    }

    public void saveOfflinePlayerData(String name, CompoundTag tag, boolean async) {
        if (this.savePlayerDataByUuid) {
            Optional<UUID> uuid = lookupName(name);
            saveOfflinePlayerData(uuid.map(UUID::toString).orElse(name), tag, async, true);
        } else {
            saveOfflinePlayerData(name, tag, async, true);
        }
    }

    private void saveOfflinePlayerData(String name, CompoundTag tag, boolean async, boolean runEvent) {
        if (!shouldSavePlayerData()) return;
        if (!async && profileQueue().isWorkerThread()) throw new IllegalStateException("Profile worker cannot synchronously save itself");
        if (!isProfileOwnerThread()) {
            AtomicInteger handoffs;
            synchronized (this) {
                if (profileCaptureHandoffs == null) profileCaptureHandoffs = new AtomicInteger();
                handoffs = profileCaptureHandoffs;
            }
            int occupied;
            do {
                occupied = handoffs.get();
                if (occupied >= 256) throw new RejectedExecutionException("Profile MAIN handoff capacity exhausted");
            } while (!handoffs.compareAndSet(occupied, occupied + 1));
            CompoundTag detached;
            try { detached = tag.copy(); }
            catch (RuntimeException | Error error) { handoffs.decrementAndGet(); throw error; }
            CompletableFuture<PlayerDataSerializer.WriteAcknowledgement> result = new CompletableFuture<>();
            if (async) result.whenComplete((ack, error) -> {
                if (error != null) log.error("Player profile capture failed for {}", name, profileFailure(error));
            });
            Task capture = new Task() {
                final AtomicBoolean ownsHandoff = new AtomicBoolean(true);
                @Override public void onRun(int tick) {
                    if (!ownsHandoff.compareAndSet(true, false)) return;
                    handoffs.decrementAndGet();
                    try {
                        admitPlayerData(name, detached, runEvent, () -> true).completion().whenComplete((ack, error) -> {
                            if (error == null) result.complete(ack); else result.completeExceptionally(error);
                        });
                    } catch (Throwable error) { result.completeExceptionally(error); }
                }
                @Override public void onCancel() {
                    if (!ownsHandoff.compareAndSet(true, false)) return;
                    handoffs.decrementAndGet();
                    result.completeExceptionally(new RejectedExecutionException("Profile MAIN handoff was cancelled"));
                }
            };
            try {
                var handler = getScheduler().scheduleTask(InternalPlugin.INSTANCE, capture);
                if (handler != null && handler.isCancelled()) capture.onCancel();
            } catch (RuntimeException | Error rejected) { capture.onCancel(); throw rejected; }
            if (!async) awaitProfileIO(result, false);
            return;
        }
        var admitted = admitPlayerData(name, tag, runEvent, () -> true);
        if (!async) awaitProfileIO(admitted.completion(), false);
    }

    /** Observable terminal storage result; UUID bypasses name lookup resolution. */
    public CompletionStage<PlayerDataSerializer.WriteAcknowledgement> saveOfflinePlayerDataAsync(UUID uuid, CompoundTag tag) {
        if (!isProfileOwnerThread()) return CompletableFuture.failedStage(new IllegalStateException("Profile capture requires its owner"));
        return admitPlayerData(uuid.toString(), tag, true, () -> true).completion();
    }

    record PreparedPlayerData(String key, CompoundTag snapshot, PlayerDataSerializer serializer, UUID uuid) { }

    PlayerDataSaveQueue.Admission<PlayerDataSerializer.WriteAcknowledgement> admitPlayerData(
            String name, CompoundTag tag, boolean runEvent, BooleanSupplier current) {
        return admitPreparedPlayerData(preparePlayerData(name, tag, runEvent, current));
    }

    PreparedPlayerData preparePlayerData(String name, CompoundTag tag, boolean runEvent, BooleanSupplier current) {
        if (!isProfileOwnerThread()) throw new IllegalStateException("Profile selection requires its owner");
        if (profileQueue().isWorkerThread()) throw new IllegalStateException("Profile IO callback cannot synchronously save itself");
        if (!shouldSavePlayerData()) throw new IllegalStateException("Player data persistence is disabled");
        String key = name.toLowerCase(Locale.ROOT);
        PlayerDataSerializeEvent event = new PlayerDataSerializeEvent(key, playerDataSerializer);
        if (runEvent) pluginManager.callEvent(event);
        if (shutdownTimedOut) throw new IllegalStateException("Player checkpoint admission exceeded the shutdown deadline");
        // A listener may close the native login or admit a newer write before this outer snapshot.
        if (!current.getAsBoolean()) throw new CancellationException("Profile capture was superseded by its listener");
        return new PreparedPlayerData(key, tag.copy(), event.getSerializer(), event.getUuid().orElse(null));
    }

    PlayerDataSaveQueue.Admission<PlayerDataSerializer.WriteAcknowledgement> admitPreparedPlayerData(PreparedPlayerData prepared) {
        if (!isProfileOwnerThread() || shutdownTimedOut) throw new IllegalStateException("Profile admission owner is unavailable");
        var admission = profileQueue().admit(prepared.key(), () -> writeSelectedProfile(
                prepared.serializer(), prepared.snapshot(), prepared.key(), prepared.uuid()));
        admission.completion().whenComplete((ack, error) -> {
            if (error != null) log.error("Player profile publication failed for {}", prepared.key(), profileFailure(error));
        });
        return admission;
    }

    private PlayerDataSerializer.WriteAcknowledgement writeSelectedProfile(
            PlayerDataSerializer serializer, CompoundTag tag, String name, UUID uuid) throws IOException {
        ReentrantLock lock = playerDataLock(name);
        lock.lock();
        try {
            if (serializer.getClass() == DefaultPlayerDataSerializer.class)
                return ((DefaultPlayerDataSerializer) serializer).writeAtomicallyAcknowledged(name, uuid, tag);
            try (OutputStream output = serializer.write(name, uuid)) { NBTIO.writeGZIPCompressed(tag, output, ByteOrder.BIG_ENDIAN); }
            return PlayerDataSerializer.WriteAcknowledgement.PROVIDER_ACK;
        } finally { lock.unlock(); }
    }

    <T> T awaitProfileIO(CompletionStage<T> result, boolean strict) {
        if (profileQueue().isWorkerThread()) throw new IllegalStateException("Profile worker cannot wait on its own FIFO");
        try { return result.toCompletableFuture().get(30, TimeUnit.SECONDS); }
        catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new IllegalStateException("Profile wait interrupted", error); }
        catch (ExecutionException error) {
            Throwable cause = profileFailure(error);
            if (cause instanceof Error fatal) throw fatal;
            if (strict || !(cause instanceof IOException)) throw new IllegalStateException("Profile publication failed", cause);
            return null; // Preserve the historical void API's logged IOException contract.
        } catch (TimeoutException error) { throw new IllegalStateException("Profile ACK deadline expired", error); }
    }

    private static Throwable profileFailure(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null) error = error.getCause();
        return error;
    }

    /**
     * Internal: Convert legacy player saves to the uuid based saving
     */
    private void convertLegacyPlayerData() {
        File dataDirectory = new File(getDataPath(), "players/");

        File[] files = dataDirectory.listFiles(file -> {
            String name = file.getName();
            return !uuidPattern.matcher(name).matches() && name.endsWith(".dat");
        });

        if (files == null) {
            return;
        }

        for (File legacyData : files) {
            String name = legacyData.getName();
            // Remove file extension
            name = name.substring(0, name.length() - 4);

            log.debug("Attempting legacy player data conversion for {}", name);

            CompoundTag tag = getOfflinePlayerDataInternal(name, false, false);

            if (tag == null || !tag.contains("UUIDLeast") || !tag.contains("UUIDMost")) {
                // No UUID so we cannot convert. Wait until player logs in.
                continue;
            }

            UUID uuid = new UUID(tag.getLong("UUIDMost"), tag.getLong("UUIDLeast"));
            if (!tag.contains("NameTag")) {
                tag.putString("NameTag", name);
            }

            if (new File(getDataPath() + "players/" + uuid.toString() + ".dat").exists()) {
                // We don't want to overwrite existing data.
                continue;
            }

            saveOfflinePlayerData(uuid.toString(), tag, false, false);

            // Add name to lookup table
            // 遗留按名数据无认证记录：按离线标记，保持可迁移
            // Legacy name-keyed data carries no auth record: mark it offline so it stays migratable
            updateName(uuid, name, false);

            // Delete legacy data
            if (!legacyData.delete()) {
                log.warn("Unable to delete legacy data for {}", name);
            }
        }
    }

    /**
     * Get an online player by name
     *
     * @param name player name
     * @return Player or null
     */
    public Player getPlayer(String name) {
        Player found = null;
        name = name.toLowerCase(Locale.ROOT);
        int delta = Integer.MAX_VALUE;
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(name)) {
                int curDelta = player.getName().length() - name.length();
                if (curDelta < delta) {
                    found = player;
                    delta = curDelta;
                }
                if (curDelta == 0) {
                    break;
                }
            }
        }

        return found;
    }

    /**
     * Get an online player by exact player name
     *
     * @param name exact player name
     * @return Player or null
     */
    public Player getPlayerExact(String name) {
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().equalsIgnoreCase(name)) {
                return player;
            }
        }

        return null;
    }

    /**
     * Get players that match with the name
     *
     * @param partialName name
     * @return matching players
     */
    public Player[] matchPlayer(String partialName) {
        partialName = partialName.toLowerCase(Locale.ROOT);
        List<Player> matchedPlayer = new ArrayList<>();
        for (Player player : this.getOnlinePlayers().values()) {
            if (player.getName().toLowerCase(Locale.ROOT).equals(partialName)) {
                return new Player[]{player};
            } else if (player.getName().toLowerCase(Locale.ROOT).contains(partialName)) {
                matchedPlayer.add(player);
            }
        }

        return matchedPlayer.toArray(Player.EMPTY_ARRAY);
    }

    /**
     * Internal: Remove a player from the server
     *
     * @param player player
     */
    public void removePlayer(Player player) {
        if (this.players.remove(player.getRawSocketAddress()) != null) {
            return;
        }

        for (InetSocketAddress socketAddress : new ArrayList<>(this.players.keySet())) {
            if (player == this.players.get(socketAddress)) {
                this.players.remove(socketAddress);
                break;
            }
        }
    }

    /**
     * Get all levels
     *
     * @return levels
     */
    public Map<Integer, Level> getLevels() {
        return levels;
    }

    /**
     * Get default level
     *
     * @return default level
     */
    public Level getDefaultLevel() {
        return defaultLevel;
    }

    /**
     * 获取全服共享的客户端缓存 blob 仓库：内容寻址字节预算 LRU，跨世界去重。
     * <p>
     * Gets the server-wide shared client cache blob store: content-addressed,
     * byte-budget LRU, deduplicated across all levels.
     */
    public ClientCacheBlobStore getClientCacheBlobStore() {
        if (this.clientCacheBlobStore == null) {
            this.clientCacheBlobStore = new ClientCacheBlobStore(
                    Math.max(1, this.getServerConfig().chunkSettings().blobStoreMaxMb()) * 1024L * 1024L);
        }
        return this.clientCacheBlobStore;
    }

    /**
     * Change the default level
     *
     * @param defaultLevel new default level
     */
    public void setDefaultLevel(Level defaultLevel) {
        if (defaultLevel == null || (this.isLevelLoaded(defaultLevel.getFolderName()) && defaultLevel != this.defaultLevel)) {
            this.defaultLevel = defaultLevel;
        }
    }

    /**
     * Check whether a level is loaded
     *
     * @param name level name
     * @return is loaded
     */
    public boolean isLevelLoaded(String name) {
        return this.getLevelByName(name) != null;
    }

    /**
     * Get a level by ID
     *
     * @param levelId level ID
     * @return Level or null
     */
    public Level getLevel(int levelId) {
        if (this.levels.containsKey(levelId)) {
            return this.levels.get(levelId);
        }
        return null;
    }

    /**
     * Get a level by name
     *
     * @param name level name
     * @return Level or null
     */
    public Level getLevelByName(String name) {
        for (Level level : this.levelArray) {
            if (level.getFolderName().equalsIgnoreCase(name)) {
                return level;
            }
        }

        if (name != null && (name.contains("/") || name.contains("\\"))) {
            return this.findLevelByPath(this.resolveLevelFile(name));
        }

        return null;
    }

    /**
     * Unload a level
     *
     * Notice: the default level cannot be unloaded without forceUnload=true
     *
     * @param level Level
     * @return unloaded
     */
    public boolean unloadLevel(Level level) {
        return this.unloadLevel(level, false);
    }

    /**
     * Unload a level
     *
     * Notice: the default level cannot be unloaded without forceUnload=true
     *
     * @param level Level
     * @param forceUnload force unload (ignore cancelled events and default level)
     * @return unloaded
     */
    public boolean unloadLevel(Level level, boolean forceUnload) {
        if (level == this.defaultLevel && !forceUnload) {
            throw new IllegalStateException("The default level cannot be unloaded while running, please switch levels.");
        }

        return level.unload(forceUnload);
    }

    /**
     * Load or generate worlds defined in the per-world config section.
     */
    private void loadCustomWorlds() {
        Map<String, WorldEntry> worlds = this.serverConfig.worldSettings().worlds();
        if (worlds == null || worlds.isEmpty()) {
            return;
        }
        for (Map.Entry<String, WorldEntry> entry : worlds.entrySet()) {
            String worldName = entry.getKey();
            if (worldName == null || worldName.trim().isEmpty()) {
                continue;
            }
            if (this.isLevelLoaded(worldName)) {
                continue;
            }
            WorldEntry worldEntry = entry.getValue();
            if (worldEntry == null) {
                worldEntry = new WorldEntry();
            }
            if (this.isLevelGenerated(worldName)) {
                this.loadLevel(worldName);
            } else {
                long seed = worldEntry.seed() != 0 ? worldEntry.seed() : System.currentTimeMillis();
                Class<? extends Generator> generator = Generator.getGenerator(worldEntry.generator());
                Map<String, Object> options = new HashMap<>();
                String settings = worldEntry.generatorSettings();
                if (settings != null && !settings.isEmpty()) {
                    options.put("preset", settings);
                }
                this.generateLevel(worldName, seed, generator, options);
            }
        }
    }

    /**
     * Load a level by name
     *
     * @param name level name
     * @return loaded
     */
    public boolean loadLevel(String name) {
        if (Objects.equals(name.trim(), "")) {
            throw new LevelException("Invalid empty level name");
        }

        // Canonical path so equivalent spellings (relative/absolute/symlink) match.
        File resolved = this.resolveLevelFile(name);

        // Raw-name lookup for backwards compat; path check below is authoritative.
        if (this.isLevelLoaded(name) || this.isLevelPathLoaded(resolved)) {
            return true;
        }

        String path = resolved.getPath() + '/';

        if (!this.isLevelGenerated(name)) {
            log.warn(this.baseLang.translateString("nukkit.level.notFound", name));
            log.warn(this.baseLang.translateString("nukkit.level.notFoundHint", new String[]{path, diagnoseLevelPath(path)}));
            return false;
        }

        Class<? extends LevelProvider> provider = LevelProviderManager.getProvider(path);

        if (provider == null) {
            log.error(this.baseLang.translateString("nukkit.level.loadError", new String[]{name, "Unknown provider"}));
            return false;
        }

        // Last path segment keeps folderName clean when a path was passed.
        String folderName = resolved.getName();

        Level level;
        try {
            level = new Level(this, folderName, path, provider);
        } catch (Exception e) {
            log.error(this.baseLang.translateString("nukkit.level.loadError", new String[]{name, e.getMessage()}));
            return false;
        }

        this.levels.put(level.getId(), level);

        level.initLevel();

        level.setTickRate(this.baseTickRate);

        this.pluginManager.callEvent(new LevelLoadEvent(level));
        return true;
    }

    /**
     * Resolve a level name or path to a canonical world directory, so
     * equivalent spellings (plain name, relative/absolute path, symlink)
     * always match. Falls back to the absolute path on canonicalization
     * failure to avoid throwing {@link IOException}.
     */
    private File resolveLevelFile(String name) {
        File f = (name.contains("/") || name.contains("\\"))
                ? new File(name)
                : new File(this.dataPath + "worlds/" + name);
        try {
            return f.getCanonicalFile();
        } catch (IOException e) {
            return f.getAbsoluteFile();
        }
    }

    /**
     * Whether a world directory is already loaded, matched by canonical
     * provider path rather than folderName, so a world loaded by plain
     * name is recognized when re-requested via an equivalent path.
     */
    private boolean isLevelPathLoaded(File resolved) {
        return this.findLevelByPath(resolved) != null;
    }

    /**
     * 按规范化路径匹配已加载的世界，供 loadLevel 去重和 getLevelByName
     * 的路径回退共用，保证两端用同一套比对逻辑。
     * <p>Match an already-loaded level by canonical provider path, shared by
     * loadLevel's dedup check and getLevelByName's path fallback.
     */
    private Level findLevelByPath(File resolved) {
        for (Level level : this.levelArray) {
            String providerPath;
            try {
                providerPath = level.requireProvider().getPath();
            } catch (Exception ignored) {
                continue;
            }
            File loaded;
            try {
                loaded = new File(providerPath).getCanonicalFile();
            } catch (IOException e) {
                loaded = new File(providerPath).getAbsoluteFile();
            }
            if (loaded.equals(resolved)) {
                return level;
            }
        }
        return null;
    }

    /**
     * Explain why a level directory was rejected by the providers, so the
     * "not found" warning points at the concrete cause (missing dir, missing
     * level.dat, missing data folder, or unrecognized region files).
     */
    private static String diagnoseLevelPath(String path) {
        File dir = new File(path);
        if (!dir.exists() || !dir.isDirectory()) {
            return "directory does not exist";
        }
        if (!new File(dir, "level.dat").exists()) {
            return "missing level.dat";
        }
        if (new File(dir, "db").isDirectory()) {
            return "leveldb folder present but provider rejected it";
        }
        File region = new File(dir, "region");
        if (!region.isDirectory()) {
            return "missing db/ or region/ data folder";
        }
        return "region folder present but no valid .mca files";
    }

    /**
     * Generate a new level
     *
     * @param name level name
     * @return generated
     */
    public boolean generateLevel(String name) {
        return this.generateLevel(name, Utils.random.nextLong());
    }

    /**
     * Generate a new level
     *
     * @param name level name
     * @param seed level seed
     * @return generated
     */
    public boolean generateLevel(String name, long seed) {
        return this.generateLevel(name, seed, null);
    }

    /**
     * Generate a new level
     *
     * @param name level name
     * @param seed level seed
     * @param generator level generator
     * @return generated
     */
    public boolean generateLevel(String name, long seed, Class<? extends Generator> generator) {
        return this.generateLevel(name, seed, generator, new HashMap<>());
    }

    /**
     * Generate a new level
     *
     * @param name level name
     * @param seed level seed
     * @param generator level generator
     * @param options level generator options
     * @return generated
     */
    public boolean generateLevel(String name, long seed, Class<? extends Generator> generator, Map<String, Object> options) {
        return generateLevel(name, seed, generator, options, null);
    }

    /**
     * Generate a new level
     *
     * @param name level name
     * @param seed level seed
     * @param generator level generator
     * @param options level generator options
     * @param provider level provider
     * @return generated
     */
    public boolean generateLevel(String name, long seed, Class<? extends Generator> generator, Map<String, Object> options, Class<? extends LevelProvider> provider) {
        if (Objects.equals(name.trim(), "") || this.isLevelGenerated(name)) {
            return false;
        }

        if (!options.containsKey("preset")) {
            options.put("preset", this.getPropertyString("generator-settings", ""));
        }

        if (generator == null) {
            generator = Generator.getGenerator(this.getLevelType());
        }

        if (provider == null) {
            provider = LevelProviderManager.getProviderByName("leveldb");
        }

        // Canonical path: dedupes equivalent spellings and keeps folderName clean.
        File resolved = this.resolveLevelFile(name);
        String path = resolved.getPath() + '/';
        String folderName = resolved.getName();

        Level level;
        try {
            provider.getMethod("generate", String.class, String.class, long.class, Class.class, Map.class).invoke(null, path, folderName, seed, generator, options);

            level = new Level(this, folderName, path, provider);
            this.levels.put(level.getId(), level);

            level.initLevel();

            level.setTickRate(this.baseTickRate);
        } catch (Exception e) {
            log.error(this.baseLang.translateString("nukkit.level.generationError", new String[]{name, Utils.getExceptionMessage(e)}));
            return false;
        }

        this.pluginManager.callEvent(new LevelInitEvent(level));
        this.pluginManager.callEvent(new LevelLoadEvent(level));
        return true;
    }

    /**
     * Check whether a level by name is generated
     *
     * @param name level name
     * @return level found
     */
    public boolean isLevelGenerated(String name) {
        if (Objects.equals(name.trim(), "")) {
            return false;
        }

        if (this.getLevelByName(name) == null) {
            String path = this.resolveLevelFile(name).getPath() + '/';
            return LevelProviderManager.getProvider(path) != null;
        }

        return true;
    }

    /**
     * Get BaseLang (server's default language)
     *
     * @return BaseLang
     */
    public BaseLang getLanguage() {
        return baseLang;
    }

    /**
     * Is forcing language enabled
     *
     * @return force-language enabled
     */
    public boolean isLanguageForced() {
        return forceLanguage;
    }

    /**
     * Get Network
     *
     * @return Network
     */
    public Network getNetwork() {
        return network;
    }

    /**
     * Load server configuration using OkaeriConfig (YAML format)
     *
     * @param firstLoad true on initial startup, false on reload
     */
    private void loadServerConfig(boolean firstLoad) {
        try {
            File configFile = new File(this.dataPath, "nukkit-mot.yml");
            ConfigMigration.migrateYamlKeys(configFile);
            ServerConfig newConfig = ConfigManager.create(ServerConfig.class, (it) -> {
                it.configure(opt -> {
                    opt.configurer(new YamlSnakeYamlConfigurer());
                    opt.bindFile(configFile);
                    opt.removeOrphans(true);
                });
                if (firstLoad) {
                    it.saveDefaults();
                }
                it.load(true);
            });

            // Apply localized comments based on server language
            String lang = this.getPropertyString("language", "eng");
            ConfigComments.apply(newConfig, lang);
            newConfig.save();

            this.serverConfig = newConfig;
            if (firstLoad) {
                log.info("Server configuration loaded from nukkit-mot.yml");
            }
        } catch (Exception e) {
            logConfigError(e);
            if (firstLoad) {
                log.error("Server cannot start with an invalid configuration. Please fix nukkit-mot.yml and restart.");
                Nukkit.shutdownLogging();
                System.exit(1);
            } else {
                log.error("Failed to reload nukkit-mot.yml. Keeping previous configuration.");
            }
        }
    }

    /**
     * Load server configuration (initial startup)
     */
    private void loadServerConfig() {
        this.loadServerConfig(true);
    }

    private void logConfigError(Exception e) {
        // Search the exception chain for specific error types
        org.yaml.snakeyaml.error.MarkedYAMLException yamlEx = null;
        eu.okaeri.configs.exception.OkaeriConfigException configEx = null;
        for (Throwable t = e; t != null && t != t.getCause(); t = t.getCause()) {
            if (yamlEx == null && t instanceof org.yaml.snakeyaml.error.MarkedYAMLException marked) {
                yamlEx = marked;
            }
            if (configEx == null && t instanceof eu.okaeri.configs.exception.OkaeriConfigException okaeri) {
                configEx = okaeri;
            }
        }

        if (configEx != null) {
            log.error("nukkit-mot.yml configuration error:\n{}", configEx.getMessage());
        } else if (yamlEx != null) {
            log.error("nukkit-mot.yml YAML syntax error: {}", yamlEx.getMessage());
        } else {
            log.error("Failed to load nukkit-mot.yml: {}", e.getMessage());
        }

        log.debug("Configuration load exception details:", e);
    }

    /**
     * Get the server configuration (OkaeriConfig)
     *
     * @return ServerConfig instance
     */
    public ServerConfig getServerConfig() {
        return this.serverConfig;
    }

    /**
     * Save server configuration to nukkit-mot.yml
     */
    public void saveServerConfig() {
        if (this.serverConfig != null) {
            try {
                this.serverConfig.save();
                log.debug("Server configuration saved to nukkit-mot.yml");
            } catch (Exception e) {
                log.error("Failed to save nukkit-mot.yml", e);
            }
        }
    }

    /**
     * 按当前语言设置刷新 server.properties 的逐键注释并保存
     * <p>
     * Refresh per-key comments in server.properties for the current language setting, then save.
     * Keys unknown to the defaults are kept at the end of the file behind a localized notice.
     */
    private void applyPropertiesComments() {
        String lang = this.getPropertyString("language", "eng");
        this.properties.setPropertyComments(ConfigComments.loadPropertyComments(lang));
        this.properties.setUnrecognizedPropertyComment(ConfigComments.loadUnrecognizedPropertyComment(lang));
        this.properties.save();
    }


    /**
     * Get server.properties config
     *
     * @return Config instance
     */
    public Config getProperties() {
        return this.properties;
    }

    public Object getProperty(String variable) {
        return this.getProperty(variable, null);
    }

    public Object getProperty(String variable, Object defaultValue) {
        return this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
    }

    public String getPropertyString(String key) {
        return this.getPropertyString(key, null);
    }

    public String getPropertyString(String key, String defaultValue) {
        return this.properties.exists(key) ? this.properties.get(key).toString() : defaultValue;
    }

    public void setPropertyString(String variable, String value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    public int getPropertyInt(String variable) {
        return this.getPropertyInt(variable, null);
    }

    public int getPropertyInt(String variable, Integer defaultValue) {
        Object value = this.properties.get(variable);
        if (value == null) {
            value = defaultValue;
        }
        if (value instanceof Integer) {
            return (Integer) value;
        }
        String trimmed = String.valueOf(value).trim();
        if (trimmed.isEmpty()) {
            return defaultValue;
        }
        return Integer.parseInt(trimmed);
    }

    public void setPropertyInt(String variable, int value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    public boolean getPropertyBoolean(String variable) {
        return this.getPropertyBoolean(variable, null);
    }

    public boolean getPropertyBoolean(String variable, Object defaultValue) {
        Object value = this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        return switch (String.valueOf(value).trim().toLowerCase(Locale.ROOT)) {
            case "on", "true", "1", "yes" -> true;
            default -> false;
        };
    }

    public void setPropertyBoolean(String variable, boolean value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    /**
     * Get plugin commands
     *
     * @param name command name
     * @return PluginIdentifiableCommand or null
     */
    public PluginIdentifiableCommand getPluginCommand(String name) {
        Command command = this.commandMap.getCommand(name);
        if (command instanceof PluginIdentifiableCommand) {
            return (PluginIdentifiableCommand) command;
        } else {
            return null;
        }
    }

    /**
     * Get list of banned players
     *
     * @return ban list
     */
    public BanList getNameBans() {
        return this.banByName;
    }

    /**
     * Get list of IP bans
     *
     * @return IP bans
     */
    public BanList getIPBans() {
        return this.banByIP;
    }

    /**
     * Give player the operator status
     *
     * @param name player name
     */
    public void addOp(String name) {
        this.operators.set(name.toLowerCase(Locale.ROOT), true);
        Player player = this.getPlayerExact(name);
        if (player != null) {
            player.recalculatePermissions();
        }
        this.operators.save(true);
    }

    /**
     * Remove player's operator status
     *
     * @param name player name
     */
    public void removeOp(String name) {
        this.operators.remove(name.toLowerCase(Locale.ROOT));
        Player player = this.getPlayerExact(name);
        if (player != null) {
            player.recalculatePermissions();
        }
        this.operators.save();
    }

    /**
     * Add a player to whitelist
     *
     * @param name player name
     */
    public void addWhitelist(String name) {
        this.whitelist.set(name.toLowerCase(Locale.ROOT), true);
        this.whitelist.save(true);
    }

    /**
     * Remove a player from whitelist
     *
     * @param name player name
     */
    public void removeWhitelist(String name) {
        this.whitelist.remove(name.toLowerCase(Locale.ROOT));
        this.whitelist.save(true);
    }

    /**
     * @return true when a duplicate login refuses the newcomer and keeps the session already in the world
     */
    public boolean isDuplicateLoginKeepingExistingSession() {
        return this.keepExistingSessionOnDuplicateLogin;
    }

    /**
     * Check whether a player is whitelisted
     *
     * @param name player name
     * @return is whitelisted or whitelist is not enabled
     */
    public boolean isWhitelisted(String name) {
        return !this.hasWhitelist() || this.operators.exists(name, true) || this.whitelist.exists(name, true);
    }

    public void setWhitelisted(boolean value) {
        whitelistEnabled = value;
    }

    /**
     * Check whether a player is an operator
     *
     * @param name player name
     * @return is operator
     */
    public boolean isOp(String name) {
        return name != null && this.operators.exists(name, true);
    }

    /**
     * Get whitelist config
     *
     * @return whitelist
     */
    public Config getWhitelist() {
        return whitelist;
    }

    /**
     * Get operator list config
     *
     * @return operators
     */
    public Config getOps() {
        return operators;
    }

    /**
     * Reload whitelist
     */
    public void reloadWhitelist() {
        this.whitelist.reload();
    }

    /**
     * Get service manager
     *
     * @return service manager
     */
    public ServiceManager getServiceManager() {
        return serviceManager;
    }

    /**
     * Should player data saving be enabled
     *
     * @return player data saving enabled
     */
    public boolean shouldSavePlayerData() {
        return shouldSavePlayerData;
    }

    /**
     * How often player is allowed to change skin in game (in seconds)
     *
     * @return skin change cooldown
     */
    public int getPlayerSkinChangeCooldown() {
        return skinChangeCooldown;
    }

    /**
     * Get nether world for a level
     *
     * @param world level
     * @return nether world for that level
     */
    public Level getNetherWorld(String world) {
        return multiNetherWorlds.contains(world) ? this.getLevelByName(world + "-nether") : this.getLevelByName("nether");
    }

    /**
     * Sort players by protocol version
     *
     * @param players players
     * @return players sorted by protocol
     */
    @Deprecated
    public static Int2ObjectMap<ObjectList<Player>> sortPlayers(Player[] players) {
        Int2ObjectMap<ObjectList<Player>> targets = new Int2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.protocol, i -> new ObjectArrayList<>()).add(player);
        }
        return targets;
    }

    /**
     * Sort players by protocol version
     *
     * @param players players
     * @return players sorted by protocol
     */
    @Deprecated
    public static Int2ObjectMap<ObjectList<Player>> sortPlayers(Collection<Player> players) {
        Int2ObjectMap<ObjectList<Player>> targets = new Int2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.protocol, i -> new ObjectArrayList<>()).add(player);
        }
        return targets;
    }

    /**
     * Group players by game version
     *
     * @param players players
     * @return players grouped by game version
     */
    public static Object2ObjectMap<GameVersion, ObjectList<Player>> groupPlayersByGameVersion(Player[] players) {
        Object2ObjectMap<GameVersion, ObjectList<Player>> targets = new Object2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.getGameVersion(), i -> new ObjectArrayList<>()).add(player);
        }
        return targets;
    }

    /**
     * Group players by game version
     *
     * @param players players
     * @return players grouped by game version
     */
    public static Object2ObjectMap<GameVersion, ObjectList<Player>> groupPlayersByGameVersion(Collection<Player> players) {
        Object2ObjectMap<GameVersion, ObjectList<Player>> targets = new Object2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.getGameVersion(), i -> new ObjectArrayList<>()).add(player);
        }
        return targets;
    }

    /**
     * Checks the current thread against the expected primary thread for the server.
     *
     * <b>Note:</b> this method should not be used to indicate the current synchronized state of the runtime. A current thread matching the main thread indicates that it is synchronized, but a mismatch does not preclude the same assumption.
     *
     * @return true if the current thread matches the expected primary thread, false otherwise
     */
    public boolean isPrimaryThread() {
        return (Thread.currentThread() == currentThread);
    }

    /**
     * Get server's primary thread
     *
     * @return primary thread
     */
    public Thread getPrimaryThread() {
        return currentThread;
    }

    private void registerProfessions() {
        Profession.init();
    }

    /**
     * Internal method to register all default entities
     */
    private static void registerEntities() {
        //Items
        Entity.registerEntity("Item", EntityItem.class);
        Entity.registerEntity("Painting", EntityPainting.class);
        Entity.registerEntity("XpOrb", EntityXPOrb.class);
        Entity.registerEntity("ArmorStand", EntityArmorStand.class);
        Entity.registerEntity("EndCrystal", EntityEndCrystal.class);
        Entity.registerEntity("FallingSand", EntityFallingBlock.class);
        Entity.registerEntity("PrimedTnt", EntityPrimedTNT.class);
        Entity.registerEntity("Firework", EntityFirework.class);
        //Projectiles
        Entity.registerEntity("Arrow", EntityArrow.class);
        Entity.registerEntity("Snowball", EntitySnowball.class);
        Entity.registerEntity("EnderPearl", EntityEnderPearl.class);
        Entity.registerEntity("EnderEye", EntityEnderEye.class);
        Entity.registerEntity("ThrownExpBottle", EntityExpBottle.class);
        Entity.registerEntity("ThrownPotion", EntityPotion.class);
        Entity.registerEntity("Egg", EntityEgg.class);
        Entity.registerEntity("SmallFireBall", EntitySmallFireBall.class);
        // 和原版名称不一样，已弃用
        // The name is different from the vanilla version and has been deprecated
        Entity.registerEntity("BlazeFireBall", EntityBlazeFireBall.class);
        Entity.registerEntity("GhastFireBall", EntityGhastFireBall.class);
        Entity.registerEntity("ShulkerBullet", EntityShulkerBullet.class);
        Entity.registerEntity("ThrownLingeringPotion", EntityPotionLingering.class);
        Entity.registerEntity("ThrownTrident", EntityThrownTrident.class);
        Entity.registerEntity("WitherSkull", EntityWitherSkull.class);
        Entity.registerEntity("BlueWitherSkull", EntityBlueWitherSkull.class);
        Entity.registerEntity("LlamaSpit", EntityLlamaSpit.class);
        Entity.registerEntity("EvocationFangs", EntityEvocationFangs.class);
        Entity.registerEntity("EnderCharge", EntityEnderCharge.class);
        Entity.registerEntity("FishingHook", EntityFishingHook.class);
        //Monsters
        Entity.registerEntity("Blaze", EntityBlaze.class);
        Entity.registerEntity("Creeper", EntityCreeper.class);
        Entity.registerEntity("CaveSpider", EntityCaveSpider.class);
        Entity.registerEntity("Drowned", EntityDrowned.class);
        Entity.registerEntity("ElderGuardian", EntityElderGuardian.class);
        Entity.registerEntity("EnderDragon", EntityEnderDragon.class);
        Entity.registerEntity("Enderman", EntityEnderman.class);
        Entity.registerEntity("Endermite", EntityEndermite.class);
        Entity.registerEntity("Evoker", EntityEvoker.class);
        Entity.registerEntity("Ghast", EntityGhast.class);
        Entity.registerEntity("Guardian", EntityGuardian.class);
        Entity.registerEntity("Husk", EntityHusk.class);
        Entity.registerEntity("MagmaCube", EntityMagmaCube.class);
        Entity.registerEntity("Phantom", EntityPhantom.class);
        Entity.registerEntity("Ravager", EntityRavager.class);
        Entity.registerEntity("Shulker", EntityShulker.class);
        Entity.registerEntity("Silverfish", EntitySilverfish.class);
        Entity.registerEntity("Skeleton", EntitySkeleton.class);
        Entity.registerEntity("SkeletonHorse", EntitySkeletonHorse.class);
        Entity.registerEntity("Slime", EntitySlime.class);
        Entity.registerEntity("Spider", EntitySpider.class);
        Entity.registerEntity("Stray", EntityStray.class);
        Entity.registerEntity("Vindicator", EntityVindicator.class);
        Entity.registerEntity("Warden", EntityWarden.class);
        Entity.registerEntity("Vex", EntityVex.class);
        Entity.registerEntity("WitherSkeleton", EntityWitherSkeleton.class);
        Entity.registerEntity("Wither", EntityWither.class);
        Entity.registerEntity("Witch", EntityWitch.class);
        Entity.registerEntity("ZombiePigman", EntityZombiePigman.class);
        Entity.registerEntity("ZombieVillager", EntityZombieVillager.class);
        Entity.registerEntity("Zombie", EntityZombie.class);
        Entity.registerEntity("Pillager", EntityPillager.class);
        Entity.registerEntity("ZombieVillagerV2", EntityZombieVillagerV2.class);
        Entity.registerEntity("Hoglin", EntityHoglin.class);
        Entity.registerEntity("Piglin", EntityPiglin.class);
        Entity.registerEntity("Zoglin", EntityZoglin.class);
        Entity.registerEntity("PiglinBrute", EntityPiglinBrute.class);
        Entity.registerEntity("Breeze", EntityBreeze.class);
        Entity.registerEntity("Bogged", EntityBogged.class);
        Entity.registerEntity("Creaking", EntityCreaking.class);
        //Passive
        Entity.registerEntity("Bat", EntityBat.class);
        Entity.registerEntity("Cat", EntityCat.class);
        Entity.registerEntity("Chicken", EntityChicken.class);
        Entity.registerEntity("Cod", EntityCod.class);
        Entity.registerEntity("Cow", EntityCow.class);
        Entity.registerEntity("Dolphin", EntityDolphin.class);
        Entity.registerEntity("Donkey", EntityDonkey.class);
        Entity.registerEntity("Horse", EntityHorse.class);
        Entity.registerEntity("IronGolem", EntityIronGolem.class);
        Entity.registerEntity("Llama", EntityLlama.class);
        Entity.registerEntity("Mooshroom", EntityMooshroom.class);
        Entity.registerEntity("Mule", EntityMule.class);
        Entity.registerEntity("Panda", EntityPanda.class);
        Entity.registerEntity("Parrot", EntityParrot.class);
        Entity.registerEntity("PolarBear", EntityPolarBear.class);
        Entity.registerEntity("Pig", EntityPig.class);
        Entity.registerEntity("Pufferfish", EntityPufferfish.class);
        Entity.registerEntity("Rabbit", EntityRabbit.class);
        Entity.registerEntity("Salmon", EntitySalmon.class);
        Entity.registerEntity("Sheep", EntitySheep.class);
        Entity.registerEntity("Squid", EntitySquid.class);
        Entity.registerEntity("SnowGolem", EntitySnowGolem.class);
        Entity.registerEntity("TropicalFish", EntityTropicalFish.class);
        Entity.registerEntity("Turtle", EntityTurtle.class);
        Entity.registerEntity("Wolf", EntityWolf.class);
        Entity.registerEntity("Ocelot", EntityOcelot.class);
        Entity.registerEntity("Villager", EntityVillager.class);
        Entity.registerEntity("ZombieHorse", EntityZombieHorse.class);
        Entity.registerEntity("WanderingTrader", EntityWanderingTrader.class);
        Entity.registerEntity("VillagerV2", EntityVillagerV2.class);
        Entity.registerEntity("Fox", EntityFox.class);
        Entity.registerEntity("Frog", EntityFrog.class);
        Entity.registerEntity("Goat", EntityGoat.class);
        Entity.registerEntity("Bee", EntityBee.class);
        Entity.registerEntity("Strider", EntityStrider.class);
        Entity.registerEntity("Tadpole", EntityTadpole.class);
        Entity.registerEntity("Axolotl", EntityAxolotl.class);
        Entity.registerEntity("GlowSquid", EntityGlowSquid.class);
        Entity.registerEntity("Allay", EntityAllay.class);
        Entity.registerEntity("Npc", EntityNPCEntity.class);
        Entity.registerEntity("Camel", EntityCamel.class);
        Entity.registerEntity("HappyGhast", EntityHappyGhast.class);
        Entity.registerEntity("CopperGolem", EntityCopperGolem.class);
        Entity.registerEntity("Sniffer", EntitySniffer.class);
        Entity.registerEntity("Armadillo", EntityArmadillo.class);
        //Vehicles
        Entity.registerEntity("MinecartRideable", EntityMinecartEmpty.class);
        Entity.registerEntity("MinecartChest", EntityMinecartChest.class);
        Entity.registerEntity("MinecartHopper", EntityMinecartHopper.class);
        Entity.registerEntity("MinecartTnt", EntityMinecartTNT.class);
        Entity.registerEntity("MinecartCommandBlock", EntityMinecartCommandBlock.class);
        Entity.registerEntity("Boat", EntityBoat.class);
        Entity.registerEntity("ChestBoat", EntityChestBoat.class);
        //Others
        Entity.registerEntity("Human", EntityHuman.class, true);
        Entity.registerEntity("Lightning", EntityLightning.class);
        Entity.registerEntity("AreaEffectCloud", EntityAreaEffectCloud.class);

        Entity.registerEntity("WindCharge", EntityWindCharge.class);
        Entity.registerEntity("BreezeWindCharge", EntityBreezeWindCharge.class);
    }

    /**
     * Internal method to register all default block entities
     */
    private static void registerBlockEntities() {
        BlockEntity.registerBlockEntity(BlockEntity.FURNACE, BlockEntityFurnace.class);
        BlockEntity.registerBlockEntity(BlockEntity.BLAST_FURNACE, BlockEntityBlastFurnace.class);
        BlockEntity.registerBlockEntity(BlockEntity.SMOKER, BlockEntitySmoker.class);
        BlockEntity.registerBlockEntity(BlockEntity.CHEST, BlockEntityChest.class);
        BlockEntity.registerBlockEntity(BlockEntity.SIGN, BlockEntitySign.class);
        BlockEntity.registerBlockEntity(BlockEntity.HANGING_SIGN, BlockEntityHangingSign.class);
        BlockEntity.registerBlockEntity(BlockEntity.ENCHANT_TABLE, BlockEntityEnchantTable.class);
        BlockEntity.registerBlockEntity(BlockEntity.SKULL, BlockEntitySkull.class);
        BlockEntity.registerBlockEntity(BlockEntity.FLOWER_POT, BlockEntityFlowerPot.class);
        BlockEntity.registerBlockEntity(BlockEntity.BREWING_STAND, BlockEntityBrewingStand.class);
        BlockEntity.registerBlockEntity(BlockEntity.ITEM_FRAME, BlockEntityItemFrame.class);
        BlockEntity.registerBlockEntity(BlockEntity.GLOW_ITEM_FRAME, BlockEntityItemFrameGlow.class);
        BlockEntity.registerBlockEntity(BlockEntity.CAULDRON, BlockEntityCauldron.class);
        BlockEntity.registerBlockEntity(BlockEntity.ENDER_CHEST, BlockEntityEnderChest.class);
        BlockEntity.registerBlockEntity(BlockEntity.BEACON, BlockEntityBeacon.class);
        BlockEntity.registerBlockEntity(BlockEntity.PISTON_ARM, BlockEntityPistonArm.class);
        BlockEntity.registerBlockEntity(BlockEntity.COMPARATOR, BlockEntityComparator.class);
        BlockEntity.registerBlockEntity(BlockEntity.HOPPER, BlockEntityHopper.class);
        BlockEntity.registerBlockEntity(BlockEntity.BED, BlockEntityBed.class);
        BlockEntity.registerBlockEntity(BlockEntity.JUKEBOX, BlockEntityJukebox.class);
        BlockEntity.registerBlockEntity(BlockEntity.SHULKER_BOX, BlockEntityShulkerBox.class);
        BlockEntity.registerBlockEntity(BlockEntity.BANNER, BlockEntityBanner.class);
        BlockEntity.registerBlockEntity(BlockEntity.DROPPER, BlockEntityDropper.class);
        BlockEntity.registerBlockEntity(BlockEntity.DISPENSER, BlockEntityDispenser.class);
        BlockEntity.registerBlockEntity(BlockEntity.MOB_SPAWNER, BlockEntitySpawner.class);
        BlockEntity.registerBlockEntity(BlockEntity.MUSIC, BlockEntityMusic.class);
        BlockEntity.registerBlockEntity(BlockEntity.LECTERN, BlockEntityLectern.class);
        BlockEntity.registerBlockEntity(BlockEntity.BEEHIVE, BlockEntityBeehive.class);
        BlockEntity.registerBlockEntity(BlockEntity.CAMPFIRE, BlockEntityCampfire.class);
        BlockEntity.registerBlockEntity(BlockEntity.BELL, BlockEntityBell.class);
        BlockEntity.registerBlockEntity(BlockEntity.BARREL, BlockEntityBarrel.class);
        BlockEntity.registerBlockEntity(BlockEntity.MOVING_BLOCK, BlockEntityMovingBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.END_GATEWAY, BlockEntityEndGateway.class);
        BlockEntity.registerBlockEntity(BlockEntity.DECORATED_POT, BlockEntityDecoratedPot.class);
        BlockEntity.registerBlockEntity(BlockEntity.TARGET, BlockEntityTarget.class);
        BlockEntity.registerBlockEntity(BlockEntity.BRUSHABLE_BLOCK, BlockEntityBrushableBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.CONDUIT, BlockEntityConduit.class);
        BlockEntity.registerBlockEntity(BlockEntity.POTENT_SULFUR, BlockEntityPotentSulfur.class);
        BlockEntity.registerBlockEntity(BlockEntity.CHISELED_BOOKSHELF, BlockEntityChiseledBookshelf.class);
        BlockEntity.registerBlockEntity(BlockEntity.CRAFTER, BlockEntityCrafter.class);
        BlockEntity.registerBlockEntity(BlockEntity.SHELF, BlockEntityShelf.class);
        BlockEntity.registerBlockEntity(BlockEntity.COPPER_GOLEM_STATUE, BlockEntityCopperGolemStatue.class);
        BlockEntity.registerBlockEntity(BlockEntity.CREAKING_HEART, BlockEntityCreakingHeart.class);
        BlockEntity.registerBlockEntity(BlockEntity.COMMAND_BLOCK, BlockEntityCommandBlock.class);
        BlockEntity.registerBlockEntity(BlockEntity.SCULK_SENSOR, BlockEntitySculkSensor.class);
        BlockEntity.registerBlockEntity(BlockEntity.CALIBRATED_SCULK_SENSOR, BlockEntityCalibratedSculkSensor.class);
        BlockEntity.registerBlockEntity(BlockEntity.SCULK_SHRIEKER, BlockEntitySculkShrieker.class);

        // Persistent container, not on vanilla
        BlockEntity.registerBlockEntity(BlockEntity.PERSISTENT_CONTAINER, PersistentDataContainerBlockEntity.class);
    }

    /**
     * Is nether enabled on this server
     *
     * @return nether enabled
     */
    public boolean isNetherAllowed() {
        return this.netherEnabled;
    }


    /**
     * Is vibrant visuals enabled on this server
     *
     * @return vibrant visuals enabled
     */
    public boolean isVibrantVisualsEnabled() {
        return this.enableVibrantVisuals;
    }

    /**
     * Is ray tracing enabled on this server
     *
     * @return ray tracing enabled
     */
    public boolean isRaytracingEnabled() {
        return this.enableRaytracing;
    }

    public boolean isWaterdogCapable() {
        return this.useWaterdog;
    }

    /**
     * Get player data serializer that is used to save player data
     *
     * @return player data serializer
     */
    public PlayerDataSerializer getPlayerDataSerializer() {
        return playerDataSerializer;
    }

    /**
     * Set player data serializer that is used to save player data
     *
     * @param playerDataSerializer player data serializer
     */
    public void setPlayerDataSerializer(PlayerDataSerializer playerDataSerializer) {
        this.playerDataSerializer = Preconditions.checkNotNull(playerDataSerializer, "playerDataSerializer");
    }

    public TickingAreaManager getTickingAreaManager() {
        return tickingAreaManager;
    }

    /**
     * Get the Server instance
     *
     * @return Server
     */
    public static Server getInstance() {
        return instance;
    }

    /**
     * Get the mob spawner task
     *
     * @return spawner task
     */
    public SpawnerTask getSpawnerTask() {
        return this.spawnerTask;
    }

    /**
     * Load settings from server.properties (standard MC) and nukkit-mot.yml (advanced MOT)
     */
    private void loadSettings() {
        // === Standard MC settings from server.properties ===
        this.forceLanguage = this.getPropertyBoolean("force-language", false);
        this.motd = this.getPropertyString("motd", "Minecraft Server");
        this.viewDistance = Math.max(1, this.getPropertyInt("view-distance", 8));
        this.port = this.getPropertyInt("server-port", 19132);
        this.ip = this.getPropertyString("server-ip", "0.0.0.0");
        this.ipv6Port = this.getPropertyInt("server-ipv6-port", -1);
        this.ipv6Address = this.getPropertyString("server-ipv6", "::");
        if (!this.ipv6Address.isBlank()) {
            try {
                InetAddress resolved = InetAddress.getByName(this.ipv6Address);
                if (!(resolved instanceof Inet6Address)) {
                    throw new IllegalArgumentException("server-ipv6 must be an IPv6 address, got: " + this.ipv6Address + " (resolved to " + resolved.getHostAddress() + ")");
                }
                this.ipv6Address = resolved.getHostAddress();
            } catch (UnknownHostException e) {
                throw new IllegalArgumentException("Unable to resolve server-ipv6 address: " + this.ipv6Address, e);
            }
        }
        // IPv6 listener: port <= 0 (default -1) disables it; a valid port binds ipv6-address:ipv6-port
        this.ipv6Enabled = this.ipv6Port > 0 && this.ipv6Port != this.port && !this.ipv6Address.isBlank();
        if (this.ipv6Port >= 0 && !this.ipv6Enabled) {
            log.warn("IPv6 listener requested on port {} but it was disabled (port must be > 0, non-blank address, and differ from server-port {})", this.ipv6Port, this.port);
        }
        try {
            this.gamemode = this.getPropertyInt("gamemode", 0) & 0b11;
        } catch (NumberFormatException exception) {
            this.gamemode = getGamemodeFromString(this.getPropertyString("gamemode")) & 0b11;
        }
        this.forceGamemode = this.getPropertyBoolean("force-gamemode", true);
        this.isHardcore = this.getPropertyBoolean("hardcore", false);
        this.pvpEnabled = this.getPropertyBoolean("pvp", true);
        this.spawnAnimals = this.getPropertyBoolean("spawn-animals", true);
        this.spawnMonsters = this.getPropertyBoolean("spawn-mobs", true);
        this.flyChecks = this.getPropertyBoolean("allow-flight", false);
        this.spawnRadius = this.getPropertyInt("spawn-protection", 10);
        this.xboxAuth = this.getPropertyBoolean("xbox-auth", true);
        this.keepExistingSessionOnDuplicateLogin = this.getPropertyBoolean("keep-existing-session-on-duplicate-login", false);
        this.encryptionEnabled = this.getPropertyBoolean("encryption", true);
        if (!this.encryptionEnabled) {
            log.warn("Encryption is not enabled. For better security, it's recommended to enable it if you don't use a proxy software.");
        }
        this.whitelistEnabled = this.getPropertyBoolean("white-list", false);
        this.whitelistReason = this.getPropertyString("whitelist-reason", "§cServer is white-listed").replace("§n", "\n");
        this.forceResources = this.getPropertyBoolean("force-resources", false);
        this.forceResourcesAllowOwnPacks = this.getPropertyBoolean("force-resources-allow-client-packs", false);
        this.autoSaveTicks = this.serverConfig.performanceSettings().ticksPerAutosave();
        switch (this.getPropertyString("server-authoritative-movement", "server-auth")) {
            case "client-auth" -> this.serverAuthoritativeMovementMode = 0;
            case "server-auth-with-rewind" -> this.serverAuthoritativeMovementMode = 2;
            default -> this.serverAuthoritativeMovementMode = 1; // server-auth
        }
        this.serverAuthoritativeBlockBreaking = this.getPropertyBoolean("server-authoritative-block-breaking", true);
        this.serverAuthoritativeInventory = this.getPropertyBoolean("server-authoritative-inventory", true);

        // === Advanced MOT settings from nukkit-mot.yml ===
        ServerConfig config = this.serverConfig;

        // Performance
        this.networkCompressionLevel = Math.max(Math.min(config.networkSettings().compressionLevel(), 9), 0);
        this.chunkCompressionLevel = Math.max(Math.min(config.networkSettings().chunkCompressionLevel(), 9), 1);
        this.autoTickRate = config.performanceSettings().autoTickRate();
        this.autoTickRateLimit = config.performanceSettings().autoTickRateLimit();
        this.alwaysTickPlayers = config.performanceSettings().alwaysTickPlayers();
        this.baseTickRate = config.performanceSettings().baseTickRate();
        this.doLevelGC = config.performanceSettings().doLevelGc();
        this.enableSpark = config.performanceSettings().enableSpark();
        this.levelDbCache = config.performanceSettings().leveldbCacheMb();
        this.useNativeLevelDB = config.performanceSettings().useNativeLeveldb();

        // Network
        this.networkCompressionThreshold = config.networkSettings().compressionThreshold();
        this.useSnappy = config.networkSettings().useSnappyCompression();
        this.rakPacketLimit = config.networkSettings().rakPacketLimit();
        this.networkLoginTimeoutMilliseconds = config.networkSettings().timeoutMilliseconds();
        this.rakCookieMode = parseRakCookieMode(config.networkSettings().rakCookieMode());
        this.queryPlugins = config.networkSettings().queryPlugins();
        this.useWaterdog = config.networkSettings().useWaterdog();
        this.viaProxyUsernamePrefix = config.networkSettings().viaProxyUsernamePrefix();
        this.enableProxyProtocol = config.networkSettings().enableProxyProtocol();
        List<String> proxyProtocolWhitelistConfig = config.networkSettings().proxyProtocolWhitelist();
        this.proxyProtocolWhitelist = proxyProtocolWhitelistConfig == null ? new ArrayList<>() : new ArrayList<>(proxyProtocolWhitelistConfig);
        this.proxyProtocolWhitelist.replaceAll(String::trim);
        this.proxyProtocolWhitelist.removeIf(String::isEmpty);
        if (this.enableProxyProtocol) {
            log.info("Proxy Protocol v2 enabled");
            if (this.proxyProtocolWhitelist.isEmpty()) {
                log.warn("Proxy Protocol whitelist is empty - all source IPs will be trusted. Consider setting 'proxy-protocol-whitelist' for security.");
            }
        }

        // Chunks
        this.chunksPerTick = config.chunkSettings().sendingPerTick();
        this.spawnThreshold = config.chunkSettings().spawnThreshold();
        this.lightUpdates = config.chunkSettings().lightUpdates();
        this.cacheChunks = config.chunkSettings().cacheChunks();
        this.asyncChunkSending = config.chunkSettings().asyncChunks();
        this.maxPendingChunkWrites = Math.max(1, config.chunkSettings().maxPendingChunkWrites());

        // Entity
        this.spawnEggsEnabled = config.entitySettings().spawnEggs();
        this.mobAiEnabled = config.entitySettings().mobAi();
        this.despawnMobs = config.entitySettings().despawnTask();
        this.mobDespawnTicks = config.entitySettings().ticksPerDespawns();
        int activationBlocks = config.entitySettings().activationBlocks();
        // Below 16 blocks a mob could sleep inside a player's own view of it; clamp instead of guessing.
        this.entityActivationRangeSquared = activationBlocks <= 0 ? 0 : (double) Math.max(16, activationBlocks) * Math.max(16, activationBlocks);
        this.entityTargetSearchInterval = Math.max(1, config.entitySettings().targetSearchInterval());

        // World
        this.netherEnabled = config.worldSettings().nether();
        this.endEnabled = config.worldSettings().end();
        this.vanillaPortals = config.worldSettings().vanillaPortals();
        this.portalTicks = config.worldSettings().portalTicks();
        multiNetherWorlds.clear();
        multiNetherWorlds.addAll(config.worldSettings().multiNetherWorlds());

        antiXrayWorlds.clear();
        antiXrayWorlds.addAll(config.worldSettings().antiXrayWorlds());

        noTickingWorlds.clear();
        noTickingWorlds.addAll(config.worldSettings().doNotTickWorlds());

        // Player
        this.shouldSavePlayerData = config.playerSettings().savePlayerData();
        this.savePlayerDataByUuid = config.playerSettings().savePlayerDataByUuid();
        this.personaSkins = config.playerSettings().personaSkins();
        this.skinChangeCooldown = config.playerSettings().skinChangeCooldown();
        this.doNotLimitSkinGeometry = config.playerSettings().doNotLimitSkinGeometry();
        this.doNotLimitInteractions = config.playerSettings().doNotLimitInteractions();
        this.xpBottlesOnCreative = config.playerSettings().xpBottlesOnCreative();
        this.stopInGame = config.playerSettings().stopInGame();
        this.opInGame = config.playerSettings().opInGame();

        String spaceNameMode = config.playerSettings().spaceNameMode();
        switch (spaceNameMode != null ? spaceNameMode.toLowerCase(Locale.ROOT) : "ignore") {
            case "deny", "disabled" -> this.spaceMode = 0;
            case "replace", "replacing" -> this.spaceMode = 2;
            default -> this.spaceMode = 1; // ignore
        }

        // Debug
        this.callDataPkSendEv = config.debugSettings().callDataPkSendEvent();
        this.callBatchPkEv = config.debugSettings().callBatchPkSendEvent();
        this.callEntityMotionEv = config.debugSettings().callEntityMotionEvent();
        this.mobsFromBlocks = config.debugSettings().blockListener();
        this.updateChecks = config.debugSettings().updateNotifications();
        this.deprecatedVerbose = config.debugSettings().deprecatedVerbose();

        // Game features
        this.achievementsEnabled = config.gameFeatureSettings().achievements();
        this.announceAchievements = config.gameFeatureSettings().announcePlayerAchievements();
        this.bedSpawnpoints = config.gameFeatureSettings().bedSpawnpoints();
        this.explosionBreakBlocks = config.gameFeatureSettings().explosionBreakBlocks();
        this.dropSpawners = config.gameFeatureSettings().dropSpawners();
        this.anvilsEnabled = config.gameFeatureSettings().anvilsEnabled();
        this.vanillaBossBar = config.gameFeatureSettings().vanillaBossbars();
        this.useClientSpectator = config.gameFeatureSettings().useClientSpectator();
        this.enableExperimentMode = config.gameFeatureSettings().enableExperimentMode();
        this.minimumProtocol = config.gameFeatureSettings().multiversionMinProtocol();
        int maxProto = config.gameFeatureSettings().multiversionMaxProtocol();
        this.maximumProtocol = maxProto == -1 ? GameVersion.getLastVersion().getProtocol() : maxProto;
        this.enableRawOres = config.gameFeatureSettings().enableRawOres();
        this.enableNewPaintings = config.gameFeatureSettings().enableNewPaintings();
        this.enableNewChickenEggsLaying = config.gameFeatureSettings().enableNewChickenEggsLaying();
        this.forcedSafetyEnchant = config.gameFeatureSettings().forcedSafetyEnchant();
        this.enableVibrantVisuals = config.gameFeatureSettings().enableVibrantVisuals();
        this.enableRaytracing = config.gameFeatureSettings().enableRaytracing();
        this.banXBAuthFailed = config.gameFeatureSettings().tempIpBanFailedXboxAuth();
        this.strongIPBans = config.gameFeatureSettings().strongIpBans();
        this.checkOpMovement = config.gameFeatureSettings().checkOpMovement();

        // 击退抗性属性随配置变化，重载后重同步在线玩家
        // Knockback resistance attribute follows the config; resync online players after reload
        for (Player player : this.getOnlinePlayers().values()) {
            player.sendKnockBackResistanceAttribute();
        }

        // NetEase
        this.netEaseMode = config.neteaseSettings().clientSupport();
        this.onlyNetEaseMode = config.neteaseSettings().onlyAllowNeteaseClient();

        this.c_s_spawnThreshold = (int) Math.ceil(Math.sqrt(this.spawnThreshold));
    }

    /**
     * Internal: Warn user about non multiversion compatible plugins.
     */
    public static void mvw(String action) {
        if (getInstance().minimumProtocol != ProtocolInfo.CURRENT_PROTOCOL) {
            if (Nukkit.DEBUG > 1) {
                getInstance().getLogger().logException(new PluginException("Default " + action + " used by a plugin. This can cause instability with the multiversion."));
            } else {
                getInstance().getLogger().warning("Default " + action + " used by a plugin. This can cause instability with the multiversion.");
            }
        }
    }

    private class ConsoleThread extends Thread implements InterruptibleThread {
        @Override
        public void run() {
            console.start();
        }
    }

    private static class ComputeThread extends ForkJoinWorkerThread {
        ComputeThread(final ForkJoinPool pool, final AtomicInteger threadCount) {
            super(pool);
            setName("ComputeThreadPool-thread-" + threadCount.getAndIncrement());
        }
    }

    private static class ComputeThreadPoolThreadFactory implements ForkJoinPool.ForkJoinWorkerThreadFactory {
        private static final AtomicInteger threadCount = new AtomicInteger(0);

        @SuppressWarnings("removal")
        private static final AccessControlContext ACC = contextWithPermissions(
            new RuntimePermission("getClassLoader"),
            new RuntimePermission("setContextClassLoader")
        );

        @SuppressWarnings("removal")
        static AccessControlContext contextWithPermissions(final Permission... perms) {
            final Permissions permissions = new Permissions();
            for (final Permission perm : perms) {
                permissions.add(perm);
            }
            return new AccessControlContext(new ProtectionDomain[]{new ProtectionDomain(null, permissions)});
        }

        @Override
        @SuppressWarnings("removal")
        public ForkJoinWorkerThread newThread(final ForkJoinPool pool) {
            return AccessController.doPrivileged((PrivilegedAction<ForkJoinWorkerThread>) () -> new ComputeThread(pool, threadCount), ACC);
        }
    }

    /**
     * Default values for server.properties (standard MC settings only)
     */
    private static class ServerProperties extends ConfigSection {
        {
            put("motd", "Minecraft Server");
            put("sub-motd", "Powered by Nukkit-MOT");
            put("server-port", 19132);
            put("server-ip", "0.0.0.0");
            // 等于 server-port（默认 19132）即与 RakNet 共用 UDP 端口；设为 19134 等则钉住独立媒体端口
            // Equal to server-port (19132 by default) it shares RakNet's UDP port; 19134 etc. pins a standalone media port
            put("server-udp-ports", 19132);
            put("server-ipv6-port", -1);
            put("server-ipv6", "::");
            put("view-distance", 8);
            put("max-players", 50);
            put("language", "eng");
            put("force-language", false);
            put("shutdown-message", "§cServer closed");

            put("gamemode", 0);
            put("force-gamemode", true);
            put("difficulty", 2);
            put("hardcore", false);
            put("pvp", true);

            put("level-name", "world");
            put("level-seed", "");
            put("level-type", "default");
            put("generator-settings", "");
            put("spawn-protection", 10);

            put("white-list", false);
            put("whitelist-reason", "§cServer is white-listed");
            put("xbox-auth", true);
            put("keep-existing-session-on-duplicate-login", false);
            put("encryption", true);

            put("force-resources", false);
            put("force-resources-allow-client-packs", false);

            put("spawn-animals", true);
            put("spawn-mobs", true);
            put("allow-flight", false);
            put("auto-save", true);

            put("enable-rcon", false);
            put("rcon.password", Base64.getEncoder().encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes()).substring(3, 13));
            put("rcon.port", 25575);
            put("enable-query", true);

            put("server-authoritative-movement", "server-auth");
            put("server-authoritative-block-breaking", true);
            put("server-authoritative-inventory", true);
        }
    }

    private static RakServerCookieMode parseRakCookieMode(String value) {
        try {
            return RakServerCookieMode.valueOf(value.toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            // Backward compatibility: treat "true" as ACTIVE and "false" as OFF
            if ("true".equalsIgnoreCase(value)) {
                return RakServerCookieMode.ACTIVE;
            } else if ("false".equalsIgnoreCase(value)) {
                return RakServerCookieMode.OFF;
            }
            log.warn("Unknown rak-cookie-mode '{}', defaulting to ACTIVE", value);
            return RakServerCookieMode.ACTIVE;
        }
    }
}
