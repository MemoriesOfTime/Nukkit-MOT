package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.api.NonComputationAtomic;
import cn.nukkit.block.*;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.entity.BaseEntity;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.custom.EntityDefinition;
import cn.nukkit.entity.custom.EntityManager;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.entity.item.EntityXPOrb;
import cn.nukkit.entity.mob.EntitySnowGolem;
import cn.nukkit.entity.passive.EntityIronGolem;
import cn.nukkit.entity.projectile.EntityArrow;
import cn.nukkit.entity.weather.EntityLightning;
import cn.nukkit.event.block.BlockBreakEvent;
import cn.nukkit.event.block.BlockPlaceEvent;
import cn.nukkit.event.block.BlockUpdateEvent;
import cn.nukkit.event.entity.CreatureSpawnEvent;
import cn.nukkit.event.level.*;
import cn.nukkit.event.player.PlayerInteractEvent;
import cn.nukkit.event.player.PlayerInteractEvent.Action;
import cn.nukkit.event.weather.LightningStrikeEvent;
import cn.nukkit.item.Item;
import cn.nukkit.item.ItemBlock;
import cn.nukkit.item.ItemBucket;
import cn.nukkit.item.ItemID;
import cn.nukkit.item.enchantment.Enchantment;
import cn.nukkit.level.biome.Biome;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.EmptyChunkSection;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.level.generator.PopChunkManager;
import cn.nukkit.level.generator.task.GenerationTask;
import cn.nukkit.level.generator.task.LightPopulationTask;
import cn.nukkit.level.generator.task.PopulationTask;
import cn.nukkit.level.particle.DestroyBlockParticle;
import cn.nukkit.level.particle.ItemBreakParticle;
import cn.nukkit.level.particle.Particle;
import cn.nukkit.level.persistence.PersistentDataContainer;
import cn.nukkit.level.persistence.impl.DelegatePersistentDataContainer;
import cn.nukkit.level.sound.Sound;
import cn.nukkit.math.*;
import cn.nukkit.math.BlockFace.Plane;
import cn.nukkit.metadata.BlockMetadataStore;
import cn.nukkit.metadata.MetadataValue;
import cn.nukkit.metadata.Metadatable;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.*;
import cn.nukkit.network.protocol.*;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.potion.Effect;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.*;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import cn.nukkit.utils.collection.nb.LongObjectEntry;
import com.google.common.base.Preconditions;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.*;
import it.unimi.dsi.fastutil.objects.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.SoftReference;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Predicate;


/**
 * @author MagicDroidX Nukkit Project
 */
public class Level implements ChunkManager, Metadatable {

    private static int levelIdCounter = 1;
    private static int chunkLoaderCounter = 1;

    public static final int BLOCK_UPDATE_NORMAL = 1;
    public static final int BLOCK_UPDATE_RANDOM = 2;
    public static final int BLOCK_UPDATE_SCHEDULED = 3;
    public static final int BLOCK_UPDATE_WEAK = 4;
    public static final int BLOCK_UPDATE_TOUCH = 5;
    public static final int BLOCK_UPDATE_REDSTONE = 6;
    public static final int BLOCK_UPDATE_TICK = 7;

    public static final int TIME_DAY = 1000;
    public static final int TIME_NOON = 6000;
    public static final int TIME_SUNSET = 12000;
    public static final int TIME_NIGHT = 13000;
    public static final int TIME_MIDNIGHT = 18000;
    public static final int TIME_SUNRISE = 23000;

    public static final int TIME_FULL = 24000;

    public static final int DIMENSION_OVERWORLD = 0;
    public static final int DIMENSION_NETHER = 1;
    public static final int DIMENSION_THE_END = 2;

    // Lower values use less memory
    public static final int MAX_BLOCK_CACHE = 512;

    // The blocks that can randomly tick
    private static final boolean[] randomTickBlocks = new boolean[Block.MAX_BLOCK_ID];
    public static final boolean[] xrayableBlocks = new boolean[Block.MAX_BLOCK_ID];

    static {
        randomTickBlocks[Block.GRASS] = true;
        randomTickBlocks[Block.FARMLAND] = true;
        randomTickBlocks[Block.MYCELIUM] = true;
        randomTickBlocks[Block.SAPLING] = true;
        randomTickBlocks[Block.LEAVES] = true;
        randomTickBlocks[Block.LEAVES2] = true;
        randomTickBlocks[Block.SNOW_LAYER] = true;
        randomTickBlocks[Block.ICE] = true;
        randomTickBlocks[Block.LAVA] = true;
        randomTickBlocks[Block.STILL_LAVA] = true;
        randomTickBlocks[Block.CACTUS] = true;
        randomTickBlocks[Block.BEETROOT_BLOCK] = true;
        randomTickBlocks[Block.CARROT_BLOCK] = true;
        randomTickBlocks[Block.POTATO_BLOCK] = true;
        randomTickBlocks[Block.MELON_STEM] = true;
        randomTickBlocks[Block.PUMPKIN_STEM] = true;
        randomTickBlocks[Block.WHEAT_BLOCK] = true;
        randomTickBlocks[Block.SUGARCANE_BLOCK] = true;
        randomTickBlocks[Block.NETHER_WART_BLOCK] = true;
        randomTickBlocks[Block.FIRE] = true;
        randomTickBlocks[Block.GLOWING_REDSTONE_ORE] = true;
        randomTickBlocks[Block.COCOA_BLOCK] = true;
        randomTickBlocks[Block.ICE_FROSTED] = true;
        randomTickBlocks[Block.VINE] = true;
        randomTickBlocks[Block.WATER] = true;
        randomTickBlocks[Block.CAULDRON_BLOCK] = true;

        randomTickBlocks[Block.BAMBOO] = true;
        randomTickBlocks[Block.BAMBOO_SAPLING] = true;
        randomTickBlocks[Block.CORAL_FAN] = true;
        randomTickBlocks[Block.CORAL_FAN_DEAD] = true;
        randomTickBlocks[Block.BLOCK_KELP] = true;
        randomTickBlocks[Block.SWEET_BERRY_BUSH] = true;

        randomTickBlocks[Block.CAVE_VINES] = true;
        randomTickBlocks[Block.CAVE_VINES_BODY_WITH_BERRIES] = true;
        randomTickBlocks[Block.CAVE_VINES_HEAD_WITH_BERRIES] = true;
        randomTickBlocks[Block.AZALEA_LEAVES] = true;
        randomTickBlocks[Block.AZALEA_LEAVES_FLOWERED] = true;
        randomTickBlocks[Block.AZALEA] = true;
        randomTickBlocks[Block.FLOWERING_AZALEA] = true;
        randomTickBlocks[Block.MANGROVE_PROPAGULE] = true;
        randomTickBlocks[Block.MANGROVE_LEAVES] = true;
        randomTickBlocks[Block.CHERRY_SAPLING] = true;
        randomTickBlocks[Block.CHERRY_LEAVES] = true;

        xrayableBlocks[Block.GOLD_ORE] = true;
        xrayableBlocks[Block.IRON_ORE] = true;
        xrayableBlocks[Block.COAL_ORE] = true;
        xrayableBlocks[Block.LAPIS_ORE] = true;
        xrayableBlocks[Block.DIAMOND_ORE] = true;
        xrayableBlocks[Block.REDSTONE_ORE] = true;
        xrayableBlocks[Block.GLOWING_REDSTONE_ORE] = true;
        xrayableBlocks[Block.EMERALD_ORE] = true;
        xrayableBlocks[Block.ANCIENT_DEBRIS] = true;
        xrayableBlocks[Block.COPPER_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_LAPIS_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_IRON_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_GOLD_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_REDSTONE_ORE] = true;
        xrayableBlocks[Block.LIT_DEEPSLATE_REDSTONE_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_DIAMOND_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_COAL_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_EMERALD_ORE] = true;
        xrayableBlocks[Block.DEEPSLATE_COPPER_ORE] = true;

        randomTickBlocks[Block.CAVE_VINES] = true;
        randomTickBlocks[Block.CAVE_VINES_BODY_WITH_BERRIES] = true;
        randomTickBlocks[Block.CAVE_VINES_HEAD_WITH_BERRIES] = true;
        randomTickBlocks[Block.AZALEA_LEAVES] = true;
        randomTickBlocks[Block.AZALEA_LEAVES_FLOWERED] = true;
        randomTickBlocks[Block.COPPER_BLOCK] = true;
        randomTickBlocks[Block.CUT_COPPER] = true;
        randomTickBlocks[Block.EXPOSED_COPPER] = true;
        randomTickBlocks[Block.EXPOSED_CUT_COPPER] = true;
        randomTickBlocks[Block.WEATHERED_COPPER] = true;
        randomTickBlocks[Block.WEATHERED_CUT_COPPER] = true;
        randomTickBlocks[Block.OXIDIZED_COPPER] = true;
        randomTickBlocks[Block.OXIDIZED_CUT_COPPER] = true;
        randomTickBlocks[Block.CUT_COPPER_STAIRS] = true;
        randomTickBlocks[Block.EXPOSED_CUT_COPPER_STAIRS] = true;
        randomTickBlocks[Block.WEATHERED_CUT_COPPER_STAIRS] = true;
        randomTickBlocks[Block.OXIDIZED_CUT_COPPER_STAIRS] = true;
        randomTickBlocks[Block.CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.EXPOSED_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.WEATHERED_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.OXIDIZED_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.DOUBLE_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.EXPOSED_DOUBLE_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.WEATHERED_DOUBLE_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.OXIDIZED_DOUBLE_CUT_COPPER_SLAB] = true;
        randomTickBlocks[Block.COPPER_BULB] = true;
        randomTickBlocks[Block.EXPOSED_COPPER_BULB] = true;
        randomTickBlocks[Block.WEATHERED_COPPER_BULB] = true;
        randomTickBlocks[Block.OXIDIZED_COPPER_BULB] = true;

        randomTickBlocks[BlockID.BUDDING_AMETHYST] = true;
    }

    @NonComputationAtomic
    public final Long2ObjectNonBlockingMap<Entity> updateEntities = new Long2ObjectNonBlockingMap<>();

    @NonComputationAtomic
    private final Long2ObjectNonBlockingMap<BlockEntity> blockEntities = new Long2ObjectNonBlockingMap<>();

    @NonComputationAtomic
    private final Long2ObjectNonBlockingMap<Player> players = new Long2ObjectNonBlockingMap<>();

    @NonComputationAtomic
    private final Long2ObjectNonBlockingMap<Entity> entities = new Long2ObjectNonBlockingMap<>();

    private final ConcurrentLinkedQueue<BlockEntity> updateBlockEntities = new ConcurrentLinkedQueue<>();

    private final Server server;

    private final int levelId;

    private LevelProvider provider;
    /**
     * 防止读取provider时卸载世界导致空指针
     */
    public final ReentrantReadWriteLock providerLock = new ReentrantReadWriteLock();

    private final Int2ObjectOpenHashMap<ChunkLoader> loaders = new Int2ObjectOpenHashMap<>();

    private final Int2IntMap loaderCounter = new Int2IntOpenHashMap();

    private final Map<Long, Map<Integer, ChunkLoader>> chunkLoaders = new ConcurrentHashMap<>();

    private final Map<Long, Map<Integer, Player>> playerLoaders = new ConcurrentHashMap<>();

    private final Map<Long, Deque<DataPacket>> chunkPackets = new ConcurrentHashMap<>();

    @NonComputationAtomic
    private final Long2ObjectNonBlockingMap<Long> unloadQueue = new Long2ObjectNonBlockingMap<>();

    private int time;

    public boolean stopTime;

    public float skyLightSubtracted;

    private final String folderName;

    // Avoid OOM, gc'd references result in whole chunk being sent (possibly higher cpu)
    private final Long2ObjectOpenHashMap<SoftReference<Map<Integer, Object>>> changedBlocks = new Long2ObjectOpenHashMap<>();
    // Storing the vector is redundant
    private final Object changeBlocksPresent = new Object();
    // Storing extra blocks past 512 is redundant
    private final Int2ObjectOpenHashMap<Object> changeBlocksFullMap = new Int2ObjectOpenHashMap<>();

    private final BlockUpdateScheduler updateQueue;
    private final Queue<QueuedUpdate> normalUpdateQueue = new ConcurrentLinkedDeque<>();
    private final Map<Long, Set<Integer>> lightQueue = new ConcurrentHashMap<>(8, 0.9f, 1);

    private final Object2ObjectMap<GameVersion, ConcurrentMap<Long, Int2ObjectMap<Player>>> chunkSendQueues = new Object2ObjectOpenHashMap<>();
    private final Object2ObjectMap<GameVersion, LongSet> chunkSendTasks = new Object2ObjectOpenHashMap<>();

    private final Long2ObjectOpenHashMap<Boolean> chunkPopulationQueue = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Boolean> chunkPopulationLock = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Boolean> chunkGenerationQueue = new Long2ObjectOpenHashMap<>();
    private final int chunkGenerationQueueSize;
    private final int chunkPopulationQueueSize;

    private boolean autoSave;
    private boolean autoCompaction;
    @Getter
    @Setter
    private boolean saveOnUnloadEnabled = true;
    public boolean isBeingConverted;

    private BlockMetadataStore blockMetadata;

    private final boolean useSections;

    private final Vector3 temporalVector;

    public int sleepTicks = 0;

    private final int chunkTickRadius;
    private final Long2IntMap chunkTickList = new Long2IntOpenHashMap();
    private final int chunksPerTicks;
    private final boolean clearChunksOnTick;

    private int updateLCG = ThreadLocalRandom.current().nextInt();

    private static final int LCG_CONSTANT = 1013904223;

    private int tickRate;
    public int tickRateTime = 0;
    public int tickRateCounter = 0;

    // Notice: These shouldn't be used in the internal methods
    // Check the dimension id instead
    @Deprecated
    public final boolean isNether;
    @Deprecated
    public final boolean isEnd;

    private final Class<? extends Generator> generatorClass;
    private final ThreadLocal<Generator> generators = new ThreadLocal<>() {
        @Override
        public Generator initialValue() {
            try {
                Generator generator = generatorClass.getConstructor(Map.class).newInstance(requireProvider().getGeneratorOptions());
                NukkitRandom rand = new NukkitRandom(getSeed());
                if (Server.getInstance().isPrimaryThread()) {
                    generator.init(Level.this, rand);
                }
                generator.init(new PopChunkManager(getSeed(), Level.this::getDimensionData), rand);
                return generator;
            } catch (Throwable e) {
                Server.getInstance().getLogger().logException(e);
                return null;
            }
        }
    };

    private boolean raining;
    private int rainingIntensity;
    private int rainTime;
    private boolean thundering;
    private int thunderTime;

    private long levelCurrentTick;

    private DimensionData dimensionData;

    public GameRules gameRules;

    private final boolean randomTickingEnabled;

    @Getter
    private ExecutorService asyncChuckExecutor;
    private final Queue<NetworkChunkSerializer.NetworkChunkSerializerCallbackData> asyncChunkRequestCallbackQueue = new ConcurrentLinkedQueue<>();

    private Iterator<LongObjectEntry<Long>> lastUsingUnloadingIter;

    private final boolean antiXray;

    // 用于实现世界监听的回调
    private static final AtomicInteger callbackIdCounter = new AtomicInteger();
    private final Int2ObjectMap<Consumer<Block>> callbackBlockSet = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectMap<BiConsumer<Long, DataPacket>> callbackChunkPacketSend = new Int2ObjectOpenHashMap<>();

    public Level(Server server, String name, String path, Class<? extends LevelProvider> provider) {
        this.levelId = levelIdCounter++;
        this.blockMetadata = new BlockMetadataStore(this);
        this.server = server;
        this.autoSave = server.getAutoSave();
        this.autoCompaction = server.isAutoCompactionEnabled();

        try {
            this.provider = provider.getConstructor(Level.class, String.class).newInstance(this, path);
        } catch (Exception e) {
            throw new LevelException("Caused by " + Utils.getExceptionMessage(e));
        }

        LevelProvider levelProvider = this.requireProvider();

        levelProvider.updateLevelName(name);

        this.server.getLogger().info(this.server.getLanguage().translateString("nukkit.level.preparing",
                TextFormat.GREEN + levelProvider.getName() + TextFormat.WHITE));

        this.generatorClass = Generator.getGenerator(levelProvider.getGenerator());

        try {
            this.useSections = (boolean) provider.getMethod("usesChunkSection").invoke(null);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }

        this.folderName = name;
        this.time = (int) levelProvider.getTime();

        this.raining = levelProvider.isRaining();
        this.rainTime = levelProvider.getRainTime();
        if (this.rainTime <= 0) {
            setRainTime(Utils.random.nextInt(168000) + 12000);
        }

        this.thundering = levelProvider.isThundering();
        this.thunderTime = levelProvider.getThunderTime();
        if (this.thunderTime <= 0) {
            setThunderTime(Utils.random.nextInt(168000) + 12000);
        }

        this.levelCurrentTick = levelProvider.getCurrentTick();
        this.updateQueue = new BlockUpdateScheduler(this, levelCurrentTick);

        this.chunkTickRadius = Math.min(this.server.getViewDistance(), Math.max(1, this.server.getPropertyInt("chunk-ticking-radius", 4)));
        this.chunksPerTicks = this.server.getPropertyInt("chunk-ticking-per-tick", 40);
        this.chunkGenerationQueueSize = this.server.getPropertyInt("chunk-generation-queue-size", 8);
        this.chunkPopulationQueueSize = this.server.getPropertyInt("chunk-generation-population-queue-size", 8);
        this.chunkTickList.clear();
        this.clearChunksOnTick = this.server.getPropertyBoolean("clear-chunk-tick-list", true);
        this.temporalVector = new Vector3(0, 0, 0);
        this.tickRate = 1;

        this.skyLightSubtracted = this.calculateSkylightSubtracted(1);

        this.isNether = name.equals("nether");
        this.isEnd = name.equals("the_end");

        this.randomTickingEnabled = !Server.noTickingWorlds.contains(name);

        this.antiXray = Server.antiXrayWorlds.contains(name);

        if (this.server.asyncChunkSending) {
            this.asyncChuckExecutor = Executors.newSingleThreadExecutor(new ThreadFactoryBuilder().setNameFormat("AsyncChunkThread for " + name).build());
        }
    }

    public static long chunkHash(int x, int z) {
        return (((long) x) << 32) | (z & 0xffffffffL);
    }

    @Deprecated
    public static long blockHash(int x, int y, int z) {
        if (y < -64 || y >= 384) {
            throw new IllegalArgumentException("Y coordinate " + y + " is out of range!");
        }
        return blockHash(x, y, z, DimensionEnum.OVERWORLD.getDimensionData());
    }

    public static long blockHash(int x, int y, int z, DimensionData dimensionData) {
        return (((long) x & (long) 0xFFFFFFF) << 36) | ((long) (capWorldY(y, dimensionData) - dimensionData.getMinHeight()) << 28) | ((long) z & (long) 0xFFFFFFF);
    }

    public static int localBlockHash(double x, double y, double z, DimensionData dimensionData) {
        byte hi = (byte) (((int) x & 15) + (((int) z & 15) << 4));
        short lo = (short) (capWorldY((int) y, dimensionData) - dimensionData.getMinHeight());
        return (hi & 0xFF) << 16 | lo;
    }

    public static Vector3 getBlockXYZ(long chunkHash, int blockHash, DimensionData dimensionData) {
        int hi = (byte) (blockHash >>> 16);
        int lo = (short) blockHash;
        int y = capWorldY(lo + dimensionData.getMinHeight(), dimensionData);
        int x = (hi & 0xF) + (getHashX(chunkHash) << 4);
        int z = ((hi >> 4) & 0xF) + (getHashZ(chunkHash) << 4);
        return new Vector3(x, y, z);
    }

    public static BlockVector3 blockHash(double x, double y, double z) {
        return new BlockVector3((int) x, (int) y, (int) z);
    }

    public static int chunkBlockHash(int x, int y, int z) {
        return (x << 12) | (z << 8) | y;
    }

    public static int getHashX(long hash) {
        return (int) (hash >> 32);
    }

    public static int getHashZ(long hash) {
        return (int) hash;
    }

    public static Vector3 getBlockXYZ(BlockVector3 hash) {
        return new Vector3(hash.x, hash.y, hash.z);
    }

    public static Chunk.Entry getChunkXZ(long hash) {
        return new Chunk.Entry(getHashX(hash), getHashZ(hash));
    }

    private static int capWorldY(int y, DimensionData dimensionData) {
        return Math.max(Math.min(y, dimensionData.getMaxHeight()), dimensionData.getMinHeight());
    }

    public static int generateChunkLoaderId(ChunkLoader loader) {
        if (loader.getLoaderId() == 0) {
            return chunkLoaderCounter++;
        } else {
            throw new IllegalStateException("ChunkLoader has a loader id already assigned: " + loader.getLoaderId());
        }
    }

    public int getTickRate() {
        return tickRate;
    }

    public int getTickRateTime() {
        return tickRateTime;
    }

    public void setTickRate(int tickRate) {
        this.tickRate = tickRate;
    }

    public void initLevel() {
        Generator generator = generators.get();
        this.dimensionData = generator.getDimensionData();
        //Anvil 不支持384世界高度
        if (this.dimensionData.getDimensionId() == DIMENSION_OVERWORLD && this.provider instanceof Anvil) {
            this.dimensionData = DimensionData.LEGACY_DIMENSION;
        }
        this.gameRules = this.requireProvider().getGamerules();
    }

    public Generator getGenerator() {
        return generators.get();
    }

    public BlockMetadataStore getBlockMetadata() {
        return this.blockMetadata;
    }

    public Server getServer() {
        return server;
    }

    final public LevelProvider getProvider() {
        return this.provider;
    }

    @NotNull
    public final LevelProvider requireProvider() {
        LevelProvider levelProvider = getProvider();
        if (levelProvider == null) {
            LevelException levelException = new LevelException("The level \"" + getFolderName() + "\" is already closed (have no providers)");
            try {
                this.server.getLevels().remove(this.levelId);
                Level defaultLevel = Server.getInstance().getDefaultLevel();
                for (Player player : new ArrayList<>(this.getPlayers().values())) {
                    if (this == defaultLevel || defaultLevel == null) {
                        player.close(player.getLeaveMessage(), "Default level unload");
                    } else {
                        player.teleport(this.server.getDefaultLevel().getSafeSpawn(), null);
                    }
                }
            } catch (Exception e) {
                levelException.addSuppressed(e);
            }
            throw levelException;
        }
        return levelProvider;
    }

    final public int getId() {
        return this.levelId;
    }

    public void close() {
        this.providerLock.writeLock().lock();
        try {
            if (this.asyncChuckExecutor != null) {
                this.asyncChuckExecutor.shutdownNow();
            }

            LevelProvider levelProvider = this.provider;
            if (levelProvider != null) {
                if (this.autoSave) {
                    this.save(true);
                }
                levelProvider.close();
            }

            this.provider = null;
            this.blockMetadata = null;
            this.server.getLevels().remove(this.levelId);
            this.generators.remove();
        } finally {
            this.providerLock.writeLock().unlock();
        }
    }

    public void addSound(Vector3 pos, String sound) {
        this.addSound(pos, sound, (Player[]) null);
    }

