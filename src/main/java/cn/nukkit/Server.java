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
import cn.nukkit.event.server.PlayerDataSerializeEvent;
import cn.nukkit.event.server.QueryRegenerateEvent;
import cn.nukkit.event.server.ServerStopEvent;
import cn.nukkit.inventory.CraftingManager;
import cn.nukkit.inventory.Recipe;
import cn.nukkit.item.Item;
import cn.nukkit.item.RuntimeItemMapping;
import cn.nukkit.item.RuntimeItems;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.lang.TextContainer;
import cn.nukkit.level.EnumLevel;
import cn.nukkit.level.GlobalBlockPalette;
import cn.nukkit.level.Level;
import cn.nukkit.level.Position;
import cn.nukkit.level.biome.EnumBiome;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.LevelProviderManager;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.generator.*;
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
import cn.nukkit.network.BatchingHelper;
import cn.nukkit.network.Network;
import cn.nukkit.network.RakNetInterface;
import cn.nukkit.network.SourceInterface;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.PlayerListPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
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
import cn.nukkit.resourcepacks.loader.ZippedResourcePackLoader;
import cn.nukkit.scheduler.ServerScheduler;
import cn.nukkit.scheduler.Task;
import cn.nukkit.scoreboard.manager.IScoreboardManager;
import cn.nukkit.scoreboard.manager.ScoreboardManager;
import cn.nukkit.scoreboard.storage.JSONScoreboardStorage;
import cn.nukkit.utils.*;
import cn.nukkit.utils.bugreport.ExceptionHandler;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableMap;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import io.sentry.Sentry;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import it.unimi.dsi.fastutil.objects.ObjectList;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.netty.channel.raknet.RakConstants;
import org.iq80.leveldb.CompressionType;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.Options;
import org.iq80.leveldb.impl.Iq80DBFactory;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.ForkJoinWorkerThread;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
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
    private boolean hasStopped;

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

    private String ip;
    private int port;
    private QueryHandler queryHandler;
    private QueryRegenerateEvent queryRegenerateEvent;
    private final UUID serverID;
    private final Config properties;

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
    private final Thread currentThread;
    private Watchdog watchdog;
    private final DB nameLookup;
    private PlayerDataSerializer playerDataSerializer;
    private SpawnerTask spawnerTask;
    private final BatchingHelper batchingHelper;

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
     * Action mode if there is a space in the nickname.
        0 - disabled (kick player on login)
        1 - ignore [default]
        2 - replace (replacing the space with an underscore)
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
     * Ticks before activating the portal.
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
     * Asynchronous chunk sending (Experiment)
     */
    public boolean asyncChunkSending;
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
     * Network encryption
     */
    public boolean encryptionEnabled;
    /**
     * Using WaterdogPE Proxy
     */
    public boolean useWaterdog;
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
     * Temporary disable world saving to allow safe backup of leveldb worlds.
     */
    public boolean holdWorldSave;
    /**
     * Enable RakNet cookies for additional security
     */
    public boolean enableRakSendCookie;
    /**
     * Enable forced safety enchantments (up max lvl)
     */
    public boolean forcedSafetyEnchant;

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
        this.consoleThread.start();
        this.console.setExecutingCommands(true);

        log.info("Loading server properties...");
        this.properties = new Config(this.dataPath + "server.properties", Config.PROPERTIES, new ServerProperties());

        if (!this.getPropertyBoolean("ansi-title", true)) {
            Nukkit.TITLE = false;
        }

        int debugLvl = NukkitMath.clamp(this.getPropertyInt("debug-level", 1), 1, 3);
        if (debug && debugLvl < 2) {
            debugLvl = 2;
        }
        Nukkit.DEBUG = debugLvl;

        this.loadSettings();

        this.automaticBugReport = this.getPropertyBoolean("automatic-bug-report", false);
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

        this.baseLang = new BaseLang(this.getPropertyString("language", BaseLang.FALLBACK_LANGUAGE));

        computeThreadPool = new ForkJoinPool(Math.min(0x7fff, Runtime.getRuntime().availableProcessors()), new ComputeThreadPoolThreadFactory(), null, false);

        Object poolSize = this.getProperty("async-workers", "auto");
        if (!(poolSize instanceof Integer)) {
            try {
                poolSize = Integer.valueOf((String) poolSize);
            } catch (Exception e) {
                poolSize = Math.max(Runtime.getRuntime().availableProcessors() + 1, 4);
            }
        }

        ServerScheduler.WORKERS = (int) poolSize;

        Zlib.setProvider(this.getPropertyInt("zlib-provider", 2));

        this.scheduler = new ServerScheduler();

        this.batchingHelper = new BatchingHelper();

        if (this.getPropertyBoolean("enable-rcon", false)) {
            try {
                this.rcon = new RCON(this, this.getPropertyString("rcon.password", ""), (!this.getIp().isEmpty()) ? this.getIp() : "0.0.0.0", this.getPropertyInt("rcon.port", this.getPort()));
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

        this.autoCompaction = this.getPropertyBoolean("level-auto-compaction", true);
        this.autoCompactionTicks = Math.max(60 * 20, this.getPropertyInt("level-auto-compaction-ticks", 60 * 30 * 20));

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
        EnumBiome.values();
        Effect.init();
        Potion.init();
        Attribute.init();
        DispenseBehaviorRegister.init();
        CustomBlockManager.init(this);
        GlobalBlockPalette.getOrCreateRuntimeId(ProtocolInfo.CURRENT_PROTOCOL, 0, 0);

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
        }

        this.serverID = UUID.randomUUID();

        this.craftingManager = new CraftingManager();
        this.resourcePackManager = new ResourcePackManager(
                new ZippedResourcePackLoader(new File(Nukkit.DATA_PATH, "resource_packs")),
                new JarPluginResourcePackLoader(new File(this.pluginPath))
        );

        this.pluginManager = new PluginManager(this, this.commandMap);
        this.pluginManager.subscribeToPermission(Server.BROADCAST_CHANNEL_ADMINISTRATIVE, this.consoleSender);

        this.pluginManager.registerInterface(JavaPluginLoader.class);

        this.queryRegenerateEvent = new QueryRegenerateEvent(this, 5);

        log.info(this.baseLang.translateString("nukkit.server.networkStart", new String[]{this.getIp().isEmpty() ? "*" : this.getIp(), String.valueOf(this.getPort())}));
        this.network = new Network(this);
        this.network.setName(this.getMotd());
        this.network.setSubName(this.getSubMotd());
        this.network.registerInterface(new RakNetInterface(this));

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
                for (RuntimeItemMapping runtimeItemMapping : RuntimeItems.VALUES) {
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
        Generator.addGenerator(cn.nukkit.level.generator.Void.class, "void", Generator.TYPE_VOID);

        if (this.defaultLevel == null) {
            String defaultName = this.getPropertyString("level-name", "world");
            if (defaultName == null || defaultName.trim().isEmpty()) {
                this.getLogger().warning("level-name cannot be null, using default");
                defaultName = "world";
                this.setPropertyString("level-name", defaultName);
            }

            if (!this.loadLevel(defaultName)) {
                long seed;
                String seedString = String.valueOf(this.getProperty("level-seed", System.currentTimeMillis()));
                try {
                    seed = Long.parseLong(seedString);
                } catch (NumberFormatException e) {
                    seed = seedString.hashCode();
                }
                this.generateLevel(defaultName, seed == 0 ? System.currentTimeMillis() : seed);
            }

            this.setDefaultLevel(this.getLevelByName(defaultName));
        }

        this.properties.save(true);

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
        if (this.getPropertyBoolean("load-all-worlds", true)) {
            try {
                for (File fs : new File(new File("").getCanonicalPath() + "/worlds/").listFiles()) {
                    if ((fs.isDirectory() && !this.isLevelLoaded(fs.getName()))) {
                        this.loadLevel(fs.getName());
                    }
                }
                EnumLevel.initLevels();
            } catch (Exception e) {
                this.getLogger().error("Unable to load levels", e);
            }
        }

        if (loadPlugins) {
            this.enablePlugins(PluginLoadOrder.POSTWORLD);
        }

        EntityProperty.init();
        EntityProperty.buildPacket();
        EntityProperty.buildPlayerProperty();

        if (this.getPropertyBoolean("thread-watchdog", true)) {
            this.watchdog = new Watchdog(this, this.getPropertyInt("thread-watchdog-tick", 60000));
            this.watchdog.start();
        }

        String worlds1 = Server.getInstance().getPropertyString("worlds-entity-spawning-disabled");
        if (!worlds1.trim().isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(worlds1, ", ");
            while (tokenizer.hasMoreTokens()) {
                disabledSpawnWorlds.add(tokenizer.nextToken());
            }
        }

        String worlds2 = Server.getInstance().getPropertyString("worlds-level-auto-save-disabled");
        if (!worlds2.trim().isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(worlds2, ", ");
            while (tokenizer.hasMoreTokens()) {
                nonAutoSaveWorlds.add(tokenizer.nextToken());
            }
        }

        if (this.getPropertyBoolean("entity-auto-spawn-task", true)) {
            this.spawnerTask = new SpawnerTask();
            int spawnerTicks = Math.max(this.getPropertyInt("ticks-per-entity-spawns", 200), 2) >> 1; // Run the spawner on 2x speed but spawn only either monsters or animals
            this.scheduler.scheduleDelayedRepeatingTask(InternalPlugin.INSTANCE, this.spawnerTask, spawnerTicks, spawnerTicks);
        }

        if (this.getPropertyBoolean("bstats-metrics", true)) {
            new NukkitMetrics(this);
        }

        // Check for updates
        CompletableFuture.runAsync(() -> {
            try {
                URLConnection request = new URL(Nukkit.BRANCH).openConnection();
                request.connect();
                InputStreamReader content = new InputStreamReader((InputStream) request.getContent());
                String latest = "git-" + JsonParser.parseReader(content).getAsJsonObject().get("sha").getAsString().substring(0, 7);
                content.close();

                boolean isMaster = Nukkit.getBranch().equals("master");
                if (!this.getNukkitVersion().equals(latest) && !this.getNukkitVersion().equals("git-null") && isMaster) {
                    this.getLogger().info("§c[Nukkit-MOT][Update] §eThere is a new build of §cNukkit§3-§dMOT §eavailable! Current: " + this.getNukkitVersion() + " Latest: " + latest);
                    this.getLogger().info("§c[Nukkit-MOT][Update] §eYou can download the latest build from https://github.com/MemoriesOfTime/Nukkit-MOT/");
                } else if (!isMaster) {
                    this.getLogger().warning("§c[Nukkit-MOT] §eYou are running a dev build! Do not use in production! Branch: " + Nukkit.getBranch());
                }
            } catch (Exception ignore) {
            }
        });

        Runtime.getRuntime().addShutdownHook(new Thread(this::forceShutdown));

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

    public int broadcast(String message, String permissions) {
        Set<CommandSender> recipients = new HashSet<>();

        for (String permission : permissions.split(";")) {
            for (Permissible permissible : this.pluginManager.getPermissionSubscriptions(permission)) {
                if (permissible instanceof CommandSender && permissible.hasPermission(permission)) {
                    recipients.add((CommandSender) permissible);
                }
            }
        }

        for (CommandSender recipient : recipients) {
            recipient.sendMessage(message);
        }

        return recipients.size();
    }

    public int broadcast(TextContainer message, String permissions) {
        Set<CommandSender> recipients = new HashSet<>();

        for (String permission : permissions.split(";")) {
            for (Permissible permissible : this.pluginManager.getPermissionSubscriptions(permission)) {
                if (permissible instanceof CommandSender && permissible.hasPermission(permission)) {
                    recipients.add((CommandSender) permissible);
                }
            }
        }

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

    public void batchPackets(Player[] players, DataPacket[] packets) {
        this.batchingHelper.batchPackets(players, packets);
    }

    @Deprecated
    public void batchPackets(Player[] players, DataPacket[] packets, boolean forceSync) {
        this.batchingHelper.batchPackets(players, packets);
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

        log.info("Reloading properties...");
        this.properties.reload();
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

    public void forceShutdown() {
        this.forceShutdown(this.getPropertyString("shutdown-message", "§cServer closed").replace("§n", "\n"));
    }

    public void forceShutdown(String reason) {
        if (this.hasStopped) {
            return;
        }

        try {
            isRunning.compareAndSet(true, false);

            this.hasStopped = true;

            ServerStopEvent serverStopEvent = new ServerStopEvent();
            pluginManager.callEvent(serverStopEvent);

            if (this.holdWorldSave) {
                this.getLogger().warning("World save hold was not released! Any backup currently being taken may be invalid");
            }

            if (this.rcon != null) {
                this.getLogger().debug("Closing RCON...");
                this.rcon.close();
            }

            this.getLogger().debug("Disconnecting all players...");
            for (Player player : new ArrayList<>(this.players.values())) {
                player.close(player.getLeaveMessage(), reason);
            }

            this.getLogger().debug("Disabling all plugins...");
            this.disablePlugins();

            this.getLogger().debug("Unloading all levels...");
            for (Level level : this.levelArray) {
                this.unloadLevel(level, true);
                this.nextTick = System.currentTimeMillis(); // Fix Watchdog killing the server while saving worlds
            }

            this.getLogger().debug("Removing event handlers...");
            HandlerList.unregisterAll();

            this.getLogger().debug("Stopping all tasks...");
            this.scheduler.cancelAllTasks();
            this.scheduler.mainThreadHeartbeat(Integer.MAX_VALUE);

            this.getLogger().debug("Closing console...");
            this.consoleThread.interrupt();

            this.getLogger().debug("Closing BatchingHelper...");
            this.batchingHelper.shutdown();

            this.getLogger().debug("Stopping network interfaces...");
            for (SourceInterface interfaz : this.network.getInterfaces()) {
                interfaz.shutdown();
                this.network.unregisterInterface(interfaz);
            }

            this.batchingHelper.shutdown();

            if (nameLookup != null) {
                this.getLogger().debug("Closing name lookup DB...");
                nameLookup.close();
            }

            if (this.watchdog != null) {
                this.getLogger().debug("Stopping Watchdog...");
                this.watchdog.kill();
            }
        } catch (Exception e) {
            log.fatal("Exception happened while shutting down, exiting the process", e);
            System.exit(1);
        }
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

        this.tickProcessor();
        this.forceShutdown();
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

    public void onPlayerCompleteLoginSequence(Player player) {
        this.playerList.put(player.getUniqueId(), player);
        this.updatePlayerListData(player.getUniqueId(), player.getId(), player.getDisplayName(), player.getSkin(), player.getLoginChainData().getXUID());
    }

    public void addPlayer(InetSocketAddress socketAddress, Player player) {
        this.players.put(socketAddress, player);
    }

    public void addOnlinePlayer(Player player) {
        this.playerList.put(player.getUniqueId(), player);
        this.updatePlayerListData(player.getUniqueId(), player.getId(), player.getDisplayName(), player.getSkin(), player.getLoginChainData().getXUID());
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

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, String xboxUserId, Player[] players) {
        PlayerListPacket pk = new PlayerListPacket();
        pk.type = PlayerListPacket.TYPE_ADD;
        pk.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(uuid, entityId, name, skin, xboxUserId)};
        this.batchPackets(players, new DataPacket[]{pk}); // This is sent "directly" so it always gets thru before possible TYPE_REMOVE packet for NPCs etc.
    }

    public void updatePlayerListData(UUID uuid, long entityId, String name, Skin skin, String xboxUserId, Collection<Player> players) {
        this.updatePlayerListData(uuid, entityId, name, skin, xboxUserId, players.toArray(Player.EMPTY_ARRAY));
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
        }
    }

    public void removePlayerListData(UUID uuid, Collection<Player> players) {
        this.removePlayerListData(uuid, players.toArray(Player.EMPTY_ARRAY));
    }

    public void removePlayerListData(UUID uuid, Player player) {
        PlayerListPacket pk = new PlayerListPacket();
        pk.type = PlayerListPacket.TYPE_REMOVE;
        pk.entries = new PlayerListPacket.Entry[]{new PlayerListPacket.Entry(uuid)};
        player.dataPacket(pk);
    }

    public void sendFullPlayerListData(Player player) {
        PlayerListPacket.Entry[] array = this.playerList.values().stream()
                .map(p -> new PlayerListPacket.Entry(
                        p.getUniqueId(),
                        p.getId(),
                        p.getDisplayName(),
                        p.getSkin(),
                        p.getLoginChainData().getXUID()))
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
        BatchPacket cachedPacket = this.craftingManager.getCachedPacket(player.protocol);
        if (cachedPacket != null) { // Don't send recipes if they wouldn't work anyways
            player.dataPacket(cachedPacket);
        }
    }

    private void checkTickUpdates(int currentTick) {
        if (this.alwaysTickPlayers) {
            for (Player p : new ArrayList<>(this.players.values())) {
                p.onUpdate(currentTick);
            }
        }

        for (Player p : this.getOnlinePlayers().values()) {
            p.resetPacketCounters();
        }

        // Do level ticks
        for (Level level : this.levelArray) {
            if (level.isBeingConverted || (level.getTickRate() > this.baseTickRate && --level.tickRateCounter > 0)) {
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
        long tickTime = System.currentTimeMillis();

        long time = tickTime - this.nextTick;
        if (time < -25) {
            try {
                Thread.sleep(Math.max(5, -time - 25));
            } catch (InterruptedException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }

        long tickTimeNano = System.nanoTime();
        if ((tickTime - this.nextTick) < -25) {
            return;
        }

        ++this.tickCounter;

        this.network.processInterfaces();

        if (this.rcon != null) {
            this.rcon.check();
        }

        this.scheduler.mainThreadHeartbeat(this.tickCounter);

        this.checkTickUpdates(this.tickCounter);

        for (Player player : new ArrayList<>(this.players.values())) {
            player.checkNetwork();
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

        if (this.tickCounter % 100 == 0) {
            for (Level level : this.levelArray) {
                if (!level.isBeingConverted) {
                    level.doChunkGarbageCollection();
                }
            }
        }

        long nowNano = System.nanoTime();

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
    }

    public long getNextTick() {
        return nextTick;
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

    public String getIp() {
        return ip;
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
        String sub = this.getPropertyString("sub-motd", "Powered by Nukkit");
        if (sub.isEmpty()) sub = "Powered by Nukkit";
        return sub;
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

    public Optional<UUID> lookupName(String name) {
        byte[] nameBytes = name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);
        byte[] uuidBytes = nameLookup.get(nameBytes);
        if (uuidBytes == null) {
            return Optional.empty();
        }

        if (uuidBytes.length != 16) {
            log.warn("Invalid uuid in name lookup database detected! Removing...");
            nameLookup.delete(nameBytes);
            return Optional.empty();
        }

        ByteBuffer buffer = ByteBuffer.wrap(uuidBytes);
        return Optional.of(new UUID(buffer.getLong(), buffer.getLong()));
    }

    void updateName(UUID uuid, String name) {
        byte[] nameBytes = name.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8);

        ByteBuffer buffer = ByteBuffer.allocate(16);
        buffer.putLong(uuid.getMostSignificantBits());
        buffer.putLong(uuid.getLeastSignificantBits());

        nameLookup.put(nameBytes, buffer.array());
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

    private CompoundTag getOfflinePlayerDataInternal(String name, boolean runEvent, boolean create) {
        Preconditions.checkNotNull(name, "name");

        PlayerDataSerializeEvent event = new PlayerDataSerializeEvent(name, playerDataSerializer);
        if (runEvent) {
            pluginManager.callEvent(event);
        }

        Optional<InputStream> dataStream = Optional.empty();
        try {
            dataStream = event.getSerializer().read(name, event.getUuid().orElse(null));
            if (dataStream.isPresent()) {
                return NBTIO.readCompressed(dataStream.get());
            }
        } catch (IOException e) {
            log.warn(this.getLanguage().translateString("nukkit.data.playerCorrupted", name));
            log.throwing(e);
            create = true;
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
            Position spawn = this.getDefaultLevel().getSafeSpawn();
            long time = System.currentTimeMillis();
            nbt = new CompoundTag()
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

            this.saveOfflinePlayerData(name, nbt, true, runEvent);
        }
        return nbt;
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
        String nameLower = name.toLowerCase(Locale.ROOT);
        if (this.shouldSavePlayerData()) {
            PlayerDataSerializeEvent event = new PlayerDataSerializeEvent(nameLower, playerDataSerializer);
            if (runEvent) {
                pluginManager.callEvent(event);
            }

            if (async) {
                this.getScheduler().scheduleTask(InternalPlugin.INSTANCE, new Task() {
                    boolean hasRun = false;

                    @Override
                    public void onRun(int currentTick) {
                        this.onCancel();
                    }

                    // Doing it like this ensures that the player data will be saved in a server shutdown
                    @Override
                    public void onCancel() {
                        if (!this.hasRun) {
                            this.hasRun = true;
                            saveOfflinePlayerDataInternal(event.getSerializer(), tag, nameLower, event.getUuid().orElse(null));
                        }
                    }
                }, true);
            } else {
                saveOfflinePlayerDataInternal(event.getSerializer(), tag, nameLower, event.getUuid().orElse(null));
            }
        }
    }

    /**
     * Internal: Save offline player data
     *
     * @param serializer serializer
     * @param tag compound tag
     * @param name player name
     * @param uuid player uuid
     */
    private void saveOfflinePlayerDataInternal(PlayerDataSerializer serializer, CompoundTag tag, String name, UUID uuid) {
        try (OutputStream dataStream = serializer.write(name, uuid)) {
            NBTIO.writeGZIPCompressed(tag, dataStream, ByteOrder.BIG_ENDIAN);
        } catch (Exception e) {
            log.error(this.getLanguage().translateString("nukkit.data.saveError", name, e));
        }
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
            updateName(uuid, name);

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
     * Load a level by name
     *
     * @param name level name
     * @return loaded
     */
    public boolean loadLevel(String name) {
        if (Objects.equals(name.trim(), "")) {
            throw new LevelException("Invalid empty level name");
        }

        if (this.isLevelLoaded(name)) {
            return true;
        } else if (!this.isLevelGenerated(name)) {
            log.warn(this.baseLang.translateString("nukkit.level.notFound", name));
            return false;
        }

        String path;

        if (name.contains("/") || name.contains("\\")) {
            path = name;
        } else {
            path = this.dataPath + "worlds/" + name + '/';
        }

        Class<? extends LevelProvider> provider = LevelProviderManager.getProvider(path);

        if (provider == null) {
            log.error(this.baseLang.translateString("nukkit.level.loadError", new String[]{name, "Unknown provider"}));
            return false;
        }

        Level level;
        try {
            level = new Level(this, name, path, provider);
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

        String path;

        if (name.contains("/") || name.contains("\\")) {
            path = name;
        } else {
            path = this.dataPath + "worlds/" + name + '/';
        }

        Level level;
        try {
            provider.getMethod("generate", String.class, String.class, long.class, Class.class, Map.class).invoke(null, path, name, seed, generator, options);

            level = new Level(this, name, path, provider);
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
            String path;

            if (name.contains("/") || name.contains("\\")) {
                path = name;
            } else {
                path = this.dataPath + "worlds/" + name + '/';
            }

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
     * Get server.properties
     *
     * @return server.properties as a Config
     */
    public Config getProperties() {
        return this.properties;
    }

    /**
     * Get a value from server.properties
     *
     * @param variable key
     * @return value
     */
    public Object getProperty(String variable) {
        return this.getProperty(variable, null);
    }

    /**
     * Get a value from server.properties
     *
     * @param variable key
     * @param defaultValue default value
     * @return value
     */
    public Object getProperty(String variable, Object defaultValue) {
        return this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
    }

    /**
     * Set a string value in server.properties
     *
     * @param variable key
     * @param value value
     */
    public void setPropertyString(String variable, String value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    /**
     * Get a string value from server.properties
     *
     * @param key key
     * @return value
     */
    public String getPropertyString(String key) {
        return this.getPropertyString(key, null);
    }

    /**
     * Get a string value from server.properties
     *
     * @param key key
     * @param defaultValue default value
     * @return value
     */
    public String getPropertyString(String key, String defaultValue) {
        return this.properties.exists(key) ? this.properties.get(key).toString() : defaultValue;
    }

    /**
     * Get an int value from server.properties
     *
     * @param variable key
     * @return value
     */
    public int getPropertyInt(String variable) {
        return this.getPropertyInt(variable, null);
    }

    /**
     * Get an int value from server.properties
     *
     * @param variable key
     * @param defaultValue default value
     * @return value
     */
    public int getPropertyInt(String variable, Integer defaultValue) {
        return this.properties.exists(variable) ? (!this.properties.get(variable).equals("") ? Integer.parseInt(String.valueOf(this.properties.get(variable))) : defaultValue) : defaultValue;
    }

    /**
     * Set an int value in server.properties
     *
     * @param variable key
     * @param value value
     */
    public void setPropertyInt(String variable, int value) {
        this.properties.set(variable, value);
        this.properties.save();
    }

    /**
     * Get a boolean value from server.properties
     *
     * @param variable key
     * @return value
     */
    public boolean getPropertyBoolean(String variable) {
        return this.getPropertyBoolean(variable, null);
    }

    /**
     * Get a boolean value from server.properties
     *
     * @param variable key
     * @param defaultValue default value
     * @return value
     */
    public boolean getPropertyBoolean(String variable, Object defaultValue) {
        Object value = this.properties.exists(variable) ? this.properties.get(variable) : defaultValue;
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        switch (String.valueOf(value)) {
            case "on":
            case "true":
            case "1":
            case "yes":
                return true;
        }
        return false;
    }

    /**
     * Set a boolean value in server.properties
     *
     * @param variable key
     * @param value value
     */
    public void setPropertyBoolean(String variable, boolean value) {
        this.properties.set(variable, value ? "1" : "0");
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
    public static Int2ObjectMap<ObjectList<Player>> sortPlayers(Collection<Player> players) {
        Int2ObjectMap<ObjectList<Player>> targets = new Int2ObjectOpenHashMap<>();
        for (Player player : players) {
            targets.computeIfAbsent(player.protocol, i -> new ObjectArrayList<>()).add(player);
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
        //Entity.registerEntity("Breeze", EntityBreeze.class);
        //Entity.registerEntity("Bogged", EntityBogged.class);
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
        //Vehicles
        Entity.registerEntity("MinecartRideable", EntityMinecartEmpty.class);
        Entity.registerEntity("MinecartChest", EntityMinecartChest.class);
        Entity.registerEntity("MinecartHopper", EntityMinecartHopper.class);
        Entity.registerEntity("MinecartTnt", EntityMinecartTNT.class);
        Entity.registerEntity("Boat", EntityBoat.class);
        Entity.registerEntity("ChestBoat", EntityChestBoat.class);
        //Others
        Entity.registerEntity("Human", EntityHuman.class, true);
        Entity.registerEntity("Lightning", EntityLightning.class);
        Entity.registerEntity("AreaEffectCloud", EntityAreaEffectCloud.class);
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
     * Load some settings from server.properties
     */
    private void loadSettings() {
        this.forceLanguage = this.getPropertyBoolean("force-language", false);
        this.networkCompressionLevel = Math.max(Math.min(this.getPropertyInt("compression-level", 5), 9), 0);
        this.chunkCompressionLevel = Math.max(Math.min(this.getPropertyInt("chunk-compression-level", 7), 9), 1);
        this.autoTickRate = this.getPropertyBoolean("auto-tick-rate", true);
        this.autoTickRateLimit = this.getPropertyInt("auto-tick-rate-limit", 20);
        this.alwaysTickPlayers = this.getPropertyBoolean("always-tick-players", false);
        this.baseTickRate = this.getPropertyInt("base-tick-rate", 1);
        this.callDataPkSendEv = this.getPropertyBoolean("call-data-pk-send-event", true);
        this.callBatchPkEv = this.getPropertyBoolean("call-batch-pk-send-event", true);
        this.doLevelGC = this.getPropertyBoolean("do-level-gc", true);
        this.mobAiEnabled = this.getPropertyBoolean("mob-ai", true);

        this.netherEnabled = this.getPropertyBoolean("nether", true);
        this.endEnabled = this.getPropertyBoolean("end", false);

        antiXrayWorlds.clear();
        String antiXrayWorldsString = this.getPropertyString("anti-xray-worlds");
        if (!antiXrayWorldsString.trim().isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(antiXrayWorldsString, ", ");
            while (tokenizer.hasMoreTokens()) {
                antiXrayWorlds.add(tokenizer.nextToken());
            }
        }

        this.xboxAuth = this.getPropertyBoolean("xbox-auth", true);
        this.bedSpawnpoints = this.getPropertyBoolean("bed-spawnpoints", true);
        this.achievementsEnabled = this.getPropertyBoolean("achievements", true);
        this.banXBAuthFailed = this.getPropertyBoolean("temp-ip-ban-failed-xbox-auth", false);
        this.pvpEnabled = this.getPropertyBoolean("pvp", true);
        this.announceAchievements = this.getPropertyBoolean("announce-player-achievements", false);
        this.spawnEggsEnabled = this.getPropertyBoolean("spawn-eggs", true);
        this.forcedSafetyEnchant = this.getPropertyBoolean("forced-safety-enchant", true);
        this.xpBottlesOnCreative = this.getPropertyBoolean("xp-bottles-on-creative", false);
        this.shouldSavePlayerData = this.getPropertyBoolean("save-player-data", true);
        this.mobsFromBlocks = this.getPropertyBoolean("block-listener", true);
        this.explosionBreakBlocks = this.getPropertyBoolean("explosion-break-blocks", true);
        this.vanillaBossBar = this.getPropertyBoolean("vanilla-bossbars", false);
        this.stopInGame = this.getPropertyBoolean("stop-in-game", false);
        this.opInGame = this.getPropertyBoolean("op-in-game", false);

        switch (this.getPropertyString("space-name-mode")) {
            case "disabled" -> this.spaceMode = 0;
            case "replacing" -> this.spaceMode = 2;
            default -> this.spaceMode = 1;
        }

        this.lightUpdates = this.getPropertyBoolean("light-updates", false);
        this.queryPlugins = this.getPropertyBoolean("query-plugins", false);
        this.flyChecks = this.getPropertyBoolean("allow-flight", false);
        this.isHardcore = this.getPropertyBoolean("hardcore", false);
        this.despawnMobs = this.getPropertyBoolean("entity-despawn-task", true);
        this.forceResources = this.getPropertyBoolean("force-resources", false);
        this.forceResourcesAllowOwnPacks = this.getPropertyBoolean("force-resources-allow-client-packs", false);
        this.whitelistEnabled = this.getPropertyBoolean("white-list", false);
        this.checkOpMovement = this.getPropertyBoolean("check-op-movement", false);
        this.forceGamemode = this.getPropertyBoolean("force-gamemode", true);
        this.doNotLimitInteractions = this.getPropertyBoolean("do-not-limit-interactions", false);
        this.motd = this.getPropertyString("motd", "Minecraft Server");
        this.viewDistance = Math.max(1, this.getPropertyInt("view-distance", 8));
        this.mobDespawnTicks = this.getPropertyInt("ticks-per-entity-despawns", 12000);
        this.port = this.getPropertyInt("server-port", 19132);
        this.ip = this.getPropertyString("server-ip", "0.0.0.0");
        this.skinChangeCooldown = this.getPropertyInt("skin-change-cooldown", 30);
        this.strongIPBans = this.getPropertyBoolean("strong-ip-bans", false);
        this.spawnRadius = this.getPropertyInt("spawn-protection", 10);
        this.dropSpawners = this.getPropertyBoolean("drop-spawners", true);
        this.spawnAnimals = this.getPropertyBoolean("spawn-animals", true);
        this.spawnMonsters = this.getPropertyBoolean("spawn-mobs", true);
        this.autoSaveTicks = this.getPropertyInt("ticks-per-autosave", 6000);
        this.doNotLimitSkinGeometry = this.getPropertyBoolean("do-not-limit-skin-geometry", true);
        this.anvilsEnabled = this.getPropertyBoolean("anvils-enabled", true);
        this.chunksPerTick = this.getPropertyInt("chunk-sending-per-tick", 4);
        this.spawnThreshold = this.getPropertyInt("spawn-threshold", 56);
        this.savePlayerDataByUuid = this.getPropertyBoolean("save-player-data-by-uuid", true);

        this.vanillaPortals = this.getPropertyBoolean("vanilla-portals", true);
        this.portalTicks = this.getPropertyInt("portal-ticks", 80);

        this.personaSkins = this.getPropertyBoolean("persona-skins", true);
        this.cacheChunks = this.getPropertyBoolean("cache-chunks", false);
        this.callEntityMotionEv = this.getPropertyBoolean("call-entity-motion-event", true);
        this.updateChecks = this.getPropertyBoolean("update-notifications", false);
        this.minimumProtocol = this.getPropertyInt("multiversion-min-protocol", 0);
        this.maximumProtocol = this.getPropertyInt("multiversion-max-protocol", ProtocolInfo.CURRENT_PROTOCOL);
        this.whitelistReason = this.getPropertyString("whitelist-reason", "§cServer is white-listed").replace("§n", "\n");
        this.enableExperimentMode = this.getPropertyBoolean("enable-experiment-mode", true);
        this.asyncChunkSending = this.getPropertyBoolean("async-chunks", true);
        this.deprecatedVerbose = this.getPropertyBoolean("deprecated-verbose", true);
        switch (this.getPropertyString("server-authoritative-movement")) {
            case "client-auth" -> this.serverAuthoritativeMovementMode = 0;
            case "server-auth-with-rewind" -> this.serverAuthoritativeMovementMode = 2;
            default -> this.serverAuthoritativeMovementMode = 1;
        }
        this.serverAuthoritativeBlockBreaking = this.getPropertyBoolean("server-authoritative-block-breaking", true);
        this.encryptionEnabled = this.getPropertyBoolean("encryption", true);
        if (!this.encryptionEnabled) {
            log.warn("Encryption is not enabled. For better security, it's recommended to enable it if you don't use a proxy software.");
        }
        this.useWaterdog = this.getPropertyBoolean("use-waterdog", false);
        this.useSnappy = this.getPropertyBoolean("use-snappy-compression", false);
        this.useClientSpectator = this.getPropertyBoolean("use-client-spectator", true);
        this.networkCompressionThreshold = this.getPropertyInt("compression-threshold", 256);
        this.enableSpark = this.getPropertyBoolean("enable-spark", false);
        this.c_s_spawnThreshold = (int) Math.ceil(Math.sqrt(this.spawnThreshold));
        try {
            this.gamemode = this.getPropertyInt("gamemode", 0) & 0b11;
        } catch (NumberFormatException exception) {
            this.gamemode = getGamemodeFromString(this.getPropertyString("gamemode")) & 0b11;
        }
        String list = this.getPropertyString("do-not-tick-worlds");
        if (!list.trim().isEmpty()) {
            StringTokenizer tokenizer = new StringTokenizer(list, ", ");
            while (tokenizer.hasMoreTokens()) {
                noTickingWorlds.add(tokenizer.nextToken());
            }
        }

        this.levelDbCache = this.getPropertyInt("leveldb-cache-mb", 80);
        this.useNativeLevelDB = this.getPropertyBoolean("use-native-leveldb", false);
        this.enableRawOres = this.getPropertyBoolean("enable-raw-ores", true);
        this.enableNewPaintings = this.getPropertyBoolean("enable-new-paintings", true);
        this.enableNewChickenEggsLaying = this.getPropertyBoolean("enable-new-chicken-eggs-laying", true);
        this.rakPacketLimit = this.getPropertyInt("rak-packet-limit", RakConstants.DEFAULT_PACKET_LIMIT);
        this.enableRakSendCookie = this.getPropertyBoolean("enable-rak-send-cookie", true);
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

    /**
     * This class contains all default server.properties values.
     */
    private static class ServerProperties extends ConfigSection {
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
            put("drop-spawners", true); //TODO 考虑弃用
            put("spawn-animals", true);
            put("spawn-mobs", true);
            put("gamemode", 0);
            put("force-gamemode", true);
            put("difficulty", 2);
            put("hardcore", false);
            put("pvp", true);

            put("white-list", false);
            put("whitelist-reason", "§cServer is white-listed");

            put("generator-settings", "");
            put("level-name", "world");
            put("level-seed", "");
            put("level-type", "default");

            put("enable-rcon", false);
            put("rcon.password", Base64.getEncoder().encodeToString(UUID.randomUUID().toString().replace("-", "").getBytes()).substring(3, 13));
            put("rcon.port", 25575);

            put("auto-save", true);
            put("level-auto-compaction", true);
            put("level-auto-compaction-ticks", 60 * 30 * 20);

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
            put("forced-safety-enchant", true);
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
            put("compression-threshold", "256");
            put("use-snappy-compression", false);
            put("rak-packet-limit", RakConstants.DEFAULT_PACKET_LIMIT);
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

            put("multi-version-packs", "");

            put("do-not-tick-worlds", "");
            put("worlds-entity-spawning-disabled", "");
            put("load-all-worlds", true);
            put("ansi-title", false);
            put("block-listener", true);
            put("allow-flight", false);
            put("multiversion-min-protocol", 0);
            put("multiversion-max-protocol", -1);
            put("vanilla-bossbars", false);
            put("strong-ip-bans", false);
            put("worlds-level-auto-save-disabled", "");
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
}