    public void addSound(Vector3 pos, String sound, Player... players) {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.name = sound;
        packet.volume = 1;
        packet.pitch = 1;
        packet.x = pos.getFloorX();
        packet.y = pos.getFloorY();
        packet.z = pos.getFloorZ();

        if (players == null || players.length == 0) {
            addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, packet);
        } else {
            Server.broadcastPacket(players, packet);
        }
    }

    public void addSoundToViewers(Vector3 pos, cn.nukkit.level.Sound sound) {
        PlaySoundPacket packet = new PlaySoundPacket();
        packet.name = sound.getSound();
        packet.volume = 1f;
        packet.pitch = 1f;
        packet.x = pos.getFloorX();
        packet.y = pos.getFloorY();
        packet.z = pos.getFloorZ();
        addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, packet);
    }

    public void addSound(Vector3 pos, cn.nukkit.level.Sound sound) {
        this.addSound(pos, sound, 1, 1, (Player[]) null);
    }

    public void addSound(Vector3 pos, cn.nukkit.level.Sound sound, float volume, float pitch) {
        this.addSound(pos, sound, volume, pitch, (Player[]) null);
    }

    public void addSound(Vector3 pos, cn.nukkit.level.Sound sound, float volume, float pitch, Collection<Player> players) {
        this.addSound(pos, sound, volume, pitch, players.toArray(Player.EMPTY_ARRAY));
    }

    public void addSound(Vector3 pos, cn.nukkit.level.Sound sound, float volume, float pitch, Player... players) {
        Preconditions.checkArgument(volume >= 0 && volume <= 1, "Sound volume must be between 0 and 1");
        Preconditions.checkArgument(pitch >= 0, "Sound pitch must be higher than 0");

        PlaySoundPacket packet = new PlaySoundPacket();
        packet.name = sound.getSound();
        packet.volume = volume;
        packet.pitch = pitch;
        packet.x = pos.getFloorX();
        packet.y = pos.getFloorY();
        packet.z = pos.getFloorZ();

        if (players == null || players.length == 0) {
            addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, packet);
        } else {
            Server.broadcastPacket(players, packet);
        }
    }

    public void addSound(Sound sound) {
        this.addSound(sound, (Player[]) null);
    }

    public void addSound(Sound sound, Player player) {
        this.addSound(sound, new Player[]{player});
    }

    public void addSound(Sound sound, Player[] players) {
        DataPacket packet = sound.encode();
        if (packet != null) {
            if (players == null) {
                this.addChunkPacket((int) sound.x >> 4, (int) sound.z >> 4, packet);
            } else {
                Server.broadcastPacket(players, packet);
            }
        }
    }

    public void addSound(Sound sound, Collection<Player> players) {
        this.addSound(sound, players.toArray(Player.EMPTY_ARRAY));
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, int entityType) {
        this.addLevelSoundEvent(pos, type, data, entityType, null);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, int entityType, Player[] players) {
        this.addLevelSoundEvent(pos, type, data, entityType, false, false, players);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, int entityType, boolean isBaby, boolean isGlobal) {
        this.addLevelSoundEvent(pos, type, data, entityType, isBaby, isGlobal, null);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, int entityType, boolean isBaby, boolean isGlobal, Player[] players) {
        String identifier = Entity.getEntityRuntimeMapping().get(entityType);
        if (identifier == null) {
            EntityDefinition entityDefinition = EntityManager.get().getDefinition(entityType);
            identifier = entityDefinition == null ? ":" : entityDefinition.getIdentifier();
        }
        this.addLevelSoundEvent(pos, type, data, identifier, isBaby, isGlobal, players);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type) {
        this.addLevelSoundEvent(pos, type, null);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, Player[] players) {
        this.addLevelSoundEvent(pos, type, -1, players);
    }

    public void addLevelSoundEvent(int type, int pitch, int data, @NotNull Vector3 pos) {
        this.addLevelSoundEvent(type, pitch, data, pos, null);
    }

    public void addLevelSoundEvent(int type, int pitch, int data, @NotNull Vector3 pos, Player[] players) {
        this.addLevelSoundEvent(pos, type, data, ":", false, false, players);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data) {
        this.addLevelSoundEvent(pos, type, data, null);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, Player[] players) {
        this.addLevelSoundEvent(pos, type, data, ":", false, false, players);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, @NotNull String identifier, boolean isBaby, boolean isGlobal) {
        this.addLevelSoundEvent(pos, type, data, identifier, isBaby, isGlobal, null);
    }

    public void addLevelSoundEvent(@NotNull Vector3 pos, int type, int data, @NotNull String identifier, boolean isBaby, boolean isGlobal, Player[] players) {
        LevelSoundEventPacket pk = new LevelSoundEventPacket();
        pk.sound = type;
        pk.extraData = data;
        pk.entityIdentifier = identifier;
        pk.x = (float) pos.x;
        pk.y = (float) pos.y;
        pk.z = (float) pos.z;
        pk.isGlobal = isGlobal;
        pk.isBabyMob = isBaby;

        if (players == null) {
            this.addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
        } else {
            Server.broadcastPacket(players, pk);
        }
    }

    public void addLevelSoundEvent(Vector3 pos, int type, int pitch, int data, boolean isGlobal) {
        LevelSoundEventPacketV1 pk = new LevelSoundEventPacketV1();
        pk.sound = type;
        pk.pitch = pitch;
        pk.extraData = data;
        pk.x = (float) pos.x;
        pk.y = (float) pos.y;
        pk.z = (float) pos.z;
        pk.isGlobal = isGlobal;

        this.addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
    }

    public void addParticle(Particle particle) {
        this.addParticle(particle, (Player[]) null);
    }

    public void addParticle(Particle particle, Player player) {
        this.addParticle(particle, new Player[]{player});
    }

    public void addParticle(Particle particle, Player[] players) {
        addParticle(particle, players, 1);
    }

    public void addParticle(Particle particle, Player[] players, int count) {
        Object2ObjectMap<GameVersion, ObjectList<Player>> targets;
        if (players == null) {
            targets = Server.groupPlayersByGameVersion(this.getChunkPlayers(particle.getChunkX(), particle.getChunkZ()).values());
        } else {
            targets = Server.groupPlayersByGameVersion(players);
        }

        for (GameVersion protocolId : targets.keySet()) {
            ObjectList<Player> protocolPlayers = targets.get(protocolId);
            DataPacket[] packets = particle.mvEncode(protocolId);
            if (packets != null) {
                if (count == 1) {
                    Server.broadcastPackets(protocolPlayers.toArray(Player.EMPTY_ARRAY), packets);
                    continue;
                }

                List<DataPacket> packetList = Arrays.asList(packets);
                List<DataPacket> sendList = new ObjectArrayList<>();
                for (int i = 0; i < count; i++) {
                    sendList.addAll(packetList);
                }
                Server.broadcastPackets(protocolPlayers.toArray(Player.EMPTY_ARRAY), sendList.toArray(new DataPacket[0]));
            }
        }
    }

    public void addParticle(Particle particle, Collection<Player> players) {
        this.addParticle(particle, players.toArray(Player.EMPTY_ARRAY));
    }

    public void addParticleEffect(Vector3 pos, ParticleEffect particleEffect) {
        this.addParticleEffect(pos, particleEffect, -1, this.getDimension(), (Player[]) null);
    }

    public void addParticleEffect(Vector3 pos, ParticleEffect particleEffect, long uniqueEntityId) {
        this.addParticleEffect(pos, particleEffect, uniqueEntityId, this.getDimension(), (Player[]) null);
    }

    public void addParticleEffect(Vector3 pos, ParticleEffect particleEffect, long uniqueEntityId, int dimensionId) {
        this.addParticleEffect(pos, particleEffect, uniqueEntityId, dimensionId, (Player[]) null);
    }

    public void addParticleEffect(Vector3 pos, ParticleEffect particleEffect, long uniqueEntityId, int dimensionId, Collection<Player> players) {
        this.addParticleEffect(pos, particleEffect, uniqueEntityId, dimensionId, players.toArray(Player.EMPTY_ARRAY));
    }

    public void addParticleEffect(Vector3 pos, ParticleEffect particleEffect, long uniqueEntityId, int dimensionId, Player... players) {
        this.addParticleEffect(pos.asVector3f(), particleEffect.getIdentifier(), uniqueEntityId, dimensionId, players);
    }

    public void addParticleEffect(Vector3f pos, String identifier, long uniqueEntityId, int dimensionId, Player... players) {
        SpawnParticleEffectPacket pk = new SpawnParticleEffectPacket();
        pk.identifier = identifier;
        pk.uniqueEntityId = uniqueEntityId;
        pk.dimensionId = dimensionId;
        pk.position = pos;

        if (players == null || players.length == 0) {
            addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
        } else {
            Server.broadcastPacket(players, pk);
        }
    }

    public boolean getAutoSave() {
        return this.autoSave;
    }

    public void setAutoSave(boolean autoSave) {
        this.autoSave = autoSave;
    }

    public boolean isAutoCompaction() {
        return this.autoCompaction && this.autoSave;
    }

    public void setAutoCompaction(boolean autoCompaction) {
        this.autoCompaction = autoCompaction;
    }

    public boolean unload() {
        return this.unload(false);
    }

    public boolean unload(boolean force) {
        LevelUnloadEvent ev = new LevelUnloadEvent(this);

        if (this == this.server.getDefaultLevel() && !force) {
            ev.setCancelled();
        }

        this.server.getPluginManager().callEvent(ev);

        if (!force && ev.isCancelled()) {
            return false;
        }

        this.server.getLogger().info(this.server.getLanguage().translateString("nukkit.level.unloading",
                TextFormat.GREEN + this.getName() + TextFormat.WHITE));
        Level defaultLevel = this.server.getDefaultLevel();

        for (Player player : new ArrayList<>(this.getPlayers().values())) {
            if (this == defaultLevel || defaultLevel == null) {
                player.close(player.getLeaveMessage(), "Forced default level unload");
            } else {
                player.teleport(this.server.getDefaultLevel().getSafeSpawn());
            }
        }

        if (this == defaultLevel) {
            this.server.setDefaultLevel(null);
        }

        this.close();

        return true;
    }

    public Map<Integer, Player> getChunkPlayers(int chunkX, int chunkZ) {
        long index = Level.chunkHash(chunkX, chunkZ);
        Map<Integer, Player> map = this.playerLoaders.get(index);
        if (map != null) {
            return new HashMap<>(map);
        } else {
            return new HashMap<>();
        }
    }

    public ChunkLoader[] getChunkLoaders(int chunkX, int chunkZ) {
        long index = Level.chunkHash(chunkX, chunkZ);
        Map<Integer, ChunkLoader> map = this.chunkLoaders.get(index);
        if (map != null) {
            return map.values().toArray(new ChunkLoader[0]);
        } else {
            return new ChunkLoader[0];
        }
    }

    public void addChunkPacket(int chunkX, int chunkZ, DataPacket packet) {
        long index = Level.chunkHash(chunkX, chunkZ);
        Deque<DataPacket> packets = chunkPackets.computeIfAbsent(index, i -> new ConcurrentLinkedDeque<>());
        packets.add(packet);
        try {
            for (BiConsumer<Long, DataPacket> consumer : this.callbackChunkPacketSend.values()) {
                consumer.accept(index, packet);
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().error("Error while calling chunk packet send callback", e);
        }
    }

    public void registerChunkLoader(ChunkLoader loader, int chunkX, int chunkZ) {
        this.registerChunkLoader(loader, chunkX, chunkZ, true);
    }

    public void registerChunkLoader(ChunkLoader loader, int chunkX, int chunkZ, boolean autoLoad) {
        int hash = loader.getLoaderId();
        long index = Level.chunkHash(chunkX, chunkZ);

        Map<Integer, ChunkLoader> map = this.chunkLoaders.get(index);
        if (map == null) {
            Map<Integer, ChunkLoader> newChunkLoader = new HashMap<>();
            newChunkLoader.put(hash, loader);
            this.chunkLoaders.put(index, newChunkLoader);
            Map<Integer, Player> newPlayerLoader = new HashMap<>();
            if (loader instanceof Player) {
                newPlayerLoader.put(hash, (Player) loader);
            }
            this.playerLoaders.put(index, newPlayerLoader);
        } else if (map.containsKey(hash)) {
            return;
        } else {
            map.put(hash, loader);
            if (loader instanceof Player) {
                this.playerLoaders.get(index).put(hash, (Player) loader);
            }
        }

        if (!this.loaders.containsKey(hash)) {
            this.loaderCounter.put(hash, 1);
            this.loaders.put(hash, loader);
        } else {
            this.loaderCounter.put(hash, this.loaderCounter.get(hash) + 1);
        }

        this.cancelUnloadChunkRequest(hash);

        if (autoLoad) {
            this.loadChunk(chunkX, chunkZ);
        }
    }

    public void unregisterChunkLoader(ChunkLoader loader, int chunkX, int chunkZ) {
        int hash = loader.getLoaderId();
        long index = Level.chunkHash(chunkX, chunkZ);
        Map<Integer, ChunkLoader> chunkLoadersIndex = this.chunkLoaders.get(index);
        if (chunkLoadersIndex != null) {
            ChunkLoader oldLoader = chunkLoadersIndex.remove(hash);
            if (oldLoader != null) {
                if (chunkLoadersIndex.isEmpty()) {
                    this.chunkLoaders.remove(index);
                    this.playerLoaders.remove(index);
                    this.unloadChunkRequest(chunkX, chunkZ, true);
                } else {
                    Map<Integer, Player> playerLoadersIndex = this.playerLoaders.get(index);
                    playerLoadersIndex.remove(hash);
                }

                int count = this.loaderCounter.get(hash);
                if (--count == 0) {
                    this.loaderCounter.remove(hash);
                    this.loaders.remove(hash);
                } else {
                    this.loaderCounter.put(hash, count);
                }
            }
        }
    }

    public void checkTime() {
        if (!this.stopTime && this.gameRules.getBoolean(GameRule.DO_DAYLIGHT_CYCLE)) {
            this.time = (this.time + tickRate) % TIME_FULL;
        }
    }

    public void sendTime(Player... players) {
        SetTimePacket pk = new SetTimePacket();
        pk.time = this.time;

        Server.broadcastPacket(players, pk);
    }

    public void sendTime() {
        sendTime(this.players.values().toArray(Player.EMPTY_ARRAY));
    }

    public GameRules getGameRules() {
        return gameRules;
    }

    @SuppressWarnings("unchecked")
    public void doTick(int currentTick) {
        updateBlockLight(lightQueue);
        this.checkTime();

        if (/*stopTime || !this.gameRules.getBoolean(GameRule.DO_DAYLIGHT_CYCLE) ||*/ currentTick % 6000 == 0) { // Keep the time in sync
            this.sendTime();
        }

        // Tick Weather
        if (this.getDimension() != DIMENSION_NETHER && this.getDimension() != DIMENSION_THE_END && this.gameRules.getBoolean(GameRule.DO_WEATHER_CYCLE) && this.randomTickingEnabled()) {
            this.rainTime--;
            if (this.rainTime <= 0) {
                if (!this.setRaining(!this.raining)) {
                    if (this.raining) {
                        setRainTime(Utils.random.nextInt(12000) + 12000);
                    } else {
                        setRainTime(Utils.random.nextInt(168000) + 12000);
                    }
                }
            }

            this.thunderTime--;
            if (this.thunderTime <= 0) {
                if (!this.setThundering(!this.thundering)) {
                    if (this.thundering) {
                        setThunderTime(Utils.random.nextInt(12000) + 3600);
                    } else {
                        setThunderTime(Utils.random.nextInt(168000) + 12000);
                    }
                }
            }

            if (this.isThundering()) {
                Map<Long, ? extends FullChunk> chunks = getChunks();
                if (chunks instanceof Long2ObjectOpenHashMap) {
                    @SuppressWarnings("rawtypes")
                    Long2ObjectOpenHashMap<? extends FullChunk> fastChunks = (Long2ObjectOpenHashMap) chunks;
                    ObjectIterator<? extends Long2ObjectMap.Entry<? extends FullChunk>> iter = fastChunks.long2ObjectEntrySet().fastIterator();
                    while (iter.hasNext()) {
                        Long2ObjectMap.Entry<? extends FullChunk> entry = iter.next();
                        performThunder(entry.getLongKey(), entry.getValue());
                    }
                } else {
                    for (Map.Entry<Long, ? extends FullChunk> entry : getChunks().entrySet()) {
                        performThunder(entry.getKey(), entry.getValue());
                    }
                }
            }
        }

        if (Server.getInstance().lightUpdates) {
            this.skyLightSubtracted = this.calculateSkylightSubtracted(1);
        }

        this.levelCurrentTick++;

        this.unloadChunks();

        this.updateQueue.tick(this.levelCurrentTick);

        QueuedUpdate queuedUpdate;
        while ((queuedUpdate = this.normalUpdateQueue.poll()) != null) {
            Block block = getBlock(queuedUpdate.block, queuedUpdate.block.layer);
            BlockUpdateEvent event = new BlockUpdateEvent(block);
            this.server.getPluginManager().callEvent(event);

            if (!event.isCancelled()) {
                block.onUpdate(BLOCK_UPDATE_NORMAL);
                if (queuedUpdate.neighbor != null) {
                    block.onNeighborChange(queuedUpdate.neighbor.getOpposite());
                }
            }
        }

        if (!this.updateEntities.isEmpty()) {
            for (long id : this.updateEntities.keySetLong()) {
                Entity entity = this.updateEntities.get(id);
                if (entity == null) {
                    this.updateEntities.remove(id);
                    continue;
                }
                if (entity.closed || !entity.onUpdate(currentTick)) {
                    this.updateEntities.remove(id);
                }
            }
        }

        this.updateBlockEntities.removeIf(blockEntity -> !blockEntity.isValid() || !blockEntity.onUpdate());

        this.tickChunks();

        synchronized (changedBlocks) {
            if (!this.changedBlocks.isEmpty()) {
                if (!this.players.isEmpty()) {
                    ObjectIterator<Long2ObjectMap.Entry<SoftReference<Map<Integer, Object>>>> iter = changedBlocks.long2ObjectEntrySet().fastIterator();
                    while (iter.hasNext()) {
                        Long2ObjectMap.Entry<SoftReference<Map<Integer, Object>>> entry = iter.next();
                        long index = entry.getLongKey();
                        Map<Integer, Object> blocks = entry.getValue().get();
                        int chunkX = Level.getHashX(index);
                        int chunkZ = Level.getHashZ(index);
                        if (blocks == null || blocks.size() > MAX_BLOCK_CACHE) {
                            FullChunk chunk = this.getChunk(chunkX, chunkZ);
                            for (Player p : this.getChunkPlayers(chunkX, chunkZ).values()) {
                                p.onChunkChanged(chunk);
                            }
                        } else {
                            Player[] playerArray = this.getChunkPlayers(chunkX, chunkZ).values().toArray(Player.EMPTY_ARRAY);
                            Vector3[] blocksArray = new Vector3[blocks.size()];
                            int i = 0;
                            for (int blockHash : blocks.keySet()) {
                                Vector3 hash = getBlockXYZ(index, blockHash, this.getDimensionData());
                                blocksArray[i++] = hash;
                            }
                            this.sendBlocks(playerArray, blocksArray, UpdateBlockPacket.FLAG_ALL);
                        }
                    }
                }
                this.changedBlocks.clear();
            }
        }

        if (this.server.asyncChunkSending) {
            NetworkChunkSerializer.NetworkChunkSerializerCallbackData data;
            int count = (this.getPlayers().size() + 1) * this.server.chunksPerTick;
            for (int i = 0; i < count && (data = this.asyncChunkRequestCallbackQueue.poll()) != null; ++i) {
                this.chunkRequestCallback(data.getGameVersion(), data.getTimestamp(), data.getX(), data.getZ(), data.getSubChunkCount(), data.getPayload());
            }
        }

        this.processChunkRequest();

        if (this.sleepTicks > 0 && --this.sleepTicks <= 0) {
            this.checkSleep();
        }

        for (Map.Entry<Long, Deque<DataPacket>> entry : this.chunkPackets.entrySet()) {
            Long index = entry.getKey();
            int chunkX = Level.getHashX(index);
            int chunkZ = Level.getHashZ(index);
            Map<Integer, Player> map = this.getChunkPlayers(chunkX, chunkZ);
            if (!map.isEmpty()) {
                Player[] chunkPlayers = map.values().toArray(Player.EMPTY_ARRAY);
                for (DataPacket pk : entry.getValue()) {
                    Server.broadcastPacket(chunkPlayers, pk);
                }
            }
        }
        this.chunkPackets.clear();

        if (gameRules.isStale()) {
            GameRulesChangedPacket packet = new GameRulesChangedPacket();
            packet.gameRulesMap = gameRules.getGameRules();
            Server.broadcastPacket(players.values().toArray(Player.EMPTY_ARRAY), packet);
            gameRules.refresh();
        }
    }

    private void performThunder(long index, FullChunk chunk) {
        if (areNeighboringChunksLoaded(index)) return;
        if (Utils.random.nextInt(100000) == 0) {
            int LCG = this.getUpdateLCG() >> 2;

            int chunkX = chunk.getX() << 4;
            int chunkZ = chunk.getZ() << 4;
            Vector3 vector = this.adjustPosToNearbyEntity(new Vector3(chunkX + (LCG & 0xf), 0, chunkZ + (LCG >> 8 & 0xf)));

            Biome biome = Biome.getBiome(this.getBiomeId(vector.getFloorX(), vector.getFloorZ()));
            if (!biome.canRain()) {
                return;
            }

            int bId = this.getBlockIdAt(vector.getFloorX(), vector.getFloorY(), vector.getFloorZ());
            if (bId != Block.TALL_GRASS && bId != Block.WATER)
                vector.y += 1;
            CompoundTag nbt = new CompoundTag()
                    .putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", vector.x))
                            .add(new DoubleTag("", vector.y)).add(new DoubleTag("", vector.z)))
                    .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", 0))
                            .add(new DoubleTag("", 0)).add(new DoubleTag("", 0)))
                    .putList(new ListTag<FloatTag>("Rotation").add(new FloatTag("", 0))
                            .add(new FloatTag("", 0)));

            EntityLightning bolt = new EntityLightning(chunk, nbt);
            LightningStrikeEvent ev = new LightningStrikeEvent(this, bolt);
            server.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) {
                bolt.spawnToAll();
                this.addLevelSoundEvent(vector, LevelSoundEventPacket.SOUND_THUNDER, -1, EntityLightning.NETWORK_ID);
                this.addLevelSoundEvent(vector, LevelSoundEventPacket.SOUND_EXPLODE, -1, EntityLightning.NETWORK_ID);
            } else {
                bolt.setEffect(false);
            }
        }
    }

    public Vector3 adjustPosToNearbyEntity(Vector3 pos) {
        pos.y = this.getHighestBlockAt(pos.getFloorX(), pos.getFloorZ());
        AxisAlignedBB axisalignedbb = new SimpleAxisAlignedBB(pos.x, pos.y, pos.z, pos.getX(), this.getMaxBlockY(), pos.getZ()).expand(3, 3, 3);
        List<Entity> list = new ArrayList<>();

        for (Entity entity : this.getCollidingEntities(axisalignedbb)) {
            if (entity.isAlive() && entity.canSeeSky()) {
                list.add(entity);
            }
        }

        if (!list.isEmpty()) {
            return list.get(Utils.random.nextInt(list.size())).getPosition();
        } else {
            if (pos.getY() == -1) {
                pos = pos.up(2);
            }

            return pos;
        }
    }

    public void checkSleep() {
        if (this.players.isEmpty()) {
            return;
        }

        int playerCount = 0;
        int sleepingPlayerCount = 0;
        for (Player p : this.getPlayers().values()) {
            playerCount++;
            if (p.isSleeping()) {
                sleepingPlayerCount++;
            }
        }

        if (playerCount > 0 && sleepingPlayerCount / playerCount * 100 >= this.gameRules.getInteger(GameRule.PLAYERS_SLEEPING_PERCENTAGE)) {
            int time = this.getTime() % Level.TIME_FULL;

            if ((time >= Level.TIME_NIGHT && time < Level.TIME_SUNRISE) || this.isThundering()) {
                this.setTime(this.getTime() + Level.TIME_FULL - time);
                this.setThundering(false);
                this.setRaining(false);

                for (Player p : this.getPlayers().values()) {
                    p.stopSleep();
                }
            }
        }
    }

    public void sendBlockExtraData(int x, int y, int z, int id, int data) {
        this.sendBlockExtraData(x, y, z, id, data, this.getChunkPlayers(x >> 4, z >> 4).values());
    }

    public void sendBlockExtraData(int x, int y, int z, int id, int data, Collection<Player> players) {
        this.sendBlockExtraData(x, y, z, id, data, players.toArray(Player.EMPTY_ARRAY));
    }

    public void sendBlockExtraData(int x, int y, int z, int id, int data, Player[] players) {
        LevelEventPacket pk = new LevelEventPacket();
        pk.evid = LevelEventPacket.EVENT_SET_DATA;
        pk.x = x + 0.5f;
        pk.y = y + 0.5f;
        pk.z = z + 0.5f;
        pk.data = (data << 8) | id;

        Server.broadcastPacket(players, pk);
    }

    public void sendBlocks(Player[] target, Vector3[] blocks) {
        this.sendBlocks(target, blocks, UpdateBlockPacket.FLAG_NONE, 0);
        this.sendBlocks(target, blocks, UpdateBlockPacket.FLAG_NONE, 1);
    }

    public void sendBlocks(Player[] target, Vector3[] blocks, int flags) {
        this.sendBlocks(target, blocks, flags, 0);
        this.sendBlocks(target, blocks, flags, 1);
    }

    public void sendBlocks(Player[] target, Vector3[] blocks, int flags, boolean optimizeRebuilds) {
        this.sendBlocks(target, blocks, flags, 0, optimizeRebuilds);
        this.sendBlocks(target, blocks, flags, 1, optimizeRebuilds);
    }

    public void sendBlocks(Player[] target, Vector3[] blocks, int flags, int dataLayer) {
        this.sendBlocks(target, blocks, flags, dataLayer, false);
    }

    public void sendBlocks(Player[] target, Vector3[] blocks, int flags, int dataLayer, boolean optimizeRebuilds) {
        LongSet chunks = null;
        if (optimizeRebuilds) {
            chunks = new LongOpenHashSet();
        }

        Object2ObjectMap<GameVersion, ObjectList<Player>> targets = Server.groupPlayersByGameVersion(target);
        for (Vector3 b : blocks) {
            if (b == null) {
                continue;
            }
            boolean first = !optimizeRebuilds;

            if (optimizeRebuilds) {
                long index = Level.chunkHash((int) b.x >> 4, (int) b.z >> 4);
                if (!chunks.contains(index)) {
                    chunks.add(index);
                    first = true;
                }
            }
            UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
            updateBlockPacket.x = (int) b.x;
            updateBlockPacket.y = (int) b.y;
            updateBlockPacket.z = (int) b.z;
            updateBlockPacket.flags = first ? flags : UpdateBlockPacket.FLAG_NONE;
            updateBlockPacket.dataLayer = dataLayer;

            for (GameVersion gameVersion : targets.keySet()) {
                if (gameVersion.getProtocol() < ProtocolInfo.v1_4_0 && dataLayer > 0) {
                    continue; //1.4以前的版本不支持dataLayer
                }
                ObjectList<Player> players = targets.get(gameVersion);
                UpdateBlockPacket packet = (UpdateBlockPacket) updateBlockPacket.clone();
                try {
                    if (gameVersion.getProtocol() > 201) {
                        if (b instanceof Block) {
                            packet.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, ((Block) b).getId(), ((Block) b).getDamage());
                        } else {
                            packet.blockRuntimeId = this.getBlockRuntimeId(gameVersion, (int) b.x, (int) b.y, (int) b.z, dataLayer);
                        }
                    } else {
                        Block bl = b instanceof Block ? (Block) b : getBlock((int) b.x, (int) b.y, (int) b.z);
                        packet.blockId = bl.getId();
                        packet.blockData = bl.getDamage();
                    }
                } catch (NoSuchElementException e) {
                    throw new IllegalStateException("Unable to create BlockUpdatePacket at (" + b.x + ", " + b.y + ", " + b.z + ") in " + getName() + " for players with protocol " + gameVersion);
                }

                for (Player player : players) {
                    player.dataPacket(packet);
                }
            }
        }
    }

    public void sendBlocks(Player target, Vector3[] blocks, int flags) {
        for (Vector3 b : blocks) {
            if (b == null) {
                continue;
            }

            UpdateBlockPacket updateBlockPacket = new UpdateBlockPacket();
            updateBlockPacket.x = (int) b.x;
            updateBlockPacket.y = (int) b.y;
            updateBlockPacket.z = (int) b.z;
            updateBlockPacket.flags = flags;

            try {
                if (target.protocol > 201) {
                    if (b instanceof Block) {
                        updateBlockPacket.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(target.getGameVersion(), ((Block) b).getId(), ((Block) b).getDamage());
                    } else {
                        updateBlockPacket.blockRuntimeId = this.getBlockRuntimeId(target.protocol, (int) b.x, (int) b.y, (int) b.z);
                    }
                } else {
                    Block bl = b instanceof Block ? (Block) b : getBlock((int) b.x, (int) b.y, (int) b.z);
                    updateBlockPacket.blockId = bl.getId();
                    updateBlockPacket.blockData = bl.getDamage();
                }
            } catch (NoSuchElementException e) {
                throw new IllegalStateException("Unable to create BlockUpdatePacket at (" + b.x + ", " + b.y + ", " + b.z + ") in " + getName() + " for player " + target.getName() + " with protocol " + target.protocol);
            }

            target.dataPacket(updateBlockPacket);
        }
    }

    private void tickChunks() {
        if (this.chunksPerTicks <= 0 || this.loaders.isEmpty()) {
            this.chunkTickList.clear();
            return;
        }

        int chunksPerLoader = Math.min(200, Math.max(1, (int) ((double) (this.chunksPerTicks - this.loaders.size()) / this.loaders.size() + 0.5)));
        int randRange = 3 + chunksPerLoader / 30;
        randRange = Math.min(randRange, this.chunkTickRadius);

        for (ChunkLoader loader : this.loaders.values()) {
            int chunkX = (int) loader.getX() >> 4;
            int chunkZ = (int) loader.getZ() >> 4;

            long index = Level.chunkHash(chunkX, chunkZ);
            int existingLoaders = Math.max(0, this.chunkTickList.getOrDefault(index, 0));
            this.chunkTickList.put(index, existingLoaders + 1);
            for (int chunk = 0; chunk < chunksPerLoader; ++chunk) {
                int dx = Utils.random.nextInt(randRange << 1) - randRange;
                int dz = Utils.random.nextInt(randRange << 1) - randRange;
                long hash = Level.chunkHash(dx + chunkX, dz + chunkZ);
                if (!this.chunkTickList.containsKey(hash) && requireProvider().isChunkLoaded(hash)) {
                    this.chunkTickList.put(hash, -1);
                }
            }
        }

        boolean blockTest = true;

        if (!chunkTickList.isEmpty()) {
            ObjectIterator<Long2IntMap.Entry> iter = chunkTickList.long2IntEntrySet().iterator();
            while (iter.hasNext()) {
                Long2IntMap.Entry entry = iter.next();
                long index = entry.getLongKey();
                if (!areNeighboringChunksLoaded(index)) {
                    iter.remove();
                    continue;
                }

                int loaders = entry.getIntValue();

                int chunkX = getHashX(index);
                int chunkZ = getHashZ(index);

                FullChunk chunk;
                if ((chunk = this.getChunk(chunkX, chunkZ, false)) == null) {
                    iter.remove();
                    continue;
                }
                if (loaders <= 0) {
                    iter.remove();
                }

                for (Entity entity : chunk.getEntities().values()) {
                    entity.scheduleUpdate();
                }

                if (this.randomTickingEnabled()) {
                    final int randomTickSpeed = gameRules.getInteger(GameRule.RANDOM_TICK_SPEED);
                    if (this.useSections) {
                        for (ChunkSection section : ((Chunk) chunk).getSections()) {
                            if (!(section instanceof EmptyChunkSection)) {
                                int Y = section.getY();
                                for (int i = 0; i < randomTickSpeed; ++i) {
                                    int n = ThreadLocalRandom.current().nextInt();
                                    int x = n & 0xF;
                                    int z = n >> 8 & 0xF;
                                    int y = n >> 16 & 0xF;

                                    int blockId = section.getBlockId(x, y, z);
                                    if (blockId >= 0 && blockId <= Block.MAX_BLOCK_ID && randomTickBlocks[blockId]) {
                                        Block block = Block.get(blockId, section.getBlockData(x, y, z), this, chunkX * 16 + x, (Y << 4) + y, chunkZ * 16 + z);
                                        block.onUpdate(BLOCK_UPDATE_RANDOM);
                                    }
                                }
                            }
                        }
                    } else {
                        for (int Y = 0; Y < 8 && (Y < 3 || blockTest); ++Y) {
                            blockTest = false;
                            for (int i = 0; i < randomTickSpeed; ++i) {
                                int n = ThreadLocalRandom.current().nextInt();
                                int x = n & 0xF;
                                int z = n >> 8 & 0xF;
                                int y = n >> 16 & 0xF;

                                int[] state = chunk.getBlockState(x, y + (Y << 4), z);
                                int blockId = state[0];
                                blockTest |= blockId != 0 && state[1] != 0;
                                if (blockId <= Block.MAX_BLOCK_ID && Level.randomTickBlocks[blockId]) {
                                    Block block = Block.get(blockId, state[1], this, x, y + (Y << 4), z);
                                    block.onUpdate(BLOCK_UPDATE_RANDOM);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (this.clearChunksOnTick) {
            this.chunkTickList.clear();
        }
    }

    public boolean save() {
        return this.save(false);
    }

    public boolean save(boolean force) {
        if ((!this.autoSave || server.holdWorldSave) && !force) {
            return false;
        }

        this.server.getPluginManager().callEvent(new LevelSaveEvent(this));

        LevelProvider levelProvider = requireProvider();
        levelProvider.setTime(this.time);
        levelProvider.setRaining(this.raining);
        levelProvider.setRainTime(this.rainTime);
        levelProvider.setThundering(this.thundering);
        levelProvider.setThunderTime(this.thunderTime);
        levelProvider.setCurrentTick(this.levelCurrentTick);
        levelProvider.setGameRules(this.gameRules);
        this.saveChunks();
        levelProvider.saveLevelData();

        return true;
    }

    public void saveChunks() {
        this.requireProvider().saveChunks();
    }

    public void updateAroundRedstone(@NotNull Vector3 pos) {
        this.updateAroundRedstone(pos, null);
    }

    public void updateAroundRedstone(@NotNull Vector3 pos, @Nullable BlockFace ignoredFace) {
        for (BlockFace side : BlockFace.values()) {
            if (ignoredFace != null && side == ignoredFace) {
                continue;
            }

            this.getBlock(pos.getSideVec(side)).onUpdate(BLOCK_UPDATE_REDSTONE);
        }
    }

    public void updateComparatorOutputLevel(Vector3 v) {
        this.updateComparatorOutputLevelSelective(v, true);
    }

    public void updateComparatorOutputLevelSelective(Vector3 v, boolean observer) {
        for (BlockFace face : Plane.HORIZONTAL) {
            Vector3 pos = v.getSideVec(face);

            if (this.isChunkLoaded((int) pos.x >> 4, (int) pos.z >> 4)) {
                Block block1 = this.getBlock(pos);

                if (block1.getId() == BlockID.OBSERVER) {
                    if (observer) {
                        block1.onNeighborChange(face.getOpposite());
                    }
                } else if (BlockRedstoneDiode.isDiode(block1)) {
                    block1.onUpdate(BLOCK_UPDATE_REDSTONE);
                } else if (block1.isNormalBlock()) {
                    pos = pos.getSideVec(face);
                    block1 = this.getBlock(pos);

                    if (BlockRedstoneDiode.isDiode(block1)) {
                        block1.onUpdate(BLOCK_UPDATE_REDSTONE);
                    }
                }
            }
        }

        if (!observer) {
            return;
        }

        for (BlockFace face : Plane.VERTICAL) {
            Block block1 = this.getBlock(v.getSideVec(face));

            if (block1.getId() == BlockID.OBSERVER) {
                block1.onNeighborChange(face.getOpposite());
            }
        }
    }

    public void updateAroundObserver(Vector3 pos) {
        for (BlockFace face : BlockFace.values()) {
            Block neighborBlock = getBlock(pos.getSide(face));
            if (neighborBlock.getId() == BlockID.OBSERVER) {
                neighborBlock.onNeighborChange(face.getOpposite());
            }
        }
    }

    public void updateAround(Vector3 pos) {
        updateAround(pos, 0);
        updateAround(pos, 1);
    }

    public void updateAround(Vector3 pos, int layer) {
        Block block = getBlock(pos);
        for (BlockFace face : BlockFace.values()) {
            normalUpdateQueue.add(new QueuedUpdate(block.getSideAtLayer(layer, face), face));
        }
    }

    @Deprecated
    public void updateAround(int x, int y, int z, int layer) {
        updateAround(new Vector3(x, y, z), layer);
        /*BlockUpdateEvent ev;
        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x, y - 1, z, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }

        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x, y + 1, z, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }

        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x - 1, y, z, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }

        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x + 1, y, z, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }

        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x, y, z - 1, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }

        this.server.getPluginManager().callEvent(
                ev = new BlockUpdateEvent(this.getBlock(x, y, z + 1, layer)));
        if (!ev.isCancelled()) {
            normalUpdateQueue.add(ev.getBlock());
        }*/
    }

    public void scheduleUpdate(Block pos, int delay) {
        this.scheduleUpdate(pos, pos, delay, 0, true);
    }

    public void scheduleUpdate(Block block, Vector3 pos, int delay) {
        this.scheduleUpdate(block, pos, delay, 0, true);
    }

    public void scheduleUpdate(Block block, Vector3 pos, int delay, int priority) {
        this.scheduleUpdate(block, pos, delay, priority, true);
    }

    public void scheduleUpdate(Block block, Vector3 pos, int delay, int priority, boolean checkArea) {
        if (block.getId() == 0 || (checkArea && !this.isChunkLoaded(block.getFloorX() >> 4, block.getFloorZ() >> 4))) {
            return;
        }

        BlockUpdateEntry entry = new BlockUpdateEntry(pos.floor(), block, ((long) delay) + levelCurrentTick, priority);

        if (!this.updateQueue.contains(entry)) {
            this.updateQueue.add(entry);
        }
    }

    public boolean cancelSheduledUpdate(Vector3 pos, Block block) {
        return this.updateQueue.remove(new BlockUpdateEntry(pos, block));
    }

    public boolean isUpdateScheduled(Vector3 pos, Block block) {
        return this.updateQueue.contains(new BlockUpdateEntry(pos, block));
    }

    public boolean isBlockTickPending(Vector3 pos, Block block) {
        return this.updateQueue.isBlockTickPending(pos, block);
    }

    public Set<BlockUpdateEntry> getPendingBlockUpdates(FullChunk chunk) {
        int minX = (chunk.getX() << 4) - 2;
        int maxX = minX + 18;
        int minZ = (chunk.getZ() << 4) - 2;
        int maxZ = minZ + 18;

        return this.getPendingBlockUpdates(new SimpleAxisAlignedBB(minX, 0, minZ, maxX, this.getMaxBlockY(), maxZ));
    }

    public Set<BlockUpdateEntry> getPendingBlockUpdates(AxisAlignedBB boundingBox) {
        return updateQueue.getPendingBlockUpdates(boundingBox);
    }

    public @NotNull Block[] getCollisionBlocks(AxisAlignedBB bb) {
        return this.getCollisionBlocks(bb, false);
    }

    public @NotNull Block[] getCollisionBlocks(AxisAlignedBB bb, boolean targetFirst) {
        return getCollisionBlocks(bb, targetFirst, false);
    }

    public @NotNull Block[] getCollisionBlocks(AxisAlignedBB bb, boolean targetFirst, boolean ignoreCollidesCheck) {
        return getCollisionBlocks(bb, targetFirst, ignoreCollidesCheck, block -> block.getId() != 0);
    }

    public @NotNull Block[] getCollisionBlocks(AxisAlignedBB bb, boolean targetFirst, boolean ignoreCollidesCheck, Predicate<Block> condition) {
        int minX = NukkitMath.floorDouble(bb.getMinX());
        int minY = NukkitMath.floorDouble(bb.getMinY());
        int minZ = NukkitMath.floorDouble(bb.getMinZ());
        int maxX = NukkitMath.ceilDouble(bb.getMaxX());
        int maxY = NukkitMath.ceilDouble(bb.getMaxY());
        int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

        if (targetFirst) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.getBlock(x, y, z, false);
                        if (block != null && condition.test(block) && (ignoreCollidesCheck || block.collidesWithBB(bb))) {
                            return new Block[]{block};
                        }
                    }
                }
            }
        } else {
            int capacity = Math.max(0, maxX - minX + 1) * Math.max(0, maxY - minY + 1) * Math.max(0, maxZ - minZ + 1);
            if (capacity == 0) {
                return Block.EMPTY_ARRAY;
            }
            Block[] collides = new Block[capacity];
            int count = 0;
            for (int z = minZ; z <= maxZ; ++z) {
                for (int x = minX; x <= maxX; ++x) {
                    for (int y = minY; y <= maxY; ++y) {
                        Block block = this.getBlock(x, y, z, false);
                        if (block != null && condition.test(block) && (ignoreCollidesCheck || block.collidesWithBB(bb))) {
                            collides[count++] = block;
                        }
                    }
                }
            }
            return count == capacity ? collides : Arrays.copyOf(collides, count);
        }

        return Block.EMPTY_ARRAY;
    }

    public boolean hasCollisionBlocks(AxisAlignedBB bb) {
        return this.hasCollisionBlocks(null, bb);
    }

    public boolean hasCollisionBlocks(Entity entity, AxisAlignedBB bb) {
        return hasCollisionBlocks(entity, bb, false);
    }

    public boolean hasCollisionBlocks(Entity entity, AxisAlignedBB bb, boolean checkCanPassThrough) {
        int minX = NukkitMath.floorDouble(bb.getMinX());
        int minY = NukkitMath.floorDouble(bb.getMinY());
        int minZ = NukkitMath.floorDouble(bb.getMinZ());
        int maxX = NukkitMath.ceilDouble(bb.getMaxX());
        int maxY = NukkitMath.ceilDouble(bb.getMaxY());
        int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    Block block = this.getBlock(entity != null ? entity.chunk : null, x, y, z, 0, false);
                    if ((!checkCanPassThrough || !block.canPassThrough()) && block.collidesWithBB(bb)) {
                        return true;
                    }
                }
            }
        }

        return false;
    }

    public boolean isFullBlock(Vector3 pos) {
        AxisAlignedBB bb;
        if (pos instanceof Block) {
            if (((Block) pos).isSolid()) {
                return true;
            }
            bb = ((Block) pos).getBoundingBox();
        } else {
            bb = this.getBlock(pos).getBoundingBox();
        }

        return bb != null && bb.getAverageEdgeLength() >= 1;
    }

    public AxisAlignedBB[] getCollisionCubes(Entity entity, AxisAlignedBB bb) {
        return this.getCollisionCubes(entity, bb, true);
    }

    public AxisAlignedBB[] getCollisionCubes(Entity entity, AxisAlignedBB bb, boolean entities) {
        return getCollisionCubes(entity, bb, entities, false);
    }

    public AxisAlignedBB[] getCollisionCubes(Entity entity, AxisAlignedBB bb, boolean entities, boolean solidEntities) {
        int minX = NukkitMath.floorDouble(bb.getMinX());
        int minY = NukkitMath.floorDouble(bb.getMinY());
        int minZ = NukkitMath.floorDouble(bb.getMinZ());
        int maxX = NukkitMath.ceilDouble(bb.getMaxX());
        int maxY = NukkitMath.ceilDouble(bb.getMaxY());
        int maxZ = NukkitMath.ceilDouble(bb.getMaxZ());

        List<AxisAlignedBB> collides = new ArrayList<>();

        for (int z = minZ; z <= maxZ; ++z) {
            for (int x = minX; x <= maxX; ++x) {
                for (int y = minY; y <= maxY; ++y) {
                    Block block = this.getBlock(x, y, z, false);
                    if (block.getId() == BlockID.BARRIER && entity.canPassThroughBarrier()) {
                        continue;
                    }
                    if (!block.canPassThrough() && block.collidesWithBB(bb)) {
                        collides.add(block.getBoundingBox());
                    }
                }
            }
        }

        if (entities || solidEntities) {
            for (Entity ent : this.getCollidingEntities(bb.grow(0.25f, 0.25f, 0.25f), entity)) {
                if (solidEntities || !ent.canPassThrough()) {
                    collides.add(ent.boundingBox.clone());
                }
            }
        }

        return collides.toArray(AxisAlignedBB.EMPTY_ARRAY);
    }

    public boolean hasCollision(Entity entity, AxisAlignedBB bb, boolean entities) {
        if (this.hasCollisionBlocks(entity, bb, true)) {
            return true;
        }

        if (entities) {
            return this.getCollidingEntities(bb.grow(0.25f, 0.25f, 0.25f), entity).length > 0;
        }
        return false;
    }

    public int getFullLight(Vector3 pos) {
        FullChunk chunk = this.getChunk((int) pos.x >> 4, (int) pos.z >> 4, false);
        int level = 0;
        if (chunk != null) {
            level = chunk.getBlockSkyLight((int) pos.x & 0x0f, (int) pos.y & 0xff, (int) pos.z & 0x0f);
            level -= this.skyLightSubtracted;

            if (level < 15) {
                level = Math.max(chunk.getBlockLight((int) pos.x & 0x0f, (int) pos.y & 0xff, (int) pos.z & 0x0f), level);
            }
        }

        return level;
    }

    public int calculateSkylightSubtracted(float tickDiff) {
        float light = 1 - (MathHelper.cos(this.calculateCelestialAngle(getTime(), tickDiff) * (6.2831855f)) * 2 + 0.5f);
        light = light < 0 ? 0 : light > 1 ? 1 : light;
        light = 1 - light;
        light = (float) ((double) light * ((raining ? 1 : 0) - 0.3125));
        light = (float) ((double) light * ((isThundering() ? 1 : 0) - 0.3125));
        light = 1 - light;
        return (int) (light * 11f);
    }

    public float calculateCelestialAngle(int time, float tickDiff) {
        float angle = ((float) time + tickDiff) / 24000f - 0.25f;

        if (angle < 0) {
            ++angle;
        }

        if (angle > 1) {
            --angle;
        }

        float i = 1 - (float) ((Math.cos((double) angle * Math.PI) + 1) / 2d);
        angle = angle + (i - angle) / 3;
        return angle;
    }

    public int getMoonPhase(long worldTime) {
        return (int) (worldTime / 24000 % 8 + 8) % 8;
    }

    public int getFullBlock(int x, int y, int z) {
        return this.getFullBlock(x, y, z, 0);
    }

    public int getFullBlock(int x, int y, int z, int layer) {
        return this.getFullBlock(null, x, y, z, layer);
    }

    public int getFullBlock(FullChunk fullChunk, int x, int y, int z, int layer) {
        if (!isYInRange(y)) {
            return 0;
        }
        FullChunk chunk = fullChunk;
        int cx = x >> 4;
        int cz = z >> 4;
        if (chunk == null || chunk.getX() != cx || chunk.getZ() != cz) {
            chunk = getChunk(cx, cz, false);
        }
        return chunk.getFullBlock(x & 0x0f, y, z & 0x0f, layer);
    }

    public int getBlockRuntimeId(int x, int y, int z, int layer) {
        return this.getBlockRuntimeId(GameVersion.getLastVersion(), x, y, z, layer);
    }

    @Deprecated
    public int getBlockRuntimeId(int protocolId, int x, int y, int z, int layer) {
        return this.getBlockRuntimeId(GameVersion.byProtocol(protocolId, Server.getInstance().onlyNetEaseMode), x, y, z, layer);
    }

    public int getBlockRuntimeId(GameVersion protocolId, int x, int y, int z, int layer) {
        return this.getChunk(x >> 4, z >> 4, false).getBlockRuntimeId(protocolId, x & 0x0f, y, z & 0x0f, layer);
    }

    public Set<Block> getBlockAround(@NotNull Vector3 pos) {
        return this.getBlockAround(pos, 0);
    }

    public Set<Block> getBlockAround(@NotNull Vector3 pos, int layer) {
        Set<Block> around = new HashSet<>();
        Block block = getBlock(pos);
        for (BlockFace face : BlockFace.values()) {
            Block side = block.getSideAtLayer(layer, face);
            around.add(side);
        }
        return around;
    }

    public Block getBlock(Vector3 pos) {
        return getBlock(pos, 0);
    }

    public Block getBlock(Vector3 pos, int layer) {
        return this.getBlock(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer);
    }

    public Block getBlock(Vector3 pos, boolean load) {
        return getBlock(pos, 0, load);
    }

    public Block getBlock(Vector3 pos, int layer, boolean load) {
        return this.getBlock(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer, load);
    }

    public Block getBlock(Vector3 pos, BlockLayer layer) {
        return this.getBlock(pos, layer.ordinal());
    }

    public Block getBlock(Vector3 pos, BlockLayer layer, boolean load) {
        return this.getBlock(pos, layer.ordinal(), load);
    }

    public Block getBlock(int x, int y, int z) {
        return getBlock(x, y, z, 0);
    }

    public Block getBlock(int x, int y, int z, int layer) {
        return getBlock(x, y, z, layer, true);
    }

    public Block getBlock(int x, int y, int z, boolean load) {
        return getBlock(x, y, z, 0, load);
    }

    public Block getBlock(int x, int y, int z, int layer, boolean load) {
        return this.getBlock(null, x, y, z, layer, load);
    }

    public Block getBlock(FullChunk chunk, int x, int y, int z, boolean load) {
        return this.getBlock(chunk, x, y, z, BlockLayer.NORMAL.ordinal(), load);
    }

    public Block getBlock(FullChunk chunk, int x, int y, int z, int layer, boolean load) {
        int[] fullState;
        if (isYInRange(y)) {
            int cx = x >> 4;
            int cz = z >> 4;
            if (chunk == null || chunk.getX() != cx || chunk.getZ() != cz) {
                if (load) {
                    chunk = getChunk(cx, cz);
                } else {
                    chunk = getChunkIfLoaded(cx, cz);
                }
            }
            if (chunk != null) {
                fullState = chunk.getBlockState(x & 0xF, y, z & 0xF, layer);
            } else {
                fullState = new int[]{0, 0};
            }
        } else {
            fullState = new int[]{0, 0};
        }

        return Block.get(fullState[0], fullState[1], this, x, y, z, layer);
    }

    public synchronized void updateAllLight(Vector3 pos) {
        this.updateBlockSkyLight((int) pos.x, (int) pos.y, (int) pos.z);
        this.addLightUpdate((int) pos.x, (int) pos.y, (int) pos.z);
    }

    public void updateBlockSkyLight(int x, int y, int z) {
    }

    public void updateBlockLight(Map<Long, Set<Integer>> map) {
        int size = map.size();
        if (size == 0) {
            return;
        }
        Queue<Long> lightPropagationQueue = new ConcurrentLinkedQueue<>();
        Queue<Object[]> lightRemovalQueue = new ConcurrentLinkedQueue<>();
        LongOpenHashSet visited = new LongOpenHashSet();
        LongOpenHashSet removalVisited = new LongOpenHashSet();

        Iterator<Map.Entry<Long, Set<Integer>>> iter = map.entrySet().iterator();
        while (iter.hasNext() && size-- > 0) {
            Map.Entry<Long, Set<Integer>> entry = iter.next();
            iter.remove();
            long index = entry.getKey();
            BaseFullChunk chunk = getChunk(getHashX(index), getHashZ(index), false);
            Set<Integer> blocks = entry.getValue();

            for (int blockHash : blocks) {
                Vector3 pos = getBlockXYZ(index, blockHash, this.getDimensionData());
                if (chunk != null) {
                    int lcx = pos.getFloorX() & 0xF;
                    int lcz = pos.getFloorZ() & 0xF;
                    int oldLevel = chunk.getBlockLight(lcx, pos.getFloorY(), lcz);
                    int newLevel = Block.getBlockLight(chunk.getBlockId(lcx, pos.getFloorY(), lcz));
                    if (oldLevel != newLevel) {
                        chunk.setBlockLight(((int) pos.x) & 0x0f, (int) pos.y, ((int) pos.z) & 0x0f, newLevel & 0x0f);

                        long hash = Hash.hashBlock(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                        if (newLevel < oldLevel) {
                            removalVisited.add(hash);
                            lightRemovalQueue.add(new Object[]{hash, oldLevel});
                        } else {
                            visited.add(hash);
                            lightPropagationQueue.add(hash);
                        }
                    }
                }
            }
        }

        while (!lightRemovalQueue.isEmpty()) {
            Object[] val = lightRemovalQueue.poll();
            long node = (long) val[0];
            int x = Hash.hashBlockX(node);
            int y = Hash.hashBlockY(node);
            int z = Hash.hashBlockZ(node);

            int lightLevel = (int) val[1];

            this.computeRemoveBlockLight(x - 1, y, z, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
            this.computeRemoveBlockLight(x + 1, y, z, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
            this.computeRemoveBlockLight(x, y - 1, z, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
            this.computeRemoveBlockLight(x, y + 1, z, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
            this.computeRemoveBlockLight(x, y, z - 1, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
            this.computeRemoveBlockLight(x, y, z + 1, lightLevel, lightRemovalQueue, lightPropagationQueue, removalVisited, visited);
        }

        while (!lightPropagationQueue.isEmpty()) {
            long node = lightPropagationQueue.poll();

            int x = Hash.hashBlockX(node);
            int y = Hash.hashBlockY(node);
            int z = Hash.hashBlockZ(node);

            int id = this.getBlockIdAt(x, y, z);
            int lightFilter = id >= Block.MAX_BLOCK_ID ? 15 : Block.lightFilter[id];
            int lightLevel = this.getBlockLightAt(x, y, z) - lightFilter;

            if (lightLevel >= 1) {
                this.computeSpreadBlockLight(x - 1, y, z, lightLevel, lightPropagationQueue, visited);
                this.computeSpreadBlockLight(x + 1, y, z, lightLevel, lightPropagationQueue, visited);
                this.computeSpreadBlockLight(x, y - 1, z, lightLevel, lightPropagationQueue, visited);
                this.computeSpreadBlockLight(x, y + 1, z, lightLevel, lightPropagationQueue, visited);
                this.computeSpreadBlockLight(x, y, z - 1, lightLevel, lightPropagationQueue, visited);
                this.computeSpreadBlockLight(x, y, z + 1, lightLevel, lightPropagationQueue, visited);
            }
        }
    }

    private void computeRemoveBlockLight(int x, int y, int z, int currentLight, Queue<Object[]> queue,
                                         Queue<Long> spreadQueue, Set<Long> visited, Set<Long> spreadVisited) {
        int current = this.getBlockLightAt(x, y, z);
        if (current != 0 && current < currentLight) {
            this.setBlockLightAt(x, y, z, 0);
            if (current > 1) {
                long index = Hash.hashBlock(x, y, z);
                if (!visited.contains(index)) {
                    visited.add(index);
                    queue.add(new Object[]{index, current});
                }
            }
        } else if (current >= currentLight) {
            long index = Hash.hashBlock(x, y, z);
            if (!spreadVisited.contains(index)) {
                spreadVisited.add(index);
                spreadQueue.add(index);
            }
        }
    }

    private void computeSpreadBlockLight(int x, int y, int z, int currentLight, Queue<Long> queue, Set<Long> visited) {
        int current = this.getBlockLightAt(x, y, z);
        if (current < currentLight - 1) {
            this.setBlockLightAt(x, y, z, currentLight);

            long index = Hash.hashBlock(x, y, z);
            if (!visited.contains(index)) {
                visited.add(index);
                if (currentLight > 1) {
                    queue.add(index);
                }
            }
        }
    }

    public void addLightUpdate(int x, int y, int z) {
        long index = chunkHash(x >> 4, z >> 4);
        Set<Integer> currentMap = this.lightQueue.computeIfAbsent(index, k -> ConcurrentHashMap.newKeySet(8));
        currentMap.add(Level.localBlockHash(x, y, z, this.getDimensionData()));
    }

    @Override
    public void setBlockFullIdAt(int x, int y, int z, int fullId) {
        this.setBlockFullIdAt(x, y, z, 0, fullId);
    }

    @Override
    public void setBlockFullIdAt(int x, int y, int z, int layer, int fullId) {
        Block block = Block.fullList[fullId];
        if (block == null) {
            block = new BlockUnknown(fullId >> Block.DATA_BITS, fullId & Block.DATA_MASK);
        }
        this.setBlock(x, y, z, layer, block, false, false);
    }

    public boolean setBlock(Vector3 pos, Block block) {
        return this.setBlock(pos, 0, block);
    }

    public boolean setBlock(Vector3 pos, BlockLayer layer, Block block) {
        return this.setBlock(pos, layer.ordinal(), block);
    }

    public boolean setBlock(Vector3 pos, int layer, Block block) {
        return this.setBlock(pos, layer, block, false);
    }

    public boolean setBlock(Vector3 pos, Block block, boolean direct) {
        return this.setBlock(pos, 0, block, direct);
    }

    public boolean setBlock(Vector3 pos, int layer, Block block, boolean direct) {
        return this.setBlock(pos, layer, block, direct, true);
    }

    public boolean setBlock(Vector3 pos, BlockLayer layer, Block block, boolean direct) {
        return this.setBlock(pos, layer.ordinal(), block, direct);
    }

    public boolean setBlock(Vector3 pos, Block block, boolean direct, boolean update) {
        return this.setBlock(pos, 0, block, direct, update);
    }

    public boolean setBlock(Vector3 pos, BlockLayer layer, Block block, boolean direct, boolean update) {
        return this.setBlock(pos, layer.ordinal(), block, direct, update);
    }

    public boolean setBlock(Vector3 pos, int layer, Block block, boolean direct, boolean update) {
        return this.setBlock(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer, block, direct, update);
    }

    public boolean setBlock(int x, int y, int z, Block block, boolean direct, boolean update) {
        return this.setBlock(x, y, z, 0, block, direct, update);
    }

    public boolean setBlock(int x, int y, int z, BlockLayer layer, Block block, boolean direct, boolean update) {
        return this.setBlock(x, y, z, layer.ordinal(), block, direct, update);
    }

    public boolean setBlock(int x, int y, int z, int layer, Block block, boolean direct, boolean update) {
        if (!isYInRange(y) || layer < 0 || layer > this.requireProvider().getMaximumLayer()) {
            return false;
        }
        BaseFullChunk chunk = this.getChunk(x >> 4, z >> 4, true);
        Block blockPrevious;
        blockPrevious = chunk.getAndSetBlock(x & 0xF, y, z & 0xF, layer, block);
        if (blockPrevious.getFullId() == block.getFullId()) {
            return false;
        }
        block.x = x;
        block.y = y;
        block.z = z;
        block.level = this;
        block.layer = layer;

        try {
            for (Consumer<Block> callback : this.callbackBlockSet.values()) {
                callback.accept(block);
            }
        } catch (Exception e) {
            Server.getInstance().getLogger().error("Error while calling block set callback", e);
        }

        int cx = x >> 4;
        int cz = z >> 4;

        if (direct) {
            this.sendBlocks(this.getChunkPlayers(cx, cz).values().toArray(Player.EMPTY_ARRAY), new Block[]{block}, UpdateBlockPacket.FLAG_ALL_PRIORITY, block.layer);
        } else {
            this.addBlockChange(Level.chunkHash(cx, cz), x, y, z);
        }

        for (ChunkLoader loader : this.getChunkLoaders(cx, cz)) {
            loader.onBlockChanged(block);
        }
        if (update) {
            if (blockPrevious.isTransparent() != block.isTransparent() || blockPrevious.getLightLevel() != block.getLightLevel()) {
                addLightUpdate(x, y, z);
            }
            BlockUpdateEvent ev = new BlockUpdateEvent(block);
            this.server.getPluginManager().callEvent(ev);
            if (!ev.isCancelled()) {
                for (Entity entity : this.getNearbyEntities(new SimpleAxisAlignedBB(x - 1, y - 1, z - 1, x + 1, y + 1, z + 1))) {
                    entity.scheduleUpdate();
                }
                block = ev.getBlock();
                block.onUpdate(BLOCK_UPDATE_NORMAL);
                block.getLevelBlockAtLayer(layer == 0 ? 1 : 0).onUpdate(BLOCK_UPDATE_NORMAL);
                if (block.isTransparent()) {
                    this.antiXrayOnBlockChange(null, block, 1);
                }
                this.updateAround(new Vector3(x, y, z));
            }
        }
        return true;
    }

    /**
     * 破坏指定方块并生成破坏效果 <br/>
     * Break the specified block and generate destruction effects
     *
     * @param block 要破坏的方块实例 <br/>
     *              The block instance to break
     */
    public void breakBlock(@NotNull Block block) {
        if(block.isValid() && block.level == this) {
            this.setBlock(block, Block.get(Block.AIR));
            Position position = block.add(0.5, 0.5, 0.5);
            this.addParticle(new DestroyBlockParticle(position, block));
            //this.getVibrationManager().callVibrationEvent(new VibrationEvent(null, position, VibrationType.BLOCK_DESTROY));
        }
    }

    private void addBlockChange(int x, int y, int z) {
        long index = Level.chunkHash(x >> 4, z >> 4);
        addBlockChange(index, x, y, z);
    }

    private void addBlockChange(long index, int x, int y, int z) {
        synchronized (changedBlocks) {
            SoftReference<Map<Integer, Object>> current = changedBlocks.computeIfAbsent(index, k -> new SoftReference<>(new HashMap<>()));
            Map<Integer, Object> currentMap = current.get();
            if (currentMap != changeBlocksFullMap && currentMap != null) {
                if (currentMap.size() > MAX_BLOCK_CACHE) {
                    this.changedBlocks.put(index, new SoftReference<>(changeBlocksFullMap));
                } else {
                    currentMap.put(Level.localBlockHash(x, y, z, this.getDimensionData()), changeBlocksPresent);
                }
            }
        }
    }

    public void antiXrayOnBlockChange(@Nullable Player player, @NotNull Vector3 vector3, int type) {
        if (!this.antiXrayEnabled()) {
            return;
        }

        //获取要发送的方块位置
        Vector3[] vector3Array;
        switch (type) {
            case 0 -> { //explode
                vector3 = vector3.floor();
                vector3Array = new Vector3[] {
                        vector3.add(1, 0, 0),
                        vector3.add(-1, 0, 0),
                        vector3.add(0, 1, 0),
                        vector3.add(0, -1, 0),
                        vector3.add(0, 0, 1),
                        vector3.add(0, 0, -1)
                };
            }
            case 1 -> { //block change
                vector3Array = new Vector3[26];
                int index = 0;
                for (int x = -1; x < 2; x++) {
                    for (int z = -1; z < 2; z++) {
                        for (int y = -1; y < 2; y++) {
                            if (x != 0 && y != 0 && z != 0) {
                                vector3Array[index] = vector3.add(x, y, z);
                                index++;
                            }
                        }
                    }
                }
            }
            case 2 -> { //player move
                vector3Array = new Vector3[100];
                int index = 0;
                for (int x = -2; x < 3; x++) {
                    for (int z = -2; z < 3; z++) {
                        for (int y = -1; y < 3; y++) {
                            vector3Array[index] = vector3.add(x, y, z);
                            index++;
                        }
                    }
                }
            }
            default -> {
                return;
            }
        }

        //发送给玩家
        for (Vector3 v : vector3Array) {
            if (v == null) {
                continue;
            }
            int x = (int) v.x;
            int y = (int) v.y;
            int z = (int) v.z;
            FullChunk fullChunk = player == null ? null : player.chunk;

            int fullId = this.getFullBlock(fullChunk, x, y, z, 0);
            int id = fullId >> Block.DATA_BITS;
            if (id >= Block.MAX_BLOCK_ID || !Level.xrayableBlocks[id]) {
                continue;
            }

            if (player == null) {
                Object2ObjectMap<GameVersion, ObjectList<Player>> players = Server.groupPlayersByGameVersion(this.getChunkPlayers(v.getChunkX(), v.getChunkZ()).values().toArray(Player.EMPTY_ARRAY));
                for (Map.Entry<GameVersion, ObjectList<Player>> entry : players.entrySet()) {
                    GameVersion gameVersion = entry.getKey();

                    UpdateBlockPacket pk = new UpdateBlockPacket();
                    pk.x = x;
                    pk.y = y;
                    pk.z = z;
                    pk.flags = UpdateBlockPacket.FLAG_ALL;

                    if (gameVersion.getProtocol() > ProtocolInfo.v1_2_10) {
                        pk.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion, id, fullId & 0xf);
                    } else {
                        pk.blockId = id;
                        pk.blockData = fullId & 0xf;
                    }

                    for (Player p : entry.getValue()) {
                        p.dataPacket(pk);
                    }
                }
            } else {
                UpdateBlockPacket pk = new UpdateBlockPacket();
                pk.x = x;
                pk.y = y;
                pk.z = z;
                pk.flags = UpdateBlockPacket.FLAG_ALL;

                if (player.protocol > ProtocolInfo.v1_2_10) {
                    pk.blockRuntimeId = GlobalBlockPalette.getOrCreateRuntimeId(player.getGameVersion(), id, fullId & 0xf);
                } else {
                    pk.blockId = id;
                    pk.blockData = fullId & 0xf;
                }

                player.dataPacket(pk);
            }
        }
    }

    public void dropItem(Vector3 source, Item item) {
        this.dropItem(source, item, null);
    }

    public void dropItem(Vector3 source, Item item, Vector3 motion) {
        this.dropItem(source, item, motion, 10);
    }

    public void dropItem(Vector3 source, Item item, Vector3 motion, int delay) {
        this.dropItem(source, item, motion, false, delay);
    }

    public void dropItem(Vector3 source, Item item, Vector3 motion, boolean dropAround, int delay) {
        if (item.getId() != 0 && item.getCount() > 0) {
            if (motion == null) {
                if (dropAround) {
                    float f = ThreadLocalRandom.current().nextFloat() * 0.5f;
                    float f1 = ThreadLocalRandom.current().nextFloat() * 6.2831855f;

                    motion = new Vector3(-MathHelper.sin(f1) * f, 0.20000000298023224, MathHelper.cos(f1) * f);
                } else {
                    motion = new Vector3(Utils.random.nextDouble() * 0.2 - 0.1, 0.2, Utils.random.nextDouble() * 0.2 - 0.1);
                }
            }

            CompoundTag itemTag = NBTIO.putItemHelper(item);
            itemTag.setName("Item");

            EntityItem itemEntity = new EntityItem(
                    this.getChunk((int) source.getX() >> 4, (int) source.getZ() >> 4, true),
                    new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", source.getX()))
                                    .add(new DoubleTag("", source.getY())).add(new DoubleTag("", source.getZ())))

                            .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", motion.x))
                                    .add(new DoubleTag("", motion.y)).add(new DoubleTag("", motion.z)))

                            .putList(new ListTag<FloatTag>("Rotation")
                                    .add(new FloatTag("", ThreadLocalRandom.current().nextFloat() * 360))
                                    .add(new FloatTag("", 0)))

                            .putShort("Health", 5).putCompound("Item", itemTag).putShort("PickupDelay", delay));

            itemEntity.spawnToAll();
        }
    }

    public EntityItem dropAndGetItem(Vector3 source, Item item) {
        return this.dropAndGetItem(source, item, null);
    }

    public EntityItem dropAndGetItem(Vector3 source, Item item, Vector3 motion) {
        return this.dropAndGetItem(source, item, motion, 10);
    }

    public EntityItem dropAndGetItem(Vector3 source, Item item, Vector3 motion, int delay) {
        return this.dropAndGetItem(source, item, motion, false, delay);
    }

    public EntityItem dropAndGetItem(Vector3 source, Item item, Vector3 motion, boolean dropAround, int delay) {
        EntityItem itemEntity = null;

        if (motion == null) {
            if (dropAround) {
                float f = ThreadLocalRandom.current().nextFloat() * 0.5f;
                float f1 = ThreadLocalRandom.current().nextFloat() * 6.2831855f;

                motion = new Vector3(-MathHelper.sin(f1) * f, 0.20000000298023224, MathHelper.cos(f1) * f);
            } else {
                motion = new Vector3(Utils.random.nextDouble() * 0.2 - 0.1, 0.2,
                        Utils.random.nextDouble() * 0.2 - 0.1);
            }
        }

        CompoundTag itemTag = NBTIO.putItemHelper(item);
        itemTag.setName("Item");

        if (item.getId() > 0 && item.getCount() > 0) {
            itemEntity = (EntityItem) Entity.createEntity("Item",
                    this.getChunk((int) source.getX() >> 4, (int) source.getZ() >> 4, true),
                    new CompoundTag().putList(new ListTag<DoubleTag>("Pos").add(new DoubleTag("", source.getX()))
                                    .add(new DoubleTag("", source.getY())).add(new DoubleTag("", source.getZ())))

                            .putList(new ListTag<DoubleTag>("Motion").add(new DoubleTag("", motion.x))
                                    .add(new DoubleTag("", motion.y)).add(new DoubleTag("", motion.z)))

                            .putList(new ListTag<FloatTag>("Rotation")
                                    .add(new FloatTag("", ThreadLocalRandom.current().nextFloat() * 360))
                                    .add(new FloatTag("", 0)))

                            .putShort("Health", 5).putCompound("Item", itemTag).putShort("PickupDelay", delay));

            if (itemEntity != null) {
                itemEntity.spawnToAll();
            }
        }

        return itemEntity;
    }

    public Item useBreakOn(Vector3 vector) {
        return this.useBreakOn(vector, null);
    }

    public Item useBreakOn(Vector3 vector, Item item) {
        return this.useBreakOn(vector, item, null);
    }

    public Item useBreakOn(Vector3 vector, Item item, Player player) {
        return this.useBreakOn(vector, item, player, false);
    }

    public Item useBreakOn(Vector3 vector, Item item, Player player, boolean createParticles) {
        return useBreakOn(vector, null, item, player, createParticles);
    }

    public Item useBreakOn(Vector3 vector, BlockFace face, Item item, Player player, boolean createParticles) {
        if (player != null && player.getGamemode() > Player.ADVENTURE) {
            return null;
        }
        Block target = this.getBlock(vector);
        Item[] drops;
        int dropExp = target.getDropExp();

        if (item == null) {
            item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        }

        boolean isSilkTouch = item.hasEnchantment(Enchantment.ID_SILK_TOUCH);

        if (player != null) {
            if (player.getGamemode() == Player.ADVENTURE) {
                Tag tag = item.getNamedTagEntry("CanDestroy");
                boolean canBreak = false;
                if (tag instanceof ListTag) {
                    for (Tag v : ((ListTag<? extends Tag>) tag).getAll()) {
                        if (v instanceof StringTag) {
                            Item entry = Item.fromString(((StringTag) v).data);
                            if (entry.getId() > 0 && entry.getBlockUnsafe() != null && entry.getBlockUnsafe().getId() == target.getId()) {
                                canBreak = true;
                                break;
                            }
                        }
                    }
                }
                if (!canBreak) {
                    return null;
                }
            }

            double breakTime = target.calculateBreakTime(item, player);

            if (player.isCreative() && breakTime > 0.15) {
                breakTime = 0.15;
            }

            if (player.hasEffect(Effect.HASTE)) {
                breakTime *= 1 - (0.2 * (player.getEffect(Effect.HASTE).getAmplifier() + 1));
            }

            if (player.hasEffect(Effect.MINING_FATIGUE)) {
                breakTime *= 1 - (0.3 * (player.getEffect(Effect.MINING_FATIGUE).getAmplifier() + 1));
            }

            Enchantment eff = item.getEnchantment(Enchantment.ID_EFFICIENCY);

            if (eff != null && eff.getLevel() > 0) {
                breakTime *= 1 - (0.3 * eff.getLevel());
            }

            breakTime -= 0.15;

            Item[] eventDrops;
            if (isSilkTouch && target.canSilkTouch() || target.isDropOriginal(player)) {
                eventDrops = new Item[]{target.toItem()};
            } else {
                eventDrops = target.getDrops(player, item);
            }
            //TODO 直接加1000可能会影响其他判断，需要进一步改进
            boolean fastBreak = (player.lastBreak + breakTime * 1000) > Long.sum(System.currentTimeMillis(), 1000);
            BlockBreakEvent ev = new BlockBreakEvent(player, target, face, item, eventDrops, player.isCreative(), fastBreak);

            if ((player.isSurvival() || player.isAdventure()) && !target.isBreakable(item)) {
                ev.setCancelled();
            } else if (!player.isOp() && isInSpawnRadius(target)) {
                ev.setCancelled();
            } else if (!ev.getInstaBreak() && ev.isFastBreak()) {
                ev.setCancelled();
            }

            player.lastBreak = System.currentTimeMillis();

            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return null;
            }

            drops = ev.getDrops();
            dropExp = ev.getDropExp();
        } else if (!target.isBreakable(item)) {
            return null;
        } else if (item.hasEnchantment(Enchantment.ID_SILK_TOUCH)) {
            drops = new Item[]{target.toItem()};
        } else {
            drops = target.getDrops(null, item);
        }

        if (createParticles) {
            Map<Integer, Player> players = this.getChunkPlayers((int) target.x >> 4, (int) target.z >> 4);
            this.addParticle(new DestroyBlockParticle(target.add(0.5), target), players.values());
        }

        BlockEntity blockEntity = this.getBlockEntity(target);
        if (blockEntity != null) {
            blockEntity.onBreak();
            blockEntity.close();

            this.updateComparatorOutputLevel(target);
        }

        target.onBreak(item, player);

        item.useOn(target);
        if (item.isTool() && item.getDamage() >= item.getMaxDurability()) {
            this.addSoundToViewers(target, cn.nukkit.level.Sound.RANDOM_BREAK);
            this.addParticle(new ItemBreakParticle(target, item));
            item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        }

        if (this.gameRules.getBoolean(GameRule.DO_TILE_DROPS)) {
            if (!isSilkTouch && player != null && drops.length != 0) { // For example no xp from redstone if it's mined with stone pickaxe
                if (player.isSurvival() || player.isAdventure()) {
                    this.dropExpOrb(vector.add(0.5, 0.5, 0.5), dropExp);
                }
            }

            for (Item drop : drops) {
                if (drop.getCount() > 0) {
                    this.dropItem(vector.add(0.5, 0.5, 0.5), drop);
                }
            }
        }

        return item;
    }

    public void dropExpOrb(Vector3 source, int exp) {
        if (exp > 0) {
            dropExpOrb(source, exp, null);
        }
    }

    public void dropExpOrb(Vector3 source, int exp, Vector3 motion) {
        dropExpOrb(source, exp, motion, 10);
    }

    public void dropExpOrb(Vector3 source, int exp, Vector3 motion, int delay) {
        Random rand = ThreadLocalRandom.current();
        for (int split : EntityXPOrb.splitIntoOrbSizes(exp)) {
            CompoundTag nbt = Entity.getDefaultNBT(source, motion == null ? new Vector3(
                            (rand.nextDouble() * 0.2 - 0.1) * 2,
                            rand.nextDouble() * 0.4,
                            (rand.nextDouble() * 0.2 - 0.1) * 2) : motion,
                    rand.nextFloat() * 360f, 0);
            nbt.putShort("Value", split);
            nbt.putShort("PickupDelay", delay);
            Entity.createEntity("XpOrb", this.getChunk(source.getChunkX(), source.getChunkZ()), nbt).spawnToAll();
        }
    }

    public Item useItemOn(@NotNull Vector3 vector, @NotNull Item item, @NotNull BlockFace face, float fx, float fy, float fz) {
        return this.useItemOn(vector, item, face, fx, fy, fz, null);
    }

    public Item useItemOn(@NotNull Vector3 vector, @NotNull Item item, @NotNull BlockFace face, float fx, float fy, float fz, @Nullable Player player) {
        return this.useItemOn(vector, item, face, fx, fy, fz, player, true);
    }

    @SuppressWarnings("unchecked")
    public Item useItemOn(@NotNull Vector3 vector, @NotNull Item item, @NotNull BlockFace face, float fx, float fy, float fz, @Nullable Player player, boolean playSound) {
        Block target = this.getBlock(vector);
        Block block = target.getSide(face);

        if (!isYInRange(block.getFloorY())) {
            return null;
        }

        if (target.getId() == Item.AIR) {
            return null;
        }

        if (item.getBlock() instanceof BlockScaffolding && face == BlockFace.UP && block.getId() == BlockID.SCAFFOLDING) {
            while (block instanceof BlockScaffolding) {
                block = block.up();
            }
        }

        if (player != null) {
            PlayerInteractEvent ev = new PlayerInteractEvent(player, item, target, face, Action.RIGHT_CLICK_BLOCK);

            if (player.getGamemode() > Player.ADVENTURE) {
                ev.setCancelled();
            }

            if (!player.isOp() && isInSpawnRadius(target)) {
                ev.setCancelled();
            }

            this.server.getPluginManager().callEvent(ev);

            if (!ev.isCancelled()) {
                target.onTouch(player, ev.getAction());

                if ((!player.isSneaking() || player.getInventory().getItemInHand().isNull()) && target.canBeActivated() && target.onActivate(item, player)) {
                    if (item.isTool() && item.getDamage() >= item.getMaxDurability()) {
                        this.addSoundToViewers(target, cn.nukkit.level.Sound.RANDOM_BREAK);
                        this.addParticle(new ItemBreakParticle(target, item));
                        item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
                    }

                    return item;
                }

                if (item.canBeActivated() && item.onActivate(this, player, block, target, face, fx, fy, fz)) {
                    if (item.getCount() <= 0) {
                        item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
                        return item;
                    }
                }
            } else {
                if (item.getId() == ItemID.BUCKET && ItemBucket.getDamageByTarget(item.getDamage()) == BlockID.WATER) {
                    player.getLevel().sendBlocks(new Player[]{player}, new Block[]{Block.get(Block.AIR, 0, target.getLevelBlockAtLayer(1))}, UpdateBlockPacket.FLAG_ALL_PRIORITY, 1);
                }
                return null;
            }

            if (item.getId() == ItemID.BUCKET && ItemBucket.getDamageByTarget(item.getDamage()) == BlockID.WATER) {
                player.getLevel().sendBlocks(new Player[]{player}, new Block[]{target.getLevelBlockAtLayer(1)}, UpdateBlockPacket.FLAG_ALL_PRIORITY, 1);
            }
        } else if (target.canBeActivated() && target.onActivate(item, null)) {
            if (item.isTool() && item.getDamage() >= item.getMaxDurability()) {
                this.addSoundToViewers(target, cn.nukkit.level.Sound.RANDOM_BREAK);
                this.addParticle(new ItemBreakParticle(target, item));
                item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
            }

            return item;
        }

        Block hand;

        if (item.canBePlaced()) {
            hand = item.getBlock();
            hand.position(block);
        } else {
            return null;
        }

        if (!(block.canBeReplaced() || (hand instanceof BlockSlab && block instanceof BlockSlab && hand.getId() == block.getId()))) {
            return null;
        }

        if (target.canBeReplaced()) {
            Block b = item.getBlockUnsafe();
            if (b != null && target.getId() == b.getId() && target.getDamage() == b.getDamage()) {
                return item; // No need to sync item
            }

            block = target;
            hand.position(block);
        }


        if (!hand.canPassThrough() && hand.getBoundingBox() != null) {
            Entity[] entities = this.getCollidingEntities(hand.getBoundingBox());
            //int realCount = 0;
            for (Entity e : entities) {
                if (e == player || e instanceof EntityArrow || e instanceof EntityItem || (e instanceof Player && ((Player) e).isSpectator() || !e.canCollide())) {
                    continue;
                }
                //++realCount;
                return null;
            }

            if (player != null) {
                Vector3 diff = player.getNextPosition().subtract(player.getPosition());
                //if (diff.lengthSquared() > 0.00001) {
                AxisAlignedBB bb = player.getBoundingBox().getOffsetBoundingBox(diff.x, diff.y, diff.z);
                bb.expand(-0.01, -0.01, -0.01);
                bb.setMinY(bb.getMinY() + 0.05);
                if (hand.getBoundingBox().intersectsWith(bb)) {
                    return null;
                }

                //}
            }

            /*if (realCount > 0) {
                return null;
            }*/
        }

        if (player != null) {
            BlockPlaceEvent event = new BlockPlaceEvent(player, hand, block, target, item);
            if (player.getGamemode() == Player.ADVENTURE) {
                Tag tag = item.getNamedTagEntry("CanPlaceOn");
                boolean canPlace = false;
                if (tag instanceof ListTag) {
                    for (Tag v : ((ListTag<Tag>) tag).getAll()) {
                        if (v instanceof StringTag) {
                            Item entry = Item.fromString(((StringTag) v).data);
                            if (entry.getId() > 0 && entry.getBlockUnsafe() != null && entry.getBlockUnsafe().getId() == target.getId()) {
                                canPlace = true;
                                break;
                            }
                        }
                    }
                }
                if (!canPlace) {
                    event.setCancelled();
                }
            }

            if (!player.isOp() && isInSpawnRadius(target)) {
                event.setCancelled();
            }

            this.server.getPluginManager().callEvent(event);
            if (event.isCancelled()) {
                return null;
            }

            if (server.mobsFromBlocks) {
                if (item.getId() == Item.JACK_O_LANTERN || item.getId() == Item.PUMPKIN) {
                    if (block.getSide(BlockFace.DOWN).getId() == Item.SNOW_BLOCK && block.getSide(BlockFace.DOWN, 2).getId() == Item.SNOW_BLOCK) {
                        block.getLevel().setBlock(target, Block.get(BlockID.AIR));
                        block.getLevel().setBlock(target.add(0, -1, 0), Block.get(BlockID.AIR));

                        Position spawnPos = target.add(0.5, -1, 0.5);

                        CreatureSpawnEvent ev = new CreatureSpawnEvent(EntitySnowGolem.NETWORK_ID, spawnPos, CreatureSpawnEvent.SpawnReason.BUILD_SNOWMAN, player);
                        server.getPluginManager().callEvent(ev);

                        if (ev.isCancelled()) {
                            return null;
                        }

                        Entity.createEntity("SnowGolem", spawnPos).spawnToAll();

                        if (!player.isCreative()) {
                            item.setCount(item.getCount() - 1);
                            player.getInventory().setItemInHand(item);
                        }
                        return null;
                    } else if (block.getSide(BlockFace.DOWN).getId() == Item.IRON_BLOCK && block.getSide(BlockFace.DOWN, 2).getId() == Item.IRON_BLOCK) {
                        block = block.getSide(BlockFace.DOWN);
                        Block first, second = null;
                        if ((first = block.getSide(BlockFace.EAST)).getId() == Item.IRON_BLOCK && (second = block.getSide(BlockFace.WEST)).getId() == Item.IRON_BLOCK) {
                            block.getLevel().setBlock(first, Block.get(BlockID.AIR));
                            block.getLevel().setBlock(second, Block.get(BlockID.AIR));
                        } else if ((first = block.getSide(BlockFace.NORTH)).getId() == Item.IRON_BLOCK && (second = block.getSide(BlockFace.SOUTH)).getId() == Item.IRON_BLOCK) {
                            block.getLevel().setBlock(first, Block.get(BlockID.AIR));
                            block.getLevel().setBlock(second, Block.get(BlockID.AIR));
                        }

                        if (second != null) {
                            block.getLevel().setBlock(block, Block.get(BlockID.AIR));
                            block.getLevel().setBlock(block.add(0, -1, 0), Block.get(BlockID.AIR));

                            Position spawnPos = block.add(0.5, -1, 0.5);

                            CreatureSpawnEvent ev = new CreatureSpawnEvent(EntityIronGolem.NETWORK_ID, spawnPos, CreatureSpawnEvent.SpawnReason.BUILD_IRONGOLEM, player);
                            server.getPluginManager().callEvent(ev);

                            if (ev.isCancelled()) {
                                return null;
                            }

                            Entity.createEntity("IronGolem", spawnPos).spawnToAll();

                            if (!player.isCreative()) {
                                item.setCount(item.getCount() - 1);
                                player.getInventory().setItemInHand(item);
                            }
                            return null;
                        }
                    }
                }
            }
        }

        if (hand.getWaterloggingType() == Block.WaterloggingType.NO_WATERLOGGING && hand.canBeFlowedInto() && (block instanceof BlockLiquid || block.getLevelBlockAtLayer(1) instanceof BlockLiquid)) {
            return null;
        }

        boolean liquidMoved = false;
        if ((block instanceof BlockLiquid) && ((BlockLiquid) block).usesWaterLogging()) {
            liquidMoved = true;
            this.setBlock(block, 1, block, false, false);
            this.setBlock(block, 0, Block.get(BlockID.AIR), false, false);
            this.scheduleUpdate(block, 1);
        }

        if (!hand.place(item, block, target, face, fx, fy, fz, player)) {
            if (liquidMoved) {
                this.setBlock(block, 0, block, false, false);
                this.setBlock(block, 1, Block.get(BlockID.AIR), false, false);
            }
            return null;
        }

        if (player != null) {
            if (!player.isCreative()) {
                item.setCount(item.getCount() - 1);
            }
        }


        if (playSound) {
            Object2ObjectMap<GameVersion, ObjectList<Player>> players = Server.groupPlayersByGameVersion(this.getChunkPlayers(hand.getChunkX(), hand.getChunkZ()).values());
            for (GameVersion gameVersion : players.keySet()) {
                ObjectList<Player> targets = players.get(gameVersion);
                int soundData = GlobalBlockPalette.getOrCreateRuntimeId(gameVersion.getProtocol() > ProtocolInfo.v1_2_10 ? gameVersion : GameVersion.getLastVersion(), // no block palette in <= 1.2.10
                        hand.getId(), hand.getDamage());
                this.addLevelSoundEvent(hand, LevelSoundEventPacket.SOUND_PLACE, soundData, targets.toArray(Player.EMPTY_ARRAY));
            }
        }

        if (item.getCount() <= 0) {
            item = new ItemBlock(Block.get(BlockID.AIR), 0, 0);
        }
        return item;
    }

    public boolean isInSpawnRadius(Vector3 vector3) {
        return server.getSpawnRadius() > -1 && new Vector2(vector3.x, vector3.z).distance(new Vector2(this.getSpawnLocation().x, this.getSpawnLocation().z)) <= server.getSpawnRadius();
    }

    public Entity getEntity(long entityId) {
        return this.entities.containsKey(entityId) ? this.entities.get(entityId) : null;
    }

    public Entity[] getEntities() {
        return entities.values().toArray(new Entity[0]);
    }

    public Entity[] getCollidingEntities(AxisAlignedBB bb) {
        return this.getCollidingEntities(bb, null);
    }

    public Entity[] getCollidingEntities(AxisAlignedBB bb, Entity entity) {
        List<Entity> nearby = new ArrayList<>();

        if (entity == null || entity.canCollide()) {
            int minX = NukkitMath.floorDouble((bb.getMinX() - 2) / 16);
            int maxX = NukkitMath.ceilDouble((bb.getMaxX() + 2) / 16);
            int minZ = NukkitMath.floorDouble((bb.getMinZ() - 2) / 16);
            int maxZ = NukkitMath.ceilDouble((bb.getMaxZ() + 2) / 16);

            for (int x = minX; x <= maxX; ++x) {
                for (int z = minZ; z <= maxZ; ++z) {
                    for (Entity ent : this.getChunkEntities(x, z, false).values()) {
                        if ((entity == null || (ent != entity && entity.canCollideWith(ent)))
                                && ent.boundingBox.intersectsWith(bb)) {
                            nearby.add(ent);
                        }
                    }
                }
            }
        }

        return nearby.toArray(new Entity[0]);
    }

    public Entity[] getNearbyEntities(AxisAlignedBB bb) {
        return this.getNearbyEntities(bb, null);
    }

    private static final Entity[] EMPTY_ENTITY_ARR = new Entity[0];
    private static final Entity[] ENTITY_BUFFER = new Entity[512];

    public Entity[] getNearbyEntities(AxisAlignedBB bb, Entity entity) {
        return getNearbyEntities(bb, entity, false);
    }

    public Entity[] getNearbyEntities(AxisAlignedBB bb, Entity entity, boolean loadChunks) {
        int index = 0;

        int minX = NukkitMath.floorDouble((bb.getMinX() - 2) * 0.0625);
        int maxX = NukkitMath.ceilDouble((bb.getMaxX() + 2) * 0.0625);
        int minZ = NukkitMath.floorDouble((bb.getMinZ() - 2) * 0.0625);
        int maxZ = NukkitMath.ceilDouble((bb.getMaxZ() + 2) * 0.0625);

        ArrayList<Entity> overflow = null;

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                for (Entity ent : this.getChunkEntities(x, z, loadChunks).values()) {
                    if (ent != entity && ent.boundingBox.intersectsWith(bb)) {
                        if (index < ENTITY_BUFFER.length) {
                            ENTITY_BUFFER[index] = ent;
                        } else {
                            if (overflow == null) overflow = new ArrayList<>(1024);
                            overflow.add(ent);
                        }
                        index++;
                    }
                }
            }
        }

        if (index == 0) return EMPTY_ENTITY_ARR;
        Entity[] copy;
        if (overflow == null) {
            copy = Arrays.copyOfRange(ENTITY_BUFFER, 0, index);
            Arrays.fill(ENTITY_BUFFER, 0, index, null);
        } else {
            copy = new Entity[ENTITY_BUFFER.length + overflow.size()];
            System.arraycopy(ENTITY_BUFFER, 0, copy, 0, ENTITY_BUFFER.length);
            for (int i = 0; i < overflow.size(); i++) {
                copy[ENTITY_BUFFER.length + i] = overflow.get(i);
            }
        }
        return copy;
    }

    @NonComputationAtomic
    public Map<Long, BlockEntity> getBlockEntities() {
        return blockEntities;
    }

    public BlockEntity getBlockEntityById(long blockEntityId) {
        return this.blockEntities.containsKey(blockEntityId) ? this.blockEntities.get(blockEntityId) : null;
    }

    @NonComputationAtomic
    public Map<Long, Player> getPlayers() {
        return players;
    }

    public Map<Integer, ChunkLoader> getLoaders() {
        return loaders;
    }

    public BlockEntity getBlockEntity(Vector3 pos) {
        return this.getBlockEntity(null, pos);
    }

    public BlockEntity getBlockEntity(BlockVector3 pos) {
        return this.getBlockEntity(null, pos.asVector3());
    }

    public BlockEntity getBlockEntity(FullChunk chunk, Vector3 pos) {
        int by = pos.getFloorY();
        if (!isYInRange(by)) {
            return null;
        }

        int cx = (int) pos.x >> 4;
        int cz = (int) pos.z >> 4;
        if (chunk == null || cx != chunk.getX() || cz != chunk.getZ()) {
            chunk = this.getChunk(cx, cz, false);
        }

        if (chunk != null) {
            return chunk.getTile((int) pos.x & 0x0f, by, (int) pos.z & 0x0f);
        }

        return null;
    }

    public BlockEntity getBlockEntityIfLoaded(Vector3 pos) {
        return this.getBlockEntityIfLoaded(null, pos);
    }

    /**
     * 如果指定位置的区块已加载，则获取该位置的方块实体。
     * If the chunk at the specified position is loaded, retrieve the block entity at that position.
     *
     * @param chunk 要检查的区块，如果为 null 则尝试从世界中获取。
     *              The chunk to check. If it is null, attempt to retrieve it from the world.
     * @param pos   方块实体所在的位置。
     *              The position where the block entity is located.
     * @return 如果区块已加载且存在方块实体，则返回该方块实体；否则返回 null。
     *         If the chunk is loaded and there is a block entity, return the block entity; otherwise, return null.
     */
    public BlockEntity getBlockEntityIfLoaded(FullChunk chunk, Vector3 pos) {
        int by = pos.getFloorY();
        if (!isYInRange(by)) {
            return null;
        }

        int cx = (int) pos.x >> 4;
        int cz = (int) pos.z >> 4;
        if (chunk == null || cx != chunk.getX() || cz != chunk.getZ()) {
            chunk = this.getChunkIfLoaded(cx, cz);
        }

        if (chunk != null) {
            return chunk.getTile((int) pos.x & 0x0f, by, (int) pos.z & 0x0f);
        }

        return null;
    }

    public Map<Long, Entity> getChunkEntities(int X, int Z) {
        return getChunkEntities(X, Z, true);
    }

    public Map<Long, Entity> getChunkEntities(int X, int Z, boolean loadChunks) {
        FullChunk chunk = loadChunks ? this.getChunk(X, Z) : this.getChunkIfLoaded(X, Z);
        return chunk != null ? chunk.getEntities() : Collections.emptyMap();
    }

    public Map<Long, BlockEntity> getChunkBlockEntities(int X, int Z) {
        FullChunk chunk;
        return (chunk = this.getChunk(X, Z)) != null ? chunk.getBlockEntities() : Collections.emptyMap();
    }

    @Override
    public int getBlockIdAt(int x, int y, int z) {
        return getBlockIdAt(x, y, z, 0);
    }

    @Override
    public int getBlockIdAt(int x, int y, int z, int layer) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        return this.getChunk(x >> 4, z >> 4, true).getBlockId(x & 0x0f, y, z & 0x0f, layer);
    }

    public int getBlockIdAt(FullChunk chunk, int x, int y, int z) {
        return this.getBlockIdAt(chunk, x, y, z, 0);
    }

    public int getBlockIdAt(FullChunk chunk, int x, int y, int z, int layer) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        int cx = x >> 4;
        int cz = z >> 4;
        if (chunk == null || cx != chunk.getX() || cz != chunk.getZ()) {
            chunk = this.getChunk(x >> 4, z >> 4, true);
        }
        return chunk.getBlockId(x & 0x0f, y, z & 0x0f, layer);
    }

    @Override
    public void setBlockIdAt(int x, int y, int z, int id) {
        this.setBlockIdAt(x, y, z, 0, id);
    }

    @Override
    public void setBlockIdAt(int x, int y, int z, int layer, int id) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return;
        }

        this.getChunk(x >> 4, z >> 4, true).setBlockId(x & 0x0f, y, z & 0x0f, layer, id & Block.ID_MASK);
        addBlockChange(x, y, z);
        temporalVector.setComponents(x, y, z);
        for (ChunkLoader loader : this.getChunkLoaders(x >> 4, z >> 4)) {
            loader.onBlockChanged(temporalVector);
        }
    }

    @Override
    public void setBlockAt(int x, int y, int z, int id, int data) {
        this.setBlockAtLayer(x, y, z, 0, id, data);
    }

    @Override
    public boolean setBlockAtLayer(int x, int y, int z, int layer, int id, int data) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return false;
        }

        BaseFullChunk chunk = this.getChunk(x >> 4, z >> 4, true);
        boolean changed = chunk.setBlockAtLayer(x & 0x0f, y, z & 0x0f, layer, id & Block.ID_MASK, data & Block.DATA_MASK);
        if (!changed) {
            return false;
        }

        addBlockChange(x, y, z);
        temporalVector.setComponents(x, y, z);
        for (ChunkLoader loader : this.getChunkLoaders(x >> 4, z >> 4)) {
            loader.onBlockChanged(temporalVector);
        }
        return changed;
    }

    public int getBlockExtraDataAt(int x, int y, int z) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        return this.getChunk(x >> 4, z >> 4, true).getBlockExtraData(x & 0x0f, y, z & 0x0f);
    }

    public void setBlockExtraDataAt(int x, int y, int z, int id, int data) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return;
        }

        this.getChunk(x >> 4, z >> 4, true).setBlockExtraData(x & 0x0f, y, z & 0x0f, (data << 8) | id);

        this.sendBlockExtraData(x, y, z, id, data);
    }

    @Override
    public int getBlockDataAt(int x, int y, int z) {
        return this.getBlockDataAt(x, y, z, 0);
    }

    @Override
    public int getBlockDataAt(int x, int y, int z, int layer) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        return this.getChunk(x >> 4, z >> 4, true).getBlockData(x & 0x0f, y, z & 0x0f, layer);
    }

    @Override
    public void setBlockDataAt(int x, int y, int z, int data) {
        this.setBlockDataAt(x, y, z, 0, data);
    }

    @Override
    public void setBlockDataAt(int x, int y, int z, int layer, int data) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return;
        }

        this.getChunk(x >> 4, z >> 4, true).setBlockData(x & 0x0f, y, z & 0x0f, layer, data & Block.DATA_MASK);
        addBlockChange(x, y, z);
        temporalVector.setComponents(x, y, z);
        for (ChunkLoader loader : this.getChunkLoaders(x >> 4, z >> 4)) {
            loader.onBlockChanged(temporalVector);
        }
    }

    public synchronized int getBlockSkyLightAt(int x, int y, int z) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        return this.getChunk(x >> 4, z >> 4, true).getBlockSkyLight(x & 0x0f, y, z & 0x0f);
    }

    public synchronized void setBlockSkyLightAt(int x, int y, int z, int level) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return;
        }

        this.getChunk(x >> 4, z >> 4, true).setBlockSkyLight(x & 0x0f, y, z & 0x0f, level & 0x0f);
    }

    public synchronized int getBlockLightAt(int x, int y, int z) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return 0;
        }

        BaseFullChunk chunk = this.getChunkIfLoaded(x >> 4, z >> 4);
        return chunk == null ? 0 : chunk.getBlockLight(x & 0x0f, y, z & 0x0f);
    }

    public synchronized void setBlockLightAt(int x, int y, int z, int level) {
        if (y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return;
        }

        BaseFullChunk c = this.getChunkIfLoaded(x >> 4, z >> 4);
        if (null != c) {
            c.setBlockLight(x & 0x0f, y, z & 0x0f, level & 0x0f);
        }
    }

    public int getBiomeId(int x, int z) {
        return this.getChunk(x >> 4, z >> 4, true).getBiomeId(x & 0x0f, z & 0x0f);
    }

    public void setBiomeId(int x, int z, int biomeId) {
        this.getChunk(x >> 4, z >> 4, true).setBiomeId(x & 0x0f, z & 0x0f, biomeId & 0x0f);
    }

    public void setBiomeId(int x, int z, byte biomeId) {
        this.getChunk(x >> 4, z >> 4, true).setBiomeId(x & 0x0f, z & 0x0f, biomeId & 0x0f);
    }

    public int getHeightMap(int x, int z) {
        return this.getChunk(x >> 4, z >> 4, true).getHeightMap(x & 0x0f, z & 0x0f);
    }

    public void setHeightMap(int x, int z, int value) {
        this.getChunk(x >> 4, z >> 4, true).setHeightMap(x & 0x0f, z & 0x0f, value & 0x0f);
    }

    public int getBiomeColor(int x, int z) {
        return this.getChunk(x >> 4, z >> 4, true).getBiomeColor(x & 0x0f, z & 0x0f);
    }

    public void setBiomeColor(int x, int z, int R, int G, int B) {
        this.getChunk(x >> 4, z >> 4, true).setBiomeColor(x & 0x0f, z & 0x0f, R, G, B);
    }

    public Map<Long, ? extends FullChunk> getChunks() {
        return this.requireProvider().getLoadedChunks();
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    public BaseFullChunk getChunk(int chunkX, int chunkZ, boolean create) {
        long index = Level.chunkHash(chunkX, chunkZ);
        BaseFullChunk chunk = this.requireProvider().getLoadedChunk(index);
        if (chunk == null) {
            chunk = this.forceLoadChunk(index, chunkX, chunkZ, create);
        }
        return chunk;
    }

    @Nullable
    public BaseFullChunk getChunkIfLoaded(int chunkX, int chunkZ) {
        return this.requireProvider().getLoadedChunk(Level.chunkHash(chunkX, chunkZ));
    }

    public void generateChunkCallback(int x, int z, BaseFullChunk chunk) {
        generateChunkCallback(x, z, chunk, true);
    }

    public final void generateChunkCallback(final int x, final int z, BaseFullChunk chunk, final boolean isPopulated) {
        this.providerLock.readLock().lock();
        try {
            LevelProvider levelProvider = this.getProvider();
            if (levelProvider == null) {
                return;
            }
            long index = Level.chunkHash(x, z);
            if (this.chunkPopulationQueue.containsKey(index)) {
                FullChunk oldChunk = this.getChunk(x, z, false);
                for (int xx = -1; xx <= 1; ++xx) {
                    for (int zz = -1; zz <= 1; ++zz) {
                        this.chunkPopulationLock.remove(Level.chunkHash(x + xx, z + zz));
                    }
                }
                this.chunkPopulationQueue.remove(index);
                chunk.setProvider(levelProvider);
                this.setChunk(x, z, chunk, false);
                chunk = this.getChunk(x, z, false);
                if (chunk != null && (oldChunk == null || !isPopulated) && chunk.isPopulated() && chunk.getProvider() != null) {
                    this.server.getPluginManager().callEvent(new ChunkPopulateEvent(chunk));

                    for (ChunkLoader loader : this.getChunkLoaders(x, z)) {
                        loader.onChunkPopulated(chunk);
                    }
                }
            } else if (this.chunkGenerationQueue.containsKey(index) || this.chunkPopulationLock.containsKey(index)) {
                this.chunkGenerationQueue.remove(index);
                this.chunkPopulationLock.remove(index);
                chunk.setProvider(levelProvider);
                this.setChunk(x, z, chunk, false);
            } else {
                chunk.setProvider(levelProvider);
                this.setChunk(x, z, chunk, false);
            }
        } finally {
            this.providerLock.readLock().unlock();
        }
    }

    @Override
    public void setChunk(int chunkX, int chunkZ) {
        this.setChunk(chunkX, chunkZ, null);
    }

    @Override
    public void setChunk(int chunkX, int chunkZ, BaseFullChunk chunk) {
        this.setChunk(chunkX, chunkZ, chunk, true);
    }

    public void setChunk(int chunkX, int chunkZ, BaseFullChunk chunk, boolean unload) {
        if (chunk == null) {
            return;
        }

        long index = Level.chunkHash(chunkX, chunkZ);
        FullChunk oldChunk = this.getChunk(chunkX, chunkZ, false);

        if (oldChunk != chunk) {
            if (unload && oldChunk != null) {
                this.unloadChunk(chunkX, chunkZ, false, false);
            } else {
                Map<Long, Entity> oldEntities = oldChunk != null ? oldChunk.getEntities() : Collections.emptyMap();

                Map<Long, BlockEntity> oldBlockEntities = oldChunk != null ? oldChunk.getBlockEntities() : Collections.emptyMap();

                if (!oldEntities.isEmpty()) {
                    Iterator<Map.Entry<Long, Entity>> iter = oldEntities.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Long, Entity> entry = iter.next();
                        Entity entity = entry.getValue();
                        chunk.addEntity(entity);
                        if (oldChunk != null) {
                            iter.remove();
                            oldChunk.removeEntity(entity);
                            entity.chunk = chunk;
                        }
                    }
                }

                if (!oldBlockEntities.isEmpty()) {
                    Iterator<Map.Entry<Long, BlockEntity>> iter = oldBlockEntities.entrySet().iterator();
                    while (iter.hasNext()) {
                        Map.Entry<Long, BlockEntity> entry = iter.next();
                        BlockEntity blockEntity = entry.getValue();
                        chunk.addBlockEntity(blockEntity);
                        if (oldChunk != null) {
                            iter.remove();
                            oldChunk.removeBlockEntity(blockEntity);
                            blockEntity.chunk = chunk;
                        }
                    }
                }

            }
            this.requireProvider().setChunk(chunkX, chunkZ, chunk);
        }

        chunk.setChanged();

        if (!this.isChunkInUse(index)) {
            this.unloadChunkRequest(chunkX, chunkZ);
        } else {
            for (ChunkLoader loader : this.getChunkLoaders(chunkX, chunkZ)) {
                loader.onChunkChanged(chunk);
            }
        }
    }

    public int getHighestBlockAt(int x, int z) {
        return this.getHighestBlockAt(x, z, true);
    }

    public int getHighestBlockAt(int x, int z, boolean cache) {
        return this.getChunk(x >> 4, z >> 4, true).getHighestBlockAt(x & 0x0f, z & 0x0f, cache);
    }

    public BlockColor getMapColorAt(int x, int z) {
        BlockColor color = BlockColor.VOID_BLOCK_COLOR;

        Block block = getMapColoredBlockAt(x, z);
        if (block == null) {
            return color;
        }
        if (block instanceof BlockGlass) {
            color = this.getGrassColorAt(x, z);
        } else {
            color = new BlockColor(block.getColor().getARGB(), true);
        }

        //在z轴存在高度差的地方，颜色变深或变浅
        Block nzy = getMapColoredBlockAt(x, z - 1);
        if (nzy == null) {
            return color;
        }
        if (nzy.getFloorY() > block.getFloorY()) {
            color = darker(color, 0.875 - Math.min(5, nzy.getFloorY() - block.getFloorY()) * 0.05);
        } else if (nzy.getFloorY() < block.getFloorY()) {
            color = brighter(color, 0.875 - Math.min(5, block.getFloorY() - nzy.getFloorY()) * 0.05);
        }

        double deltaY = block.y - 128;
        if (deltaY > 0) {
            color = brighter(color, 1 - deltaY / (192 * 3));
        } else if (deltaY < 0) {
            color = darker(color, 1 - (-deltaY) / (192 * 3));
        }

        if ((block.getSide(BlockFace.UP) instanceof BlockWater || block.getSideAtLayer(1, BlockFace.UP) instanceof BlockWater)) {
            int r1 = color.getRed();
            int g1 = color.getGreen();
            int b1 = color.getBlue();
            BlockColor waterBlockColor = this.getWaterColorAt(x, z);
            //在水下
            if (block.y < 62) {
                //海平面为62格。离海平面越远颜色越接近海洋颜色
                double depth = 62 - block.y;
                if (depth > 32) return waterBlockColor;
                b1 = waterBlockColor.getBlue();
                double radio = Math.max(0.5, depth / 96.0);
                r1 += (waterBlockColor.getRed() - r1) * radio;
                g1 += (waterBlockColor.getGreen() - g1) * radio;
            } else {
                //湖泊 or 河流
                b1 = waterBlockColor.getBlue();
                r1 += (waterBlockColor.getRed() - r1) * 0.5;
                g1 += (waterBlockColor.getGreen() - g1) * 0.5;
            }
            color = new BlockColor(r1, g1, b1);
        }

        return color;
    }

    protected BlockColor brighter(BlockColor source, double factor) {
        int r = source.getRed();
        int g = source.getGreen();
        int b = source.getBlue();
        int alpha = source.getAlpha();

        int i = (int) (1.0 / (1.0 - factor));
        if (r == 0 && g == 0 && b == 0) {
            return new BlockColor(i, i, i, alpha);
        }
        if (r > 0 && r < i) r = i;
        if (g > 0 && g < i) g = i;
        if (b > 0 && b < i) b = i;

        return new BlockColor(Math.min((int) (r / factor), 255),
                Math.min((int) (g / factor), 255),
                Math.min((int) (b / factor), 255),
                alpha);
    }

    protected BlockColor darker(BlockColor source, double factor) {
        return new BlockColor(Math.max((int) (source.getRed() * factor), 0),
                Math.max((int) (source.getGreen() * factor), 0),
                Math.max((int) (source.getBlue() * factor), 0),
                source.getAlpha());
    }

    protected Block getMapColoredBlockAt(int x, int z) {
        int y = getHighestBlockAt(x, z);
        while (y > this.getMinBlockY()) {
            Block block = getBlock(new Vector3(x, y, z));
            if (block.getColor() == null) return null;
            if (block.getColor().getAlpha() == 0x00 || block instanceof BlockWater) {
                y--;
            } else {
                return block;
            }
        }
        return null;
    }

    public BlockColor getGrassColorAt(int x, int z) {
        int biome = this.getBiomeId(x, z);

        switch (biome) {
            case 0: //ocean
            case 7: //river
            case 9: //end
            case 24: //deep ocean
                return new BlockColor("#8eb971");
            case 1: //plains
            case 16: //beach
            case 129: //sunflower plains
                return new BlockColor("#91bd59");
            case 2: //desert
            case 8: //hell
            case 17: //desert hills
            case 35: //savanna
            case 36: //savanna plateau
            case 130: //desert m
            case 163: //savanna m
            case 164: //savanna plateau m
                return new BlockColor("#bfb755");
            case 3: //extreme hills
            case 20: //extreme hills edge
            case 25: //stone beach
            case 34: //extreme hills
            case 131: //extreme hills m
            case 162: //extreme hills plus m
                return new BlockColor("#8ab689");
            case 4: //forest
            case 132: //flower forest
                return new BlockColor("#79c05a");
            case 5: //taiga
            case 19: //taiga hills
            case 32: //mega taiga
            case 33: //mega taiga hills
            case 133: //taiga m
            case 160: //mega spruce taiga
                return new BlockColor("#86b783");
            case 6: //swamp
            case 134: //swampland m
                return new BlockColor("#6A7039");
            case 10: //frozen ocean
            case 11: //frozen river
            case 12: //ice plains
            case 30: //cold taiga
            case 31: //cold taiga hills
            case 140: //ice plains spikes
            case 158: //cold taiga m
                return new BlockColor("#80b497");
            case 14: //mushroom island
            case 15: //mushroom island shore
                return new BlockColor("#55c93f");
            case 18: //forest hills
            case 27: //birch forest
            case 28: //birch forest hills
            case 155: //birch forest m
            case 156: //birch forest hills m
                return new BlockColor("#88bb67");
            case 21: //jungle
            case 22: //jungle hills
            case 149: //jungle m
                return new BlockColor("#59c93c");
            case 23: //jungle edge
            case 151: //jungle edge m
                return new BlockColor("#64c73f");
            case 26: //cold beach
                return new BlockColor("#83b593");
            case 29: //roofed forest
            case 157: //roofed forest m
                return new BlockColor("#507a32");
            case 37: //mesa
            case 38: //mesa plateau f
            case 39: //mesa plateau
            case 165: //mesa bryce
            case 166: //mesa plateau f m
            case 167: //mesa plateau m
                return new BlockColor("#90814d");
            default:
                return BlockColor.GRASS_BLOCK_COLOR;
        }
    }

    public BlockColor getWaterColorAt(int x, int z) {
        int biome = this.getBiomeId(x, z);

        switch (biome) {
            case 2: //desert
            case 130: //desert m
                return new BlockColor("#32A598");
            case 4: //forest
                return new BlockColor("#1E97F2");
            case 132: //flower forest
                return new BlockColor("#20A3CC");
            case 5: //taiga
            case 19: //taiga hills
            case 133: //taiga m
            case 3: //extreme hills
            case 20: //extreme hills edge
            case 34: //extreme hills
            case 131: //extreme hills m
            case 162: //extreme hills plus m
                return new BlockColor("#1E6B82");
            case 6: //swamp
                return new BlockColor("#4c6559");
            case 134: //swampland m
                return new BlockColor("#4c6156");
            case 7: //river
                return new BlockColor("#0084FF");
            case 9: //end
                return new BlockColor("#62529e");
            case 8: //hell
                return new BlockColor("#905957");
            case 11: //frozen river
                return new BlockColor("#185390");
            case 12: //ice plains
            case 140: //ice plains spikes
                return new BlockColor("#14559b");
            case 14: //mushroom island
                return new BlockColor("#8a8997");
            case 15: //mushroom island shore
                return new BlockColor("#818193");
            case 16: //beach
                return new BlockColor("#157cab");
            case 17: //desert hills
                return new BlockColor("#1a7aa1");
            case 18: //forest hills
                return new BlockColor("#056bd1");
            case 21: //jungle
                return new BlockColor("#14A2C5");
            case 22: //jungle hills
            case 149: //jungle m
                return new BlockColor("#1B9ED8");
            case 23: //jungle edge
            case 151: //jungle edge m
                return new BlockColor("#0D8AE3");
            case 25: //stone beach
                return new BlockColor("#0d67bb");
            case 26: //cold beach
                return new BlockColor("#1463a5");
            case 27: //birch forest
            case 155: //birch forest m
                return new BlockColor("#0677ce");
            case 28: //birch forest hills
            case 156: //birch forest hills m
                return new BlockColor("#0a74c4");
            case 29: //roofed forest
            case 157: //roofed forest m
                return new BlockColor("#3B6CD1");
            case 30: //cold taiga
            case 158: //cold taiga m
                return new BlockColor("#205e83");
            case 31: //cold taiga hills
                return new BlockColor("#245b78");
            case 32: //mega taiga
            case 160: //mega spruce taiga
                return new BlockColor("#2d6d77");
            case 33: //mega taiga hills
                return new BlockColor("#286378");
            case 35: //savanna
            case 163: //savanna m
                return new BlockColor("#2C8B9C");
            case 36: //savanna plateau
            case 164: //savanna plateau m
                return new BlockColor("#2590A8");
            case 0: //ocean
            case 24: //deep ocean
                return new BlockColor("#1787D4");
            case 10: //frozen ocean
                return new BlockColor("#2570B5");
            default: // plains, sunflower plains, others
                return new BlockColor("#44AFF5");
            case 37: //mesa
                return new BlockColor("#4E7F81");
            case 38: //mesa plateau f
            case 39: //mesa plateau
            case 165: //mesa bryce
                return new BlockColor("#497F99");
            case 166: //mesa plateau f m
            case 167: //mesa plateau m
                return new BlockColor("#55809E");
        }
    }

    public boolean isChunkLoaded(int x, int z) {
        return this.requireProvider().isChunkLoaded(x, z);
    }

    private boolean areNeighboringChunksLoaded(long hash) {
        LevelProvider levelProvider = this.requireProvider();
        return levelProvider.isChunkLoaded(hash + 1) &&
                levelProvider.isChunkLoaded(hash - 1) &&
                levelProvider.isChunkLoaded(hash + (4294967296L)) &&
                levelProvider.isChunkLoaded(hash - (4294967296L));
    }

    public boolean isChunkGenerated(int x, int z) {
        FullChunk chunk = this.getChunk(x, z);
        return chunk != null && chunk.isGenerated();
    }

    public boolean isChunkPopulated(int x, int z) {
        FullChunk chunk = this.getChunk(x, z);
        return chunk != null && chunk.isPopulated();
    }

    public Position getSpawnLocation() {
        return Position.fromObject(this.requireProvider().getSpawn(), this);
    }

    public void setSpawnLocation(Vector3 pos) {
        Position previousSpawn = this.getSpawnLocation();
        this.requireProvider().setSpawn(pos);
        this.server.getPluginManager().callEvent(new SpawnChangeEvent(this, previousSpawn));
        SetSpawnPositionPacket pk = new SetSpawnPositionPacket();
        pk.spawnType = SetSpawnPositionPacket.TYPE_WORLD_SPAWN;
        pk.x = pos.getFloorX();
        pk.y = pos.getFloorY();
        pk.z = pos.getFloorZ();
        pk.dimension = this.getDimension();
        for (Player p : getPlayers().values()) p.dataPacket(pk);
    }

    public void requestChunk(int x, int z, Player player) {
        Preconditions.checkState(player.getLoaderId() > 0, player.getName() + " has no chunk loader");
        long index = Level.chunkHash(x, z);

        this.getChunkSendQueue(player.getGameVersion()).computeIfAbsent(index, k ->
                new Int2ObjectOpenHashMap<>()).put(player.getLoaderId(), player);
    }

    private void sendChunk(int x, int z, long index, DataPacket packet) {
        for (GameVersion version : chunkSendTasks.keySet()) {
            this.sendChunkInternal(x, z, index, packet, version);
        }
    }

    private void sendChunkInternal(int x, int z, long index, DataPacket packet, GameVersion protocol) {
        LongSet tasks = this.getChunkSendTasks(protocol);
        if (!tasks.contains(index)) {
            return;
        }

        ConcurrentMap<Long, Int2ObjectMap<Player>> queue = this.getChunkSendQueue(protocol);
        for (Player player : queue.get(index).values()) {
            if (player.isConnected() && player.usedChunks.containsKey(index)) {
                player.sendChunk(x, z, packet);
            }
        }
        queue.remove(index);
        tasks.remove(index);
    }

    private void processChunkRequest() {
        // Map shorted by index => requested protocols
        Long2ObjectMap<ObjectSet<GameVersion>> chunkRequests = new Long2ObjectOpenHashMap<>();
        for (GameVersion protocolId : this.chunkSendQueues.keySet()) {
            for (long index : this.getChunkSendQueue(protocolId).keySet()) {
                LongSet tasks = this.getChunkSendTasks(protocolId);
                if (tasks.contains(index)) {
                    continue;
                }
                chunkRequests.computeIfAbsent(index, l -> new ObjectOpenHashSet<>()).add(protocolId);
                tasks.add(index);
            }
        }

        this.chunkRequestInternal(chunkRequests);
    }

    private void chunkRequestInternal(Long2ObjectMap<ObjectSet<GameVersion>> chunkRequests) {
        for (long index : chunkRequests.keySet()) {
            ObjectSet<GameVersion> protocols = new ObjectOpenHashSet<>(chunkRequests.get(index));
            int x = getHashX(index);
            int z = getHashZ(index);

            for (GameVersion protocol : chunkRequests.get(index)) {
                BaseFullChunk chunk = this.getChunk(x, z);
                if (chunk != null) {
                    BatchPacket packet = chunk.getChunkPacket(protocol);
                    if (packet != null) {
                        //this.sendChunk(x, z, index, packet);
                        this.sendChunkInternal(x, z, index, packet, protocol);
                        protocols.remove(protocol);
                    }
                }
            }

            if (protocols.isEmpty()) {
                continue;
            }

            this.requireProvider().requestChunkTask(protocols, x, z);
        }
    }

    public void asyncChunkRequestCallback(GameVersion gameVersion, long timestamp, int x, int z, int subChunkCount, byte[] payload) {
        this.asyncChunkRequestCallbackQueue.add(new NetworkChunkSerializer.NetworkChunkSerializerCallbackData(gameVersion, timestamp, x, z, subChunkCount, payload));
    }

    @Deprecated
    public void chunkRequestCallback(int protocol, long timestamp, int x, int z, int subChunkCount, byte[] payload) {
        this.chunkRequestCallback(GameVersion.byProtocol(protocol, this.getServer().onlyNetEaseMode), timestamp, x, z, subChunkCount, payload);
    }

    public void chunkRequestCallback(GameVersion protocol, long timestamp, int x, int z, int subChunkCount, byte[] payload) {
        long index = Level.chunkHash(x, z);

        if (server.cacheChunks) {
            BatchPacket data = Player.getChunkCacheFromData(protocol, x, z, subChunkCount, payload, this.getDimension());
            BaseFullChunk chunk = getChunkIfLoaded(x, z);
            if (chunk != null && chunk.getChanges() <= timestamp) {
                chunk.setChunkPacket(protocol, data);
            }
            //this.sendChunk(x, z, index, data);
            this.sendChunkInternal(x, z, index, data, protocol);
            return;
        }

        LongSet tasks = this.getChunkSendTasks(protocol);
        if (tasks.contains(index)) {
            ConcurrentMap<Long, Int2ObjectMap<Player>> queue = this.getChunkSendQueue(protocol);

            if (queue.containsKey(index)) {
                for (Player player : queue.get(index).values()) {
                    if (player.isConnected() && player.usedChunks.containsKey(index)) {
                        if (matchMVChunkProtocol(protocol, player.getGameVersion())) {
                            player.sendChunk(x, z, subChunkCount, payload, this.getDimension());
                        }
                    }
                }
            }

            queue.remove(index);
            tasks.remove(index);
        }
    }

    public void removeEntity(Entity entity) {
        if (entity.getLevel() != this) {
            throw new LevelException("Invalid Entity level");
        }

        if (entity instanceof Player) {
            this.players.remove(entity.getId());
            this.checkSleep();
        } else {
            entity.close();
        }

        this.entities.remove(entity.getId());
        this.updateEntities.remove(entity.getId());
    }

    public void addEntity(Entity entity) {
        if (entity.getLevel() != this) {
            throw new LevelException("Invalid Entity level");
        }

        if (entity instanceof Player) {
            this.players.put(entity.getId(), (Player) entity);
        }
        this.entities.put(entity.getId(), entity);
    }

    public void addBlockEntity(BlockEntity blockEntity) {
        if (blockEntity.getLevel() != this) {
            throw new LevelException("Invalid BlockEntity level");
        }
        blockEntities.put(blockEntity.getId(), blockEntity);
    }

    public void scheduleBlockEntityUpdate(BlockEntity entity) {
        Preconditions.checkNotNull(entity, "entity");
        Preconditions.checkArgument(entity.getLevel() == this, "BlockEntity is not in this level");
        if (!updateBlockEntities.contains(entity)) {
            updateBlockEntities.add(entity);
        }
    }

    public void removeBlockEntity(BlockEntity entity) {
        Preconditions.checkNotNull(entity, "entity");
        Preconditions.checkArgument(entity.getLevel() == this, "BlockEntity is not in this level");

        entity.close();

        blockEntities.remove(entity.getId());
        updateBlockEntities.remove(entity);
    }

    public boolean isChunkInUse(int x, int z) {
        return isChunkInUse(Level.chunkHash(x, z));
    }

    public boolean isChunkInUse(long hash) {
        Map<Integer, ChunkLoader> map = this.chunkLoaders.get(hash);
        return map != null && !map.isEmpty();
    }

    public boolean loadChunk(int x, int z) {
        return this.loadChunk(x, z, true);
    }

    public boolean loadChunk(int x, int z, boolean generate) {
        long index = Level.chunkHash(x, z);
        if (this.requireProvider().isChunkLoaded(index)) {
            return true;
        }
        return forceLoadChunk(index, x, z, generate) != null;
    }

    private synchronized BaseFullChunk forceLoadChunk(long index, int x, int z, boolean generate) {
        BaseFullChunk chunk = this.requireProvider().getChunk(x, z, generate);

        if (chunk == null) {
            if (generate) {
                throw new IllegalStateException("Could not create new chunk");
            }
            return null;
        }

        if (chunk.getProvider() != null) {
            this.server.getPluginManager().callEvent(new ChunkLoadEvent(chunk, !chunk.isGenerated()));
        } else {
            this.unloadChunk(x, z, false);
            return chunk;
        }

        chunk.initChunk();

        if (!chunk.isLightPopulated() && chunk.isPopulated() && this.server.lightUpdates) {
            this.server.getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new LightPopulationTask(this, chunk));
        }

        if (this.isChunkInUse(index)) {
            this.unloadQueue.remove(index);
            for (ChunkLoader loader : this.getChunkLoaders(x, z)) {
                loader.onChunkLoaded(chunk);
            }
        } else {
            this.unloadQueue.put(index, (Long) System.currentTimeMillis());
        }
        return chunk;
    }

    private void queueUnloadChunk(int x, int z) {
        long index = Level.chunkHash(x, z);
        this.unloadQueue.put(index, (Long) System.currentTimeMillis());
    }

    public boolean unloadChunkRequest(int x, int z) {
        return this.unloadChunkRequest(x, z, true);
    }

    public boolean unloadChunkRequest(int x, int z, boolean safe) {
        if ((safe && this.isChunkInUse(x, z)) || this.isSpawnChunk(x, z)) {
            return false;
        }

        this.queueUnloadChunk(x, z);

        return true;
    }

    public void cancelUnloadChunkRequest(int x, int z) {
        this.cancelUnloadChunkRequest(Level.chunkHash(x, z));
    }

    public void cancelUnloadChunkRequest(long hash) {
        this.unloadQueue.remove(hash);
    }

    public boolean unloadChunk(int x, int z) {
        return this.unloadChunk(x, z, true);
    }

    public boolean unloadChunk(int x, int z, boolean safe) {
        return this.unloadChunk(x, z, safe, true);
    }

    public synchronized boolean unloadChunk(int x, int z, boolean safe, boolean trySave) {
        if (safe && this.isChunkInUse(x, z)) {
            return false;
        }

        if (!this.isChunkLoaded(x, z)) {
            return true;
        }

        BaseFullChunk chunk = this.getChunk(x, z);

        if (chunk != null && chunk.getProvider() != null) {
            ChunkUnloadEvent ev = new ChunkUnloadEvent(chunk);
            this.server.getPluginManager().callEvent(ev);
            if (ev.isCancelled()) {
                return false;
            }
        }

        try {
            LevelProvider levelProvider = this.requireProvider();
            if (chunk != null) {
                if (trySave && this.autoSave) {
                    int entities = 0;
                    for (Entity e : chunk.getEntities().values()) {
                        if (e instanceof Player) {
                            continue;
                        }
                        ++entities;
                    }

                    if (chunk.hasChanged() || !chunk.getBlockEntities().isEmpty() || entities > 0) {
                        levelProvider.setChunk(x, z, chunk);
                        levelProvider.saveChunk(x, z);
                    }
                }
                for (ChunkLoader loader : this.getChunkLoaders(x, z)) {
                    loader.onChunkUnloaded(chunk);
                }
            }
            levelProvider.unloadChunk(x, z, safe);
        } catch (Exception e) {
            MainLogger logger = this.server.getLogger();
            logger.error(this.server.getLanguage().translateString("nukkit.level.chunkUnloadError", e.toString()));
            logger.logException(e);
        }

        return true;
    }

    public boolean isSpawnChunk(int X, int Z) {
        Vector3 spawn = this.getSpawnLocation();
        return Math.abs(X - (spawn.getFloorX() >> 4)) <= 1 && Math.abs(Z - (spawn.getFloorZ() >> 4)) <= 1;
    }

    public Position getSafeSpawn() {
        return this.getSafeSpawn(null);
    }

    public Position getSafeSpawn(Vector3 spawn) {
        if (spawn == null /*|| spawn.y < 1*/) {
            spawn = this.getSpawnLocation();
        }

        Vector3 pos = new Vector3(spawn.getFloorX(), (int) Math.floor(spawn.y + 0.1), spawn.getFloorZ());
        FullChunk chunk = this.getChunk((int) pos.x >> 4, (int) pos.z >> 4, false);
        int x = (int) pos.x & 0x0f;
        int z = (int) pos.z & 0x0f;
        if (chunk != null && chunk.isGenerated()) {
            int y = NukkitMath.clamp((int) pos.y, this.getMinBlockY() + 1, this.getMaxBlockY() - 1);
            boolean wasAir = chunk.getBlockId(x, y - 1, z) == 0;
            for (; y > 0; --y) {
                int[] b = chunk.getBlockState(x, y, z);
                Block block = Block.get(b[0], b[1]);
                if (this.isFullBlock(block)) {
                    if (wasAir) {
                        y++;
                        break;
                    }
                } else {
                    wasAir = true;
                }
            }

            for (; y >= 0 && y < this.getMaxBlockY(); y++) {
                int[] b = chunk.getBlockState(x, y + 1, z);
                Block block = Block.get(b[0], b[1]);
                if (!this.isFullBlock(block)) {
                    b = chunk.getBlockState(x, y, z);
                    block = Block.get(b[0], b[1]);
                    if (!this.isFullBlock(block)) {
                        return new Position(pos.x + 0.5, pos.y + 0.1, pos.z + 0.5, this);
                    }
                } else {
                    ++y;
                }
            }

            pos.y = y;
        }

        return new Position(pos.x + 0.5, pos.y + 0.1, pos.z + 0.5, this);
    }

    public int getTime() {
        return time;
    }

    public boolean isDaytime() {
        return this.skyLightSubtracted < 4;
    }

    public long getCurrentTick() {
        return this.levelCurrentTick;
    }

    public String getName() {
        return this.requireProvider().getName();
    }

    public String getFolderName() {
        return this.folderName;
    }

    public void setTime(int time) {
        this.time = time;
        this.sendTime();
    }

    public void stopTime() {
        this.stopTime = true;
        this.sendTime();
    }

    public void startTime() {
        this.stopTime = false;
        this.sendTime();
    }

    @Override
    public long getSeed() {
        return this.requireProvider().getSeed();
    }

    public void setSeed(int seed) {
        this.requireProvider().setSeed(seed);
    }

    public boolean populateChunk(int x, int z) {
        return this.populateChunk(x, z, false);
    }

    public boolean populateChunk(int x, int z, boolean force) {
        long index = Level.chunkHash(x, z);
        if (this.chunkPopulationQueue.containsKey(index) || this.chunkPopulationQueue.size() >= this.chunkPopulationQueueSize && !force) {
            return false;
        }

        BaseFullChunk chunk = this.getChunk(x, z, true);
        boolean populate;
        if (!chunk.isPopulated()) {
            populate = true;
            for (int xx = -1; xx <= 1; ++xx) {
                for (int zz = -1; zz <= 1; ++zz) {
                    if (this.chunkPopulationLock.containsKey(Level.chunkHash(x + xx, z + zz))) {
                        populate = false;
                        break;
                    }
                }
            }

            if (populate) {
                if (!this.chunkPopulationQueue.containsKey(index)) {
                    this.chunkPopulationQueue.put(index, Boolean.TRUE);
                    for (int xx = -1; xx <= 1; ++xx) {
                        for (int zz = -1; zz <= 1; ++zz) {
                            this.chunkPopulationLock.put(Level.chunkHash(x + xx, z + zz), Boolean.TRUE);
                        }
                    }

                    this.server.getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, new PopulationTask(this, chunk));
                }
            }
            return false;
        }

        return true;
    }

    public void generateChunk(int x, int z) {
        this.generateChunk(x, z, false);
    }

    public void generateChunk(int x, int z, boolean force) {
        if (this.chunkGenerationQueue.size() >= this.chunkGenerationQueueSize && !force) {
            return;
        }

        long index = Level.chunkHash(x, z);
        if (!this.chunkGenerationQueue.containsKey(index)) {
            this.chunkGenerationQueue.put(index, Boolean.TRUE);
            GenerationTask task = new GenerationTask(this, this.getChunk(x, z, true));
            this.server.getScheduler().scheduleAsyncTask(InternalPlugin.INSTANCE, task);
        }
    }

    public void regenerateChunk(int x, int z) {
        this.unloadChunk(x, z, false, false);
        this.cancelUnloadChunkRequest(x, z);
        LevelProvider levelProvider = requireProvider();
        levelProvider.setChunk(x, z, levelProvider.getEmptyChunk(x, z));
        this.generateChunk(x, z, true);
    }

    public void doChunkGarbageCollection() {
        // Remove all invalid block entities
        if (!blockEntities.isEmpty()) {
            Iterator<BlockEntity> iter = blockEntities.values().iterator();
            while (iter.hasNext()) {
                BlockEntity blockEntity = iter.next();
                if (blockEntity != null) {
                    if (!blockEntity.isValid()) {
                        iter.remove();
                        blockEntity.close();
                    }
                } else {
                    iter.remove();
                }
            }
        }

        LevelProvider levelProvider = this.requireProvider();
        for (Map.Entry<Long, ? extends FullChunk> entry : levelProvider.getLoadedChunks().entrySet()) {
            long index = entry.getKey();
            if (!this.unloadQueue.containsKey(index)) {
                FullChunk chunk = entry.getValue();
                int X = chunk.getX();
                int Z = chunk.getZ();
                if (!this.isSpawnChunk(X, Z)) {
                    this.unloadChunkRequest(X, Z, true);
                }
            }
        }

        levelProvider.doGarbageCollection();
    }


    public void doGarbageCollection(long allocatedTime) {
        long start = System.currentTimeMillis();
        if (unloadChunks(start, allocatedTime, false)) {
            allocatedTime -= (System.currentTimeMillis() - start);
            provider.doGarbageCollection(allocatedTime);
        }
    }

    public void unloadChunks() {
        this.unloadChunks(false);
    }

    public void unloadChunks(boolean force) {
        this.unloadChunks(50, force);
    }

    public void unloadChunks(int maxUnload, boolean force) {
        if (server.holdWorldSave && !force && this.saveOnUnloadEnabled) {
            return;
        }

        if (!this.unloadQueue.isEmpty()) {
            long now = System.currentTimeMillis();

            int unloaded = 0;
            LongList toRemove = null;
            for (var entry : unloadQueue.fastEntrySet()) {
                long index = entry.getLongKey();

                if (isChunkInUse(index)) {
                    continue;
                }

                if (!force) {
                    long time = entry.getValue();
                    if (unloaded > maxUnload) {
                        break;
                    } else if (time > (now - 20000)) {
                        continue;
                    }
                    unloaded++;
                }

                if (toRemove == null) toRemove = new LongArrayList();
                toRemove.add(index);
            }

            if (toRemove != null) {
                int size = toRemove.size();
                for (int i = 0; i < size; i++) {
                    long index = toRemove.getLong(i);
                    int X = getHashX(index);
                    int Z = getHashZ(index);

                    if (this.unloadChunk(X, Z, true)) {
                        this.unloadQueue.remove(index);
                    }
                }
            }
        }
    }

    /**
     * @param now           current time
     * @param allocatedTime allocated time
     * @param force         force
     * @return true if there is allocated time remaining
     */
    private boolean unloadChunks(long now, long allocatedTime, boolean force) {
        if (server.holdWorldSave && !force && this.saveOnUnloadEnabled) {
            return false;
        }

        if (!this.unloadQueue.isEmpty()) {
            boolean result = true;
            int maxIterations = this.unloadQueue.size();

            if (lastUsingUnloadingIter == null) {
                lastUsingUnloadingIter = this.unloadQueue.fastEntrySet().iterator();
            }

            var iter = lastUsingUnloadingIter;

            LongList toUnload = null;

            for (int i = 0; i < maxIterations; i++) {
                if (!iter.hasNext()) {
                    iter = this.unloadQueue.fastEntrySet().iterator();
                }
                var entry = iter.next();

                long index = entry.getLongKey();

                if (isChunkInUse(index)) {
                    continue;
                }

                if (!force) {
                    long time = entry.getValue();
                    if (time > (now - 20000)) {
                        continue;
                    }
                }

                if (toUnload == null) {
                    toUnload = new LongArrayList();
                }
                toUnload.add(index);
            }

            if (toUnload != null) {
                for (long index : toUnload) {
                    int X = getHashX(index);
                    int Z = getHashZ(index);
                    if (this.unloadChunk(X, Z, true)) {
                        this.unloadQueue.remove(index);
                        if (System.currentTimeMillis() - now >= allocatedTime) {
                            result = false;
                            break;
                        }
                    }
                }
            }
            return result;
        } else {
            return true;
        }
    }

    @Override
    public void setMetadata(String metadataKey, MetadataValue newMetadataValue) throws Exception {
        this.server.getLevelMetadata().setMetadata(this, metadataKey, newMetadataValue);
    }

    @Override
    public List<MetadataValue> getMetadata(String metadataKey) throws Exception {
        return this.server.getLevelMetadata().getMetadata(this, metadataKey);
    }

    @Override
    public boolean hasMetadata(String metadataKey) throws Exception {
        return this.server.getLevelMetadata().hasMetadata(this, metadataKey);
    }

    @Override
    public void removeMetadata(String metadataKey, Plugin owningPlugin) throws Exception {
        this.server.getLevelMetadata().removeMetadata(this, metadataKey, owningPlugin);
    }

    @SuppressWarnings("unused")
    public void addPlayerMovement(Entity entity, double x, double y, double z, double yaw, double pitch, double headYaw) {
        MovePlayerPacket pk = new MovePlayerPacket();
        pk.eid = entity.getId();
        pk.x = (float) x;
        pk.y = (float) y;
        pk.z = (float) z;
        pk.yaw = (float) yaw;
        pk.headYaw = (float) headYaw;
        pk.pitch = (float) pitch;
        pk.onGround = entity.onGround;

        if (entity.riding != null) {
            pk.ridingEid = entity.riding.getId();
            pk.mode = MovePlayerPacket.MODE_PITCH;
        }

        Server.broadcastPacket(entity.getViewers().values(), pk);
    }

    public void addEntityMovement(Entity entity, double x, double y, double z, double yaw, double pitch, double headYaw) {
        MoveEntityAbsolutePacket pk = new MoveEntityAbsolutePacket();
        pk.eid = entity.getId();
        pk.x = x;
        pk.y = y;
        pk.z = z;
        pk.yaw = yaw;
        pk.headYaw = headYaw;
        pk.pitch = pitch;
        pk.onGround = entity.onGround;

        entity.getViewers().values().stream().filter(p -> p.protocol < ProtocolInfo.v1_16_100).forEach(p -> p.dataPacket(pk));

        MoveEntityDeltaPacket pk2 = new MoveEntityDeltaPacket();
        pk2.eid = entity.getId();
        if (entity.lastX != x) {
            pk2.x = (float) x;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_X;
        }
        if (entity.lastY != y) {
            pk2.y = (float) y;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_Y;
        }
        if (entity.lastZ != z) {
            pk2.z = (float) z;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_Z;
        }
        if (entity.lastPitch != pitch) {
            pk2.pitchDelta = (float) pitch;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_PITCH;
        }
        if (entity.lastYaw != yaw) {
            pk2.yawDelta = (float) yaw;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_YAW;
        }
        if (entity.lastHeadYaw != headYaw) {
            pk2.headYawDelta = (float) headYaw;
            pk2.flags |= MoveEntityDeltaPacket.FLAG_HAS_HEAD_YAW;
        }
        if (entity.onGround) {
            pk2.flags |= MoveEntityDeltaPacket.FLAG_ON_GROUND;
        }

        entity.getViewers().values().stream().filter(p -> p.protocol >= ProtocolInfo.v1_16_100).forEach(p -> p.dataPacket(pk2));
    }

    public boolean isRaining() {
        return this.raining;
    }

    public boolean setRaining(boolean raining) {
        return this.setRaining(raining, ThreadLocalRandom.current().nextInt(50000) + 10000);
    }

    public boolean setRaining(boolean raining, int intensity) {
        WeatherChangeEvent ev = new WeatherChangeEvent(this, raining, intensity);
        this.server.getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return false;
        }

        this.raining = raining;
        this.rainingIntensity = ev.getIntensity();

        LevelEventPacket pk = new LevelEventPacket();
        // These numbers are from Minecraft

        if (raining) {
            pk.evid = LevelEventPacket.EVENT_START_RAIN;
            pk.data = this.rainingIntensity;
            setRainTime(Utils.random.nextInt(12000) + 12000);
        } else {
            pk.evid = LevelEventPacket.EVENT_STOP_RAIN;
            setRainTime(Utils.random.nextInt(168000) + 12000);
        }

        Server.broadcastPacket(this.getPlayers().values(), pk);

        return true;
    }

    public int getRainingIntensity() {
        return rainingIntensity;
    }

    public int getRainTime() {
        return this.rainTime;
    }

    public void setRainTime(int rainTime) {
        this.rainTime = rainTime;
    }

    public boolean isThundering() {
        return raining && this.thundering;
    }

    public boolean setThundering(boolean thundering) {
        ThunderChangeEvent ev = new ThunderChangeEvent(this, thundering);
        this.server.getPluginManager().callEvent(ev);

        if (ev.isCancelled()) {
            return false;
        }

        if (thundering && !raining) {
            setRaining(true);
        }

        this.thundering = thundering;

        LevelEventPacket pk = new LevelEventPacket();
        // These numbers are from Minecraft
        if (thundering) {
            pk.evid = LevelEventPacket.EVENT_START_THUNDER;
            int time = Utils.random.nextInt(12000) + 3600;
            pk.data = time;
            setThunderTime(time);
        } else {
            pk.evid = LevelEventPacket.EVENT_STOP_THUNDER;
            setThunderTime(Utils.random.nextInt(168000) + 12000);
        }

        Server.broadcastPacket(this.getPlayers().values(), pk);

        return true;
    }

    public int getThunderTime() {
        return this.thunderTime;
    }

    public void setThunderTime(int thunderTime) {
        this.thunderTime = thunderTime;
    }

    public void sendWeather(Player[] players) {
        if (players == null) {
            players = this.getPlayers().values().toArray(Player.EMPTY_ARRAY);
        }

        LevelEventPacket pk = new LevelEventPacket();

        if (this.raining) {
            pk.evid = LevelEventPacket.EVENT_START_RAIN;
            pk.data = this.rainingIntensity;
        } else {
            pk.evid = LevelEventPacket.EVENT_STOP_RAIN;
        }

        Server.broadcastPacket(players, pk);

        if (this.isThundering()) {
            pk.evid = LevelEventPacket.EVENT_START_THUNDER;
            pk.data = this.thunderTime;
        } else {
            pk.evid = LevelEventPacket.EVENT_STOP_THUNDER;
        }

        Server.broadcastPacket(players, pk);
    }

    public void sendWeather(Player player) {
        if (player != null) {
            this.sendWeather(new Player[]{player});
        }
    }

    public void sendWeather(Collection<Player> players) {
        if (players == null) {
            players = this.getPlayers().values();
        }
        this.sendWeather(players.toArray(Player.EMPTY_ARRAY));
    }

    public void setDimensionData(DimensionData data) {
        this.dimensionData = data;
    }

    public DimensionData getDimensionData() {
        return this.dimensionData;
    }

    public int getDimension() {
        return this.dimensionData.getDimensionId();
    }

    public final boolean isOverWorld() {
        return this.getDimension() == DIMENSION_OVERWORLD;
    }

    public final boolean isNether() {
        return this.getDimension() == DIMENSION_NETHER;
    }

    public final boolean isTheEnd() {
        return this.getDimension() == DIMENSION_THE_END;
    }

    public final boolean isYInRange(int y) {
        return y >= getMinBlockY() && y <= getMaxBlockY();
    }

    public final boolean isYInRange(double y) {
        return y >= getMinBlockY() && y <= getMaxBlockY();
    }

    @Override
    public int getMinBlockY() {
        return this.requireProvider().getMinBlockY();
    }

    @Override
    public int getMaxBlockY() {
        return this.requireProvider().getMaxBlockY();
    }

    private int ensureY(final int y) {
        return Math.max(Math.min(y, this.getMaxBlockY()), this.getMinBlockY());
    }

    public boolean canBlockSeeSky(Vector3 pos) {
        return this.getHighestBlockAt(pos.getFloorX(), pos.getFloorZ()) < pos.getY();
    }

    public boolean canBlockSeeSky(Block block) {
        return this.getHighestBlockAt((int) block.getX(), (int) block.getZ()) < block.getY();
    }

    public int getStrongPower(Vector3 pos, BlockFace direction) {
        return this.getBlock(pos).getStrongPower(direction);
    }

    public int getStrongPower(Vector3 pos) {
        int i = 0;

        for (BlockFace face : BlockFace.values()) {
            i = Math.max(i, this.getStrongPower(pos.getSideVec(face), face));

            if (i >= 15) {
                return i;
            }
        }

        return i;
    }

    public boolean isSidePowered(Vector3 pos, BlockFace face) {
        return this.getRedstonePower(pos, face) > 0;
    }

    public int getRedstonePower(Vector3 pos, BlockFace face) {
        Block block = this.getBlock(pos);
        return block.isNormalBlock() ? this.getStrongPower(pos) : block.getWeakPower(face);
    }

    public boolean isBlockPowered(Vector3 pos) {
        for (BlockFace face : BlockFace.values()) {
            if (this.getRedstonePower(pos.getSideVec(face), face) > 0) {
                return true;
            }
        }
        return false;
    }

    public int isBlockIndirectlyGettingPowered(Vector3 pos) {
        int power = 0;

        for (BlockFace face : BlockFace.values()) {
            int blockPower = this.getRedstonePower(pos.getSideVec(face), face);

            if (blockPower >= 15) {
                return 15;
            }

            if (blockPower > power) {
                power = blockPower;
            }
        }

        return power;
    }

    public boolean isAreaLoaded(AxisAlignedBB bb) {
        if (bb.getMaxY() < this.getMinBlockY() || bb.getMinY() >= this.getMaxBlockY()) {
            return false;
        }
        int minX = NukkitMath.floorDouble(bb.getMinX()) >> 4;
        int minZ = NukkitMath.floorDouble(bb.getMinZ()) >> 4;
        int maxX = NukkitMath.floorDouble(bb.getMaxX()) >> 4;
        int maxZ = NukkitMath.floorDouble(bb.getMaxZ()) >> 4;

        for (int x = minX; x <= maxX; ++x) {
            for (int z = minZ; z <= maxZ; ++z) {
                if (!this.isChunkLoaded(x, z)) {
                    return false;
                }
            }
        }

        return true;
    }

    public void addLevelEvent(Vector3 pos, int event) {
        this.addLevelEvent(pos, event, 0);
    }

    public void addLevelEvent(Vector3 pos, int event, int data) {
        LevelEventPacket pk = new LevelEventPacket();
        pk.evid = event;
        pk.x = (float) pos.x;
        pk.y = (float) pos.y;
        pk.z = (float) pos.z;
        pk.data = data;

        addChunkPacket(pos.getFloorX() >> 4, pos.getFloorZ() >> 4, pk);
    }

    private int getUpdateLCG() {
        return (this.updateLCG = (this.updateLCG * 3) ^ LCG_CONSTANT);
    }

    public boolean randomTickingEnabled() {
        return this.randomTickingEnabled;
    }

    public boolean isAnimalSpawningAllowedByTime() {
        int time = this.getTime() % TIME_FULL;
        return time < 13184 || time > 22800;
    }

    public boolean isMobSpawningAllowedByTime() {
        int time = this.getTime() % TIME_FULL;
        return time > 13184 && time < 22800;
    }

    public boolean shouldMobBurn(BaseEntity entity) {
        int time = this.getTime() % TIME_FULL;
        return !entity.isOnFire() && !this.raining && !entity.isBaby() && (time < 12567 || time > 23450) && !entity.isInsideOfWater() && entity.canSeeSky();
    }

    public boolean isMobSpawningAllowed() {
        return !Server.disabledSpawnWorlds.contains(getName()) && gameRules.getBoolean(GameRule.DO_MOB_SPAWNING);
    }

    public boolean antiXrayEnabled() {
        return this.antiXray;
    }

    public boolean createPortal(Block target, boolean fireCharge) {
        if (this.getDimension() == DIMENSION_THE_END) return false;
        final int maxPortalSize = 23;
        final int targX = target.getFloorX();
        final int targY = target.getFloorY();
        final int targZ = target.getFloorZ();
        //check if there's air above (at least 3 blocks)
        for (int i = 1; i < 4; i++) {
            if (this.getBlockIdAt(targX, targY + i, targZ) != BlockID.AIR) {
                return false;
            }
        }
        int sizePosX = 0;
        int sizeNegX = 0;
        int sizePosZ = 0;
        int sizeNegZ = 0;
        for (int i = 1; i < maxPortalSize; i++) {
            if (this.getBlockIdAt(targX + i, targY, targZ) == BlockID.OBSIDIAN) {
                sizePosX++;
            } else {
                break;
            }
        }
        for (int i = 1; i < maxPortalSize; i++) {
            if (this.getBlockIdAt(targX - i, targY, targZ) == BlockID.OBSIDIAN) {
                sizeNegX++;
            } else {
                break;
            }
        }
        for (int i = 1; i < maxPortalSize; i++) {
            if (this.getBlockIdAt(targX, targY, targZ + i) == BlockID.OBSIDIAN) {
                sizePosZ++;
            } else {
                break;
            }
        }
        for (int i = 1; i < maxPortalSize; i++) {
            if (this.getBlockIdAt(targX, targY, targZ - i) == BlockID.OBSIDIAN) {
                sizeNegZ++;
            } else {
                break;
            }
        }
        //plus one for target block
        int sizeX = sizePosX + sizeNegX + 1;
        int sizeZ = sizePosZ + sizeNegZ + 1;
        if (sizeX >= 2 && sizeX <= maxPortalSize) {
            //start scan from 1 block above base
            //find pillar or end of portal to start scan
            int scanX = targX;
            int scanY = targY + 1;
            for (int i = 0; i < sizePosX + 1; i++) {
                //this must be air
                if (this.getBlockIdAt(scanX + i, scanY, targZ) != BlockID.AIR) {
                    return false;
                }
                if (this.getBlockIdAt(scanX + i + 1, scanY, targZ) == BlockID.OBSIDIAN) {
                    scanX += i;
                    break;
                }
            }
            //make sure that the above loop finished
            if (this.getBlockIdAt(scanX + 1, scanY, targZ) != BlockID.OBSIDIAN) {
                return false;
            }

            int innerWidth = 0;
            LOOP:
            for (int i = 0; i < 21; i++) {
                int id = this.getBlockIdAt(scanX - i, scanY, targZ);
                switch (id) {
                    case BlockID.AIR:
                        innerWidth++;
                        break;
                    case BlockID.OBSIDIAN:
                        break LOOP;
                    default:
                        return false;
                }
            }
            int innerHeight = 0;
            LOOP:
            for (int i = 0; i < 21; i++) {
                int id = this.getBlockIdAt(scanX, scanY + i, targZ);
                switch (id) {
                    case BlockID.AIR:
                        innerHeight++;
                        break;
                    case BlockID.OBSIDIAN:
                        break LOOP;
                    default:
                        return false;
                }
            }
            if (!(innerWidth <= 21
                    && innerWidth >= 2
                    && innerHeight <= 21
                    && innerHeight >= 3)) {
                return false;
            }

            for (int height = 0; height < innerHeight + 1; height++) {
                if (height == innerHeight) {
                    for (int width = 0; width < innerWidth; width++) {
                        if (this.getBlockIdAt(scanX - width, scanY + height, targZ) != BlockID.OBSIDIAN) {
                            return false;
                        }
                    }
                } else {
                    if (this.getBlockIdAt(scanX + 1, scanY + height, targZ) != BlockID.OBSIDIAN
                            || this.getBlockIdAt(scanX - innerWidth, scanY + height, targZ) != BlockID.OBSIDIAN) {
                        return false;
                    }

                    for (int width = 0; width < innerWidth; width++) {
                        if (this.getBlockIdAt(scanX - width, scanY + height, targZ) != BlockID.AIR) {
                            return false;
                        }
                    }
                }
            }

            for (int height = 0; height < innerHeight; height++) {
                for (int width = 0; width < innerWidth; width++) {
                    this.setBlock(new Vector3(scanX - width, scanY + height, targZ), Block.get(BlockID.NETHER_PORTAL));
                }
            }

            if (fireCharge) {
                this.addSoundToViewers(target, cn.nukkit.level.Sound.MOB_GHAST_FIREBALL);
            } else {
                this.addLevelSoundEvent(target, LevelSoundEventPacket.SOUND_IGNITE);
            }
            return true;
        } else if (sizeZ >= 2 && sizeZ <= maxPortalSize) {
            //start scan from 1 block above base
            //find pillar or end of portal to start scan
            int scanY = targY + 1;
            int scanZ = targZ;
            for (int i = 0; i < sizePosZ + 1; i++) {
                //this must be air
                if (this.getBlockIdAt(targX, scanY, scanZ + i) != BlockID.AIR) {
                    return false;
                }
                if (this.getBlockIdAt(targX, scanY, scanZ + i + 1) == BlockID.OBSIDIAN) {
                    scanZ += i;
                    break;
                }
            }
            //make sure that the above loop finished
            if (this.getBlockIdAt(targX, scanY, scanZ + 1) != BlockID.OBSIDIAN) {
                return false;
            }

            int innerWidth = 0;
            LOOP:
            for (int i = 0; i < 21; i++) {
                int id = this.getBlockIdAt(targX, scanY, scanZ - i);
                switch (id) {
                    case BlockID.AIR:
                        innerWidth++;
                        break;
                    case BlockID.OBSIDIAN:
                        break LOOP;
                    default:
                        return false;
                }
            }
            int innerHeight = 0;
            LOOP:
            for (int i = 0; i < 21; i++) {
                int id = this.getBlockIdAt(targX, scanY + i, scanZ);
                switch (id) {
                    case BlockID.AIR:
                        innerHeight++;
                        break;
                    case BlockID.OBSIDIAN:
                        break LOOP;
                    default:
                        return false;
                }
            }
            if (!(innerWidth <= 21
                    && innerWidth >= 2
                    && innerHeight <= 21
                    && innerHeight >= 3)) {
                return false;
            }

            for (int height = 0; height < innerHeight + 1; height++) {
                if (height == innerHeight) {
                    for (int width = 0; width < innerWidth; width++) {
                        if (this.getBlockIdAt(targX, scanY + height, scanZ - width) != BlockID.OBSIDIAN) {
                            return false;
                        }
                    }
                } else {
                    if (this.getBlockIdAt(targX, scanY + height, scanZ + 1) != BlockID.OBSIDIAN
                            || this.getBlockIdAt(targX, scanY + height, scanZ - innerWidth) != BlockID.OBSIDIAN) {
                        return false;
                    }

                    for (int width = 0; width < innerWidth; width++) {
                        if (this.getBlockIdAt(targX, scanY + height, scanZ - width) != BlockID.AIR) {
                            return false;
                        }
                    }
                }
            }

            for (int height = 0; height < innerHeight; height++) {
                for (int width = 0; width < innerWidth; width++) {
                    this.setBlock(new Vector3(targX, scanY + height, scanZ - width), Block.get(BlockID.NETHER_PORTAL));
                }
            }

            if (fireCharge) {
                this.addSoundToViewers(target, cn.nukkit.level.Sound.MOB_GHAST_FIREBALL);
            } else {
                this.addLevelSoundEvent(target, LevelSoundEventPacket.SOUND_IGNITE);
            }
            return true;
        }

        return false;
    }

    public Position calculatePortalMirror(Vector3 portal) {
        Level nether = Server.getInstance().getNetherWorld(this.getName());
        if (nether == null) {
            return null;
        }

        double x;
        double y;
        double z;
        if (this == nether) {
            x = portal.getFloorX() << 3;
            y = NukkitMath.clamp(portal.getFloorY(), 70, 246);
            z = portal.getFloorZ() << 3;
        } else {
            x = portal.getFloorX() >> 3;
            y = NukkitMath.clamp(portal.getFloorY(), 70, 118);
            z = portal.getFloorZ() >> 3;
        }
        return new Position(x, y, z, this == nether ? Server.getInstance().getDefaultLevel() : nether);
    }

    public boolean isBlockWaterloggedAt(FullChunk chunk, int x, int y, int z) {
        if (chunk == null || y < this.getMinBlockY() || y > this.getMaxBlockY()) {
            return false;
        }
        int block = chunk.getBlockId(x & 0x0f, y, z & 0x0f, BlockLayer.WATERLOGGED.ordinal());
        return Block.isWater(block);
    }

    public boolean isRayCollidingWithBlocks(double srcX, double srcY, double srcZ, double dstX, double dstY, double dstZ, double stepSize) {
        Vector3 direction = new Vector3(dstX - srcX, dstY - srcY, dstZ - srcZ);
        double length = direction.length();
        Vector3 normalizedDirection = direction.divide(length);

        for (double t = 0.0; t < length; t += stepSize) {
            int x = (int) Math.round(srcX + normalizedDirection.x * t);
            int y = (int) Math.round(srcY + normalizedDirection.y * t);
            int z = (int) Math.round(srcZ + normalizedDirection.z * t);

            Block block = getBlock(x, y, z);
            if (block != null && block.getCollisionBoundingBox() != null) {
                AxisAlignedBB bb = block.getCollisionBoundingBox();
                if (bb.isVectorInside(x, y, z)) {
                    return true;
                }
            }
        }

        return false; // No collision with any blocks
    }

    public float getBlockDensity(Vector3 source, AxisAlignedBB boundingBox) {
        double xInterval = 1 / ((boundingBox.getMaxX() - boundingBox.getMinX()) * 2 + 1);
        double yInterval = 1 / ((boundingBox.getMaxY() - boundingBox.getMinY()) * 2 + 1);
        double zInterval = 1 / ((boundingBox.getMaxZ() - boundingBox.getMinZ()) * 2 + 1);
        double xOffset = (1 - Math.floor(1 / xInterval) * xInterval) / 2;
        double zOffset = (1 - Math.floor(1 / zInterval) * zInterval) / 2;

        if (xInterval >= 0 && yInterval >= 0 && zInterval >= 0) {
            int visibleBlocks = 0;
            int totalBlocks = 0;

            for (float x = 0; x <= 1; x = (float) ((double) x + xInterval)) {
                for (float y = 0; y <= 1; y = (float) ((double) y + yInterval)) {
                    for (float z = 0; z <= 1; z = (float) ((double) z + zInterval)) {
                        double blockX = boundingBox.getMinX() + (boundingBox.getMaxX() - boundingBox.getMinX()) * (double) x;
                        double blockY = boundingBox.getMinY() + (boundingBox.getMaxY() - boundingBox.getMinY()) * (double) y;
                        double blockZ = boundingBox.getMinZ() + (boundingBox.getMaxZ() - boundingBox.getMinZ()) * (double) z;

                        if (this.isRayCollidingWithBlocks(source.x, source.y, source.z, blockX + xOffset, blockY, blockZ + zOffset, 0.3)) {
                            visibleBlocks++;
                        }

                        totalBlocks++;
                    }
                }
            }

            return (float) visibleBlocks / (float) totalBlocks;
        } else {
            return 0;
        }
    }

    /**
     * 添加方块设置回调，当世界中有方块被更改时，会触发回调
     *
     * @param consumer 回调
     * @return 回调id
     */
    public int addCallbackBlockSet(Consumer<Block> consumer) {
        int id = callbackIdCounter.incrementAndGet();
        callbackBlockSet.put(id, consumer);
        return id;
    }

    public void removeCallbackBlockSet(int id) {
        callbackBlockSet.remove(id);
    }

    /**
     * 添加区块数据包发送回调，当世界中有区块数据包被发送时，会触发回调
     *
     * @param consumer 回调
     * @return 回调id
     */
    public int addCallbackChunkPacketSend(BiConsumer<Long, DataPacket> consumer) {
        int id = callbackIdCounter.incrementAndGet();
        callbackChunkPacketSend.put(id, consumer);
        return id;
    }

    public void removeCallbackChunkPacketSend(int id) {
        callbackChunkPacketSend.remove(id);
    }

    public PersistentDataContainer getPersistentDataContainer(Vector3 position) {
        return this.getPersistentDataContainer(position, false);
    }

    public PersistentDataContainer getPersistentDataContainer(Vector3 position, boolean create) {
        BlockEntity blockEntity = this.getBlockEntity(position);
        if (blockEntity != null) {
            return blockEntity.getPersistentDataContainer();
        }

        if (create) {
            CompoundTag compound = BlockEntity.getDefaultCompound(position, BlockEntity.PERSISTENT_CONTAINER);
            blockEntity = BlockEntity.createBlockEntity(BlockEntity.PERSISTENT_CONTAINER, this.getChunk(position.getChunkX(), position.getChunkZ()), compound);

            if (blockEntity == null) {
                throw new IllegalStateException("Failed to create persistent container block entity at " + position);
            }
            return blockEntity.getPersistentDataContainer();
        }

        return new DelegatePersistentDataContainer() {
            @Override
            protected PersistentDataContainer createDelegate() {
                return getPersistentDataContainer(position, true);
            }
        };
    }

    public boolean hasPersistentDataContainer(Vector3 position) {
        BlockEntity blockEntity = this.getBlockEntity(position);
        return blockEntity != null && blockEntity.hasPersistentDataContainer();
    }

    private ConcurrentMap<Long, Int2ObjectMap<Player>> getChunkSendQueue(GameVersion protocol) {
        GameVersion protocolId = this.getChunkProtocol(protocol);
        return this.chunkSendQueues.computeIfAbsent(protocolId, i -> new ConcurrentHashMap<>());
    }

    private LongSet getChunkSendTasks(GameVersion protocol) {
        GameVersion protocolId = this.getChunkProtocol(protocol);
        return this.chunkSendTasks.computeIfAbsent(protocolId, i -> new LongOpenHashSet());
    }

    private GameVersion getChunkProtocol(GameVersion version) {
        int protocol = version.getProtocol();
        if (version.isNetEase()) {
            if (protocol >= ProtocolInfo.v1_21_2) {
                return GameVersion.V1_21_2_NETEASE;
            }
            return GameVersion.V1_20_50_NETEASE;
        }
        if (protocol >= GameVersion.V1_21_110_26.getProtocol()) {
            return GameVersion.V1_21_110;
        } else if (protocol >= GameVersion.V1_21_100.getProtocol()) {
            return GameVersion.V1_21_100;
        } else if (protocol >= ProtocolInfo.v1_21_90) {
            return GameVersion.V1_21_90;
        } else if (protocol >= ProtocolInfo.v1_21_80) {
            return GameVersion.V1_21_80;
        } else if (protocol >= ProtocolInfo.v1_21_70_24) {
            return GameVersion.V1_21_70;
        } else if (protocol >= ProtocolInfo.v1_21_60) {
            return GameVersion.V1_21_60;
        } else if (protocol >= ProtocolInfo.v1_21_50_26) {
            return GameVersion.V1_21_50;
        } else if (protocol >= ProtocolInfo.v1_21_40) {
            return GameVersion.V1_21_40;
        } else if (protocol >= ProtocolInfo.v1_21_30) {
            return GameVersion.V1_21_30;
        } else if (protocol >= ProtocolInfo.v1_21_20) {
            return GameVersion.V1_21_20;
        } else if (protocol >= ProtocolInfo.v1_21_0) {
            return GameVersion.V1_21_0;
        } else if (protocol >= ProtocolInfo.v1_20_80) {
            return GameVersion.V1_20_80;
        } else if (protocol >= ProtocolInfo.v1_20_70) {
            return GameVersion.V1_20_70;
        } else if (protocol >= ProtocolInfo.v1_20_60) {
            return GameVersion.V1_20_60;
        } else if (protocol >= ProtocolInfo.v1_20_50) {
            return GameVersion.V1_20_50;
        } else if (protocol >= ProtocolInfo.v1_20_40) {
            return GameVersion.V1_20_40;
        } else if (protocol >= ProtocolInfo.v1_20_30_24) {
            return GameVersion.V1_20_30;
        } else if (protocol >= ProtocolInfo.v1_20_10_21) {
            return GameVersion.V1_20_10;
        } else if (protocol >= ProtocolInfo.v1_20_0_23) {
            return GameVersion.V1_20_0;
        } else if (protocol >= ProtocolInfo.v1_19_80) { //调色板 物品运行时id
            return GameVersion.V1_19_80;
        } else if (protocol >= ProtocolInfo.v1_19_70_24) { //调色板 物品运行时id
            return GameVersion.V1_19_70;
        } else if (protocol >= ProtocolInfo.v1_19_60) { //调色板 物品运行时id
            return GameVersion.V1_19_60;
        } else if (protocol >= ProtocolInfo.v1_19_50_20) { //调色板 物品运行时id
            return GameVersion.V1_19_50;
        } else if (protocol >= ProtocolInfo.v1_19_20) { //调色板 物品运行时id
            return GameVersion.V1_19_20;
        } else if (protocol >= ProtocolInfo.v1_19_0_29) { //调色板 物品运行时id
            return GameVersion.V1_19_0;
        } else if (protocol >= ProtocolInfo.v1_18_30) { //调色板 物品运行时id
            return GameVersion.V1_18_30;
        } else if (protocol >= ProtocolInfo.v1_18_10_26) { //调色板修改
            return GameVersion.V1_18_10;
        } else if (protocol >= ProtocolInfo.v1_18_0) { //世界高度改变
            return GameVersion.V1_18_0;
        } else if (protocol >= ProtocolInfo.v1_17_40) {
            return GameVersion.V1_17_40;
        } else if (protocol >= ProtocolInfo.v1_17_30) {
            return GameVersion.V1_17_30;
        } else if (protocol >= ProtocolInfo.v1_17_10) {
            return GameVersion.V1_17_10;
        } else if (protocol >= ProtocolInfo.v1_17_0) {
            return GameVersion.V1_17_0;
        } else if (protocol >= ProtocolInfo.v1_16_210) {
            return GameVersion.V1_16_210;
        } else if (protocol >= ProtocolInfo.v1_16_100) {
            return GameVersion.V1_16_100;
        } else if (protocol >= ProtocolInfo.v1_16_0 && protocol <= ProtocolInfo.v1_16_100_52) {
            return GameVersion.V1_16_0;
        } else if (protocol == ProtocolInfo.v1_14_0 || protocol == ProtocolInfo.v1_14_60) {
            return GameVersion.V1_14_0;
        } else if (protocol == ProtocolInfo.v1_13_0) {
            return GameVersion.V1_13_0;
        } else if (protocol == ProtocolInfo.v1_12_0) {
            return GameVersion.V1_12_0;
        } else if (protocol >= ProtocolInfo.v1_2_0 && protocol < ProtocolInfo.v1_12_0) {
            return GameVersion.V1_2_0;
        } else if (protocol == ProtocolInfo.v1_1_0) {
            return GameVersion.V1_1_0;
        }
        throw new IllegalArgumentException("Invalid chunk protocol: " + protocol);
    }

    private static boolean matchMVChunkProtocol(GameVersion chunkVersion, GameVersion playerVersion) {
        if (chunkVersion == playerVersion) return true;
        if (chunkVersion.isNetEase() != playerVersion.isNetEase()) return false;

        int chunk = chunkVersion.getProtocol();
        int player = playerVersion.getProtocol();

        if (chunk <= ProtocolInfo.v1_1_0) if (player <= ProtocolInfo.v1_1_0) return true;
        if (chunk == ProtocolInfo.v1_2_0)
            if (player >= ProtocolInfo.v1_2_0) if (player < ProtocolInfo.v1_12_0) return true;
        if (chunk == ProtocolInfo.v1_12_0) if (player == ProtocolInfo.v1_12_0) return true;
        if (chunk == ProtocolInfo.v1_13_0) if (player == ProtocolInfo.v1_13_0) return true;
        if (chunk == ProtocolInfo.v1_14_0)
            if (player == ProtocolInfo.v1_14_0 || player == ProtocolInfo.v1_14_60) return true;
        if (chunk == ProtocolInfo.v1_16_0)
            if (player >= ProtocolInfo.v1_16_0) if (player <= ProtocolInfo.v1_16_100_52) return true;
        if (chunk == ProtocolInfo.v1_16_100)
            if (player >= ProtocolInfo.v1_16_100) if (player < ProtocolInfo.v1_16_210) return true;
        if (chunk == ProtocolInfo.v1_16_210)
            if (player >= ProtocolInfo.v1_16_210) if (player < ProtocolInfo.v1_17_0) return true;
        if (chunk == ProtocolInfo.v1_17_0) if (player == ProtocolInfo.v1_17_0) return true;
        if (chunk == ProtocolInfo.v1_17_10)
            if (player >= ProtocolInfo.v1_17_10) if (player < ProtocolInfo.v1_17_30) return true;
        if (chunk == ProtocolInfo.v1_17_30) if (player == ProtocolInfo.v1_17_30) return true;
        if (chunk == ProtocolInfo.v1_17_40) if (player == ProtocolInfo.v1_17_40) return true;
        if (chunk == ProtocolInfo.v1_18_0) if (player == ProtocolInfo.v1_18_0) return true;
        if (chunk == ProtocolInfo.v1_18_10)
            if (player >= ProtocolInfo.v1_18_10_26) if (player < ProtocolInfo.v1_18_30) return true;
        if (chunk == ProtocolInfo.v1_18_30) if (player == ProtocolInfo.v1_18_30) return true;
        if (chunk == ProtocolInfo.v1_19_0)
            if (player >= ProtocolInfo.v1_19_0_29) if (player < ProtocolInfo.v1_19_20) return true;
        if (chunk == ProtocolInfo.v1_19_20)
            if (player >= ProtocolInfo.v1_19_20) if (player < ProtocolInfo.v1_19_50) return true;
        if (chunk == ProtocolInfo.v1_19_50)
            if (player >= ProtocolInfo.v1_19_50_20) if (player < ProtocolInfo.v1_19_60) return true;
        if (chunk == ProtocolInfo.v1_19_60)
            if (player >= ProtocolInfo.v1_19_60) if (player < ProtocolInfo.v1_19_70) return true;
        if (chunk == ProtocolInfo.v1_19_70)
            if (player >= ProtocolInfo.v1_19_70_24) if (player < ProtocolInfo.v1_19_80) return true;
        if (chunk == ProtocolInfo.v1_19_80) if (player == ProtocolInfo.v1_19_80) return true;
        if (chunk == ProtocolInfo.v1_20_0)
            if (player >= ProtocolInfo.v1_20_0_23) if (player < ProtocolInfo.v1_20_10_21) return true;
        if (chunk == ProtocolInfo.v1_20_10)
            if (player >= ProtocolInfo.v1_20_10_21) if (player < ProtocolInfo.v1_20_30_24) return true;
        if (chunk == ProtocolInfo.v1_20_30)
            if (player >= ProtocolInfo.v1_20_30_24) if (player < ProtocolInfo.v1_20_40) return true;
        if (chunk == ProtocolInfo.v1_20_40) if (player == ProtocolInfo.v1_20_40) return true;
        if (chunk == ProtocolInfo.v1_20_50) if (player == ProtocolInfo.v1_20_50) return true;
        if (chunk == ProtocolInfo.v1_20_60) if (player == ProtocolInfo.v1_20_60) return true;
        if (chunk == ProtocolInfo.v1_20_70) if (player == ProtocolInfo.v1_20_70) return true;
        if (chunk == ProtocolInfo.v1_20_80) if (player == ProtocolInfo.v1_20_80) return true;
        if (chunk == ProtocolInfo.v1_21_0)
            if (player >= ProtocolInfo.v1_21_0) if (player < ProtocolInfo.v1_21_20) return true;
        if (chunk == ProtocolInfo.v1_21_20) if (player == ProtocolInfo.v1_21_20) return true;
        if (chunk == ProtocolInfo.v1_21_30) if (player == ProtocolInfo.v1_21_30) return true;
        if (chunk == ProtocolInfo.v1_21_40) if (player == ProtocolInfo.v1_21_40) return true;
        if (chunk == ProtocolInfo.v1_21_50)
            if (player >= ProtocolInfo.v1_21_50_26) if (player < ProtocolInfo.v1_21_60) return true;
        if (chunk == ProtocolInfo.v1_21_60) if (player == ProtocolInfo.v1_21_60) return true;
        if (chunk == ProtocolInfo.v1_21_70)
            if (player >= ProtocolInfo.v1_21_70_24) if (player < ProtocolInfo.v1_21_80) return true;
        if (chunk == ProtocolInfo.v1_21_80) if (player == ProtocolInfo.v1_21_80) return true;
        if (chunk == ProtocolInfo.v1_21_90)
            if (player >= ProtocolInfo.v1_21_90) if (player <= ProtocolInfo.v1_21_93) return true;
        if (chunk == GameVersion.V1_21_100.getProtocol()) if (player == GameVersion.V1_21_100.getProtocol()) return true;
        if (chunk == GameVersion.V1_21_110.getProtocol()) if (player >= GameVersion.V1_21_110_26.getProtocol()) return true;
        return false; //TODO Multiversion  Remember to update when block palette changes
    }

    private static class CharacterHashMap extends HashMap<Character, Object> {

        @Override
        public int size() {
            return Character.MAX_VALUE;
        }
    }

    @AllArgsConstructor
    @Data
    private static class QueuedUpdate {
        @NotNull
        private Block block;
        private BlockFace neighbor;
    }
}
