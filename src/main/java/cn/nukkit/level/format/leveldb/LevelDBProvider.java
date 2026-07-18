package cn.nukkit.level.format.leveldb;

import cn.nukkit.GameVersion;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.format.leveldb.serializer.*;
import cn.nukkit.level.format.leveldb.structure.*;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.math.BlockVector3;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.nbt.tag.ListTag;
import cn.nukkit.plugin.InternalPlugin;
import cn.nukkit.scheduler.Task;
import cn.nukkit.utils.*;
import com.google.common.collect.ImmutableMap;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import io.netty.buffer.*;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import lombok.extern.log4j.Log4j2;
import net.daporkchop.ldbjni.DBProvider;
import net.daporkchop.ldbjni.LevelDB;
import net.daporkchop.lib.natives.FeatureBuilder;
import org.cloudburstmc.nbt.*;
import org.iq80.leveldb.*;

import javax.annotation.Nullable;
import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;
import static cn.nukkit.level.format.leveldb.LevelDBKey.*;

@Log4j2
public class LevelDBProvider implements LevelProvider {

    private static final DBProvider JAVA_LDB_PROVIDER = (DBProvider) FeatureBuilder.create(LevelDBProvider.class).addJava("net.daporkchop.ldbjni.java.JavaDBProvider").build();
    private static final byte[] FINALIZATION_STATE_ENCODING_KEY = "NukkitMOTFinalizationStateEncoding".getBytes(StandardCharsets.UTF_8);
    private static final byte[] FINALIZATION_STATE_ENCODING_BEDROCK = new byte[]{1};

    protected final Long2ObjectMap<BaseFullChunk> chunks = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());

    protected final DB db;

    protected Level level;

    protected final String path;

    protected CompoundTag levelData;
    private Vector3 spawn;
    private Long cachedSeed;

    protected volatile boolean closed;
    protected final Lock gcLock;
    // 保护 db 句柄不被异步读取与 close 竞争:异步 readChunkOffThread 持读锁(可并发,db.get 本身线程安全),close 释放 db 前持写锁
    // Guards the db handle against a read/close race: async readChunkOffThread takes the read lock (concurrent — db.get is thread-safe), close takes the write lock before freeing db
    private final ReadWriteLock dbReadCloseLock = new ReentrantReadWriteLock();
    private final ExecutorService executor;

    // 每区块挂起写槽位:保存/卸载产生的 WriteBatch 在此等待落盘,同区块新批次替换旧批次(旧内容永不落盘),
    // 落盘互斥于槽位锁,保证同区块写全序、最新内容最终生效;读取入口先认领槽位,防止「卸载→秒回」读到陈旧数据
    // Per-chunk pending-write slots: WriteBatches from save/unload wait here; a newer batch replaces the older one
    // (superseded content never hits disk). Commits are mutually exclusive per slot, giving a total order per chunk with
    // newest-wins; read entry points claim the slot first so an unload-then-reload never observes stale data
    private final ConcurrentHashMap<Long, PendingWrite> pendingWrites = new ConcurrentHashMap<>();
    private volatile boolean pendingWriteBacklogWarned;

    private static final int MAX_PENDING_WRITE_RETRIES = 3;

    // 字段仅在持有 lock 时读写 / Fields are read and written only while holding lock
    static final class PendingWrite {
        final ReentrantLock lock = new ReentrantLock();
        WriteBatch batch;
        long changeSnapshot;
        LevelDBChunk chunkRef;
        int retries;
    }

    private Task autoCompactionTask;

    public LevelDBProvider(Level level, String path) {
        this.level = level;
        this.path = path;
        Path dirPath = Paths.get(path);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        Path levelDatFile = dirPath.resolve("level.dat");
        try (InputStream stream = Files.newInputStream(levelDatFile)) {
            //noinspection ResultOfMethodCallIgnored
            stream.skip(8);
            this.levelData = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            log.fatal("Failed to load the level.dat file at {}, attempting to load level.dat.bak instead!", levelDatFile, e);
            try {
                Path bakPath = levelDatFile.resolveSibling("level.dat.bak");
                if (!bakPath.toFile().isFile()) {
                    log.fatal("The file {} does not exists!", bakPath);
                    FileNotFoundException ex = new FileNotFoundException("The file " + bakPath + " does not exists!");
                    ex.addSuppressed(e);
                    throw ex;
                }
                try (InputStream stream = Files.newInputStream(bakPath)) {
                    //noinspection ResultOfMethodCallIgnored
                    stream.skip(8);
                    this.levelData = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
                } catch (Exception e2) {
                    log.fatal("Failed to load the level.dat.bak file at {}", levelDatFile);
                    e2.addSuppressed(e);
                    throw e2;
                }
            } catch (Exception e2) {
                LevelException ex = new LevelException("Could not load the level.dat and the level.dat.bak files. You might need to restore them from a backup!", e);
                ex.addSuppressed(e2);
                throw ex;
            }
        }

        try {
            Files.copy(levelDatFile, levelDatFile.resolveSibling("level.dat.bak"), StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            log.warn("Failed to backup level.dat to level.dat.bak", e);
        }

        if (!this.levelData.contains("Generator")) {
            this.levelData.putInt("Generator", Generator.TYPE_INFINITE);
        }

        if (!this.levelData.contains("generatorOptions")) {
            this.levelData.putString("generatorOptions", "");
        }

        try {
            this.db = openDB(dirPath.resolve("db").toFile());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        this.migrateLegacyNukkitFinalizationStates();

        this.gcLock = new ReentrantLock();

        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat("LevelDB Executor for " + this.getName());
        builder.setUncaughtExceptionHandler((thread, ex) -> Server.getInstance().getLogger().error("Exception in " + thread.getName(), ex));
        this.executor = Executors.newSingleThreadExecutor(builder.build());

        if (level.isAutoCompaction()) {
            int delay = level.getServer().getAutoCompactionTicks();
            this.autoCompactionTask = new Task() {
                @Override
                public void onRun(int currentTick) {
                    if (closed || !level.isAutoCompaction()) {
                        this.cancel();
                        return;
                    }
                    CompletableFuture.runAsync(new AutoCompaction(), LevelDBProvider.this.executor);
                }
            };
            level.getServer().getScheduler().scheduleDelayedRepeatingTask(InternalPlugin.INSTANCE, autoCompactionTask, delay + ThreadLocalRandom.current().nextInt(delay), delay);
        }
    }

    @SuppressWarnings("unused")
    public static String getProviderName() {
        return "leveldb";
    }

    @SuppressWarnings("unused")
    public static byte getProviderOrder() {
        return ORDER_ZXY;
    }

    @SuppressWarnings("unused")
    public static boolean usesChunkSection() {
        return true;
    }

    public static boolean isValid(String path) {
        return new File(path + "/level.dat").exists() && new File(path + "/db").isDirectory();
    }

    @SuppressWarnings("unused")
    public static void generate(String path, String name, long seed, Class<? extends Generator> generator) throws IOException {
        generate(path, name, seed, generator, new HashMap<>());
    }

    public static void generate(String path, String name, long seed, Class<? extends Generator> generator, Map<String, String> options) throws IOException {
        Path dirPath = Paths.get(path);
        Path dbPath = dirPath.resolve("db");
        Files.createDirectories(dbPath);

        int generatorType = Integer.parseInt(options.getOrDefault("Generator", Generator.TYPE_INFINITE + ""));
        Vector3 spawnPosition = new Vector3(128, 70, 128);//options.getSpawnPosition();

        CompoundTag levelData = new CompoundTag();
        updateLevelData(levelData);
        levelData
                .putInt("Generator", generatorType)
                .putString("generatorName", Generator.getGeneratorName(generator))
                .putString("generatorOptions", options.getOrDefault("preset", ""))
                .putString("LevelName", name)
                .putLong("RandomSeed", seed)
                .putInt("SpawnX", spawnPosition.getFloorX())
                .putInt("SpawnY", spawnPosition.getFloorY())
                .putInt("SpawnZ", spawnPosition.getFloorZ());

        GameRules.getDefault().writeBedrockNBT(levelData);

        try (OutputStream stream = Files.newOutputStream(dirPath.resolve("level.dat"))) {
            byte[] data = NBTIO.write(levelData, ByteOrder.LITTLE_ENDIAN);
            stream.write(Binary.writeLInt(CURRENT_STORAGE_VERSION));
            stream.write(Binary.writeLInt(data.length));
            stream.write(data);
        }

        DB db = openDB(dbPath.toFile());
        db.close();
    }

    protected static DB openDB(File dir) throws IOException {
        Options options = new Options()
                .createIfMissing(true)
                .compressionType(CompressionType.ZLIB_RAW)
                .cacheSize(1024L * 1024L * Server.getInstance().levelDbCache)
                .blockSize(64 * 1024);
        return Server.getInstance().useNativeLevelDB ? LevelDB.PROVIDER.open(dir, options) : JAVA_LDB_PROVIDER.open(dir, options);
    }

    private void migrateLegacyNukkitFinalizationStates() {
        byte[] encodingMarker = this.db.get(FINALIZATION_STATE_ENCODING_KEY);
        if (Arrays.equals(encodingMarker, FINALIZATION_STATE_ENCODING_BEDROCK)) {
            return;
        }
        int storageVersion = this.levelData.getInt("StorageVersion");
        if (storageVersion <= 0 || storageVersion >= CURRENT_STORAGE_VERSION) {
            return;
        }

        Set<ByteBuffer> legacyNukkitChunks = findLegacyNukkitChunkMarkers(this.db, this.path);
        int migrated = 0;
        try (DBIterator iterator = this.db.iterator(); WriteBatch writeBatch = this.db.createWriteBatch()) {
            while (iterator.hasNext()) {
                Entry<byte[], byte[]> entry = iterator.next();
                byte[] key = entry.getKey();
                if (!isFinalizationStateKey(key)) {
                    continue;
                }

                int legacyValue = readFinalizationStateValue(entry.getValue());
                if (!isLegacyNukkitFinalizationState(key, legacyValue, legacyNukkitChunks)) {
                    continue;
                }

                ChunkState state = switch (legacyValue) {
                    case 0, 1 -> ChunkState.GENERATED;
                    case 2 -> ChunkState.POPULATED;
                    case 3 -> ChunkState.FINISHED;
                    default -> throw new IllegalArgumentException("Unsupported legacy Nukkit chunk finalization state: " + legacyValue);
                };
                writeBatch.put(Arrays.copyOf(key, key.length), serializeFinalizationState(state));
                migrated++;
            }

            if (migrated == 0) {
                return;
            }

            writeBatch.put(FINALIZATION_STATE_ENCODING_KEY, FINALIZATION_STATE_ENCODING_BEDROCK);
            this.db.write(writeBatch);
        } catch (IOException e) {
            throw new RuntimeException("Unable to migrate legacy Nukkit LevelDB finalization states for " + this.path, e);
        }

        this.levelData.putInt("StorageVersion", CURRENT_STORAGE_VERSION);
        this.saveLevelData();
        log.info("Migrated {} legacy Nukkit LevelDB finalization states for {}", migrated, this.getName());
    }

    static boolean hasLegacyNukkitFinalizationState(DB db, String path) {
        Set<ByteBuffer> legacyNukkitChunks = findLegacyNukkitChunkMarkers(db, path);
        try (DBIterator iterator = db.iterator()) {
            while (iterator.hasNext()) {
                Entry<byte[], byte[]> entry = iterator.next();
                if (!isFinalizationStateKey(entry.getKey())) {
                    continue;
                }
                if (isLegacyNukkitFinalizationState(entry.getKey(), readFinalizationStateValue(entry.getValue()), legacyNukkitChunks)) {
                    return true;
                }
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Unable to scan LevelDB finalization states for " + path, e);
        }
        return false;
    }

    static boolean isLegacyNukkitFinalizationState(byte[] finalizationKey, int value, Set<ByteBuffer> legacyNukkitChunks) {
        if (value == ChunkState.FINISHED.ordinal()) {
            return true;
        }
        return legacyNukkitChunks.contains(ByteBuffer.wrap(finalizationKey))
                && (value == ChunkState.GENERATED.ordinal() || value == ChunkState.POPULATED.ordinal());
    }

    private static Set<ByteBuffer> findLegacyNukkitChunkMarkers(DB db, String path) {
        Set<ByteBuffer> chunks = new HashSet<>();
        try (DBIterator iterator = db.iterator()) {
            while (iterator.hasNext()) {
                byte[] key = iterator.next().getKey();
                if (isLegacyNukkitChunkMarkerKey(key)) {
                    chunks.add(ByteBuffer.wrap(toFinalizationStateKey(key)));
                }
            }
        } catch (RuntimeException e) {
            throw new RuntimeException("Unable to scan LevelDB Nukkit chunk markers for " + path, e);
        }
        return chunks;
    }

    private static boolean isLegacyNukkitChunkMarkerKey(byte[] key) {
        if (key.length == 10) {
            return key[8] == NUKKIT_BLOCK_LIGHT.getCode() || key[8] == NUKKIT_SKY_LIGHT.getCode();
        }
        if (key.length == 14) {
            return key[12] == NUKKIT_BLOCK_LIGHT.getCode() || key[12] == NUKKIT_SKY_LIGHT.getCode();
        }
        return false;
    }

    private static byte[] toFinalizationStateKey(byte[] nukkitMarkerKey) {
        if (nukkitMarkerKey.length == 10) {
            byte[] key = Arrays.copyOf(nukkitMarkerKey, 9);
            key[8] = STATE_FINALIZATION.getCode();
            return key;
        }
        byte[] key = Arrays.copyOf(nukkitMarkerKey, 13);
        key[12] = STATE_FINALIZATION.getCode();
        return key;
    }

    private static boolean isFinalizationStateKey(byte[] key) {
        return (key.length == 9 && key[8] == STATE_FINALIZATION.getCode())
                || (key.length == 13 && key[12] == STATE_FINALIZATION.getCode());
    }

    public static void updateLevelData(CompoundTag levelData) {
        levelData.putLong("LastPlayed", System.currentTimeMillis() / 1000)
                .putString("baseGameVersion", "*")
                .putString("InventoryVersion", Utils.getVersionByProtocol(PALETTE_VERSION))
                .putInt("NetworkVersion", PALETTE_VERSION)
                .putList(new ListTag<>("MinimumCompatibleClientVersion", CURRENT_LEVEL_VERSION))
                .putList(new ListTag<>("lastOpenedWithVersion", CURRENT_LEVEL_VERSION))
                .putInt("StorageVersion", CURRENT_STORAGE_VERSION)
                .putInt("WorldVersion", 1);

        if (!levelData.contains("DayCycleStopTime")) {
            levelData.putInt("DayCycleStopTime", -1);
        }

        levelData.putIntIfAbsent("Difficulty", 0);
        levelData.putByteIfAbsent("ForceGameType", 0);
        levelData.putIntIfAbsent("GameType", 0);
        levelData.putIntIfAbsent("Platform", 2);
        levelData.putLongIfAbsent("Time", 0);
        levelData.putByteIfAbsent("eduLevel", 0);
        levelData.putByteIfAbsent("hasBeenLoadedInCreative", 1); // this actually determines whether achievements can be earned in this world
        levelData.putByteIfAbsent("immutableWorld", 0);
        levelData.putFloatIfAbsent("lightningLevel", 0);
        levelData.putIntIfAbsent("lightningTime", 1);
        levelData.putFloatIfAbsent("rainLevel", 0);
        levelData.putIntIfAbsent("rainTime", 1);
        levelData.putByteIfAbsent("spawnMobs", 1);
        levelData.putByteIfAbsent("texturePacksRequired", 0);
        levelData.putLongIfAbsent("currentTick", 1);
        levelData.putIntIfAbsent("LimitedWorldOriginX", 128);
        levelData.putIntIfAbsent("LimitedWorldOriginY", Short.MAX_VALUE);
        levelData.putIntIfAbsent("LimitedWorldOriginZ", 128);
        levelData.putIntIfAbsent("limitedWorldWidth", 16);
        levelData.putIntIfAbsent("limitedWorldDepth", 16);
        levelData.putLongIfAbsent("worldStartCount", ((long) Integer.MAX_VALUE) & 0xffffffffL);
        levelData.putByteIfAbsent("bonusChestEnabled", 0);
        levelData.putByteIfAbsent("bonusChestSpawned", 1);
        levelData.putByteIfAbsent("CenterMapsToOrigin", 0);
        levelData.putByteIfAbsent("commandsEnabled", 1);
        levelData.putByteIfAbsent("ConfirmedPlatformLockedContent", 0);
        levelData.putByteIfAbsent("educationFeaturesEnabled", 0);
        levelData.putIntIfAbsent("eduOffer", 0);
        levelData.putByteIfAbsent("hasLockedBehaviorPack", 0);
        levelData.putByteIfAbsent("hasLockedResourcePack", 0);
        levelData.putByteIfAbsent("isFromLockedTemplate", 0);
        levelData.putByteIfAbsent("isFromWorldTemplate", 0);
        levelData.putByteIfAbsent("isSingleUseWorld", 0);
        levelData.putByteIfAbsent("isWorldTemplateOptionLocked", 0);
        levelData.putByteIfAbsent("LANBroadcast", 1);
        levelData.putByteIfAbsent("LANBroadcastIntent", 1);
        levelData.putByteIfAbsent("MultiplayerGame", 1);
        levelData.putByteIfAbsent("MultiplayerGameIntent", 1);
        levelData.putIntIfAbsent("PlatformBroadcastIntent", 3);
        levelData.putIntIfAbsent("XBLBroadcastIntent", 3);
        levelData.putIntIfAbsent("NetherScale", 8);
        levelData.putStringIfAbsent("prid", "");
        levelData.putByteIfAbsent("requiresCopiedPackRemovalCheck", 0);
        levelData.putIntIfAbsent("serverChunkTickRange", 4);
        levelData.putByteIfAbsent("SpawnV1Villagers", 0);
        levelData.putByteIfAbsent("startWithMapEnabled", 0);
        levelData.putByteIfAbsent("useMsaGamertagsOnly", 0);
        levelData.putIntIfAbsent("permissionsLevel", 0);
        levelData.putIntIfAbsent("playerPermissionsLevel", 1);
        levelData.putByteIfAbsent("isCreatedInEditor", 0);
        levelData.putByteIfAbsent("isExportedFromEditor", 0);
        levelData.putStringIfAbsent("BiomeOverride", "");
        levelData.putStringIfAbsent("FlatWorldLayers", DEFAULT_FLAT_WORLD_LAYERS);
        levelData.putCompoundIfAbsent("world_policies", new CompoundTag(Collections.emptyMap()));
        levelData.putCompoundIfAbsent("experiments", new CompoundTag()
                .putByte("experiments_ever_used", 0)
                .putByte("saved_with_toggled_experiments", 0));
    }

    @Override
    public void saveLevelData() {
        updateLevelData(levelData);

        try (OutputStream stream = Files.newOutputStream(Paths.get(path, "level.dat"))) {
            byte[] data = NBTIO.write(levelData, ByteOrder.LITTLE_ENDIAN);
            stream.write(Binary.writeLInt(CURRENT_STORAGE_VERSION));
            stream.write(Binary.writeLInt(data.length));
            stream.write(data);
        } catch (IOException e) {
            throw new RuntimeException("Unable to save level.dat: " + path, e);
        }
    }

    @Override
    public void requestChunkTask(ObjectSet<GameVersion> protocols, int chunkX, int chunkZ) {
        LevelDBChunk chunk = (LevelDBChunk) this.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        if (this.getServer().asyncChunkSending) {
            final BaseChunk chunkClone = chunk.cloneForChunkSending();
            this.level.getAsyncChuckExecutor().execute(() -> {
                NetworkChunkSerializer.serialize(protocols, chunkClone, networkChunkSerializerCallback -> {
                    getLevel().asyncChunkRequestCallback(networkChunkSerializerCallback.getGameVersion(),
                            timestamp,
                            chunkX,
                            chunkZ,
                            networkChunkSerializerCallback.getSubchunks(),
                            networkChunkSerializerCallback.getStream().getBuffer()
                    );
                }, level.antiXrayEnabled(), getLevel().getDimensionData());
            });
        }else {
            NetworkChunkSerializer.serialize(protocols, chunk, networkChunkSerializerCallback -> {
                this.getLevel().chunkRequestCallback(networkChunkSerializerCallback.getGameVersion(),
                        timestamp,
                        chunkX,
                        chunkZ,
                        networkChunkSerializerCallback.getSubchunks(),
                        networkChunkSerializerCallback.getStream().getBuffer()
                );
            }, level.antiXrayEnabled(), this.level.getDimensionData());
        }
    }

    @Override
    public Map<String, Object> getGeneratorOptions() {
        Map<String, Object> options = new Object2ObjectOpenHashMap<>();
        options.put("preset", levelData.getString("generatorOptions"));
        return options;
    }

    @Override
    public BaseFullChunk getLoadedChunk(int chunkX, int chunkZ) {
        long index = Level.chunkHash(chunkX, chunkZ);
        return this.chunks.get(index);
    }

    @Override
    public BaseFullChunk getLoadedChunk(long hash) {
        return this.chunks.get(hash);
    }

    @Override
    public Map<Long, BaseFullChunk> getLoadedChunks() {
        return ImmutableMap.copyOf(chunks);
    }

    public Long2ObjectMap<? extends FullChunk> getLoadedChunksUnsafe() {
        return chunks;
    }

    @Override
    public boolean isChunkLoaded(int chunkX, int chunkZ) {
        return this.isChunkLoaded(Level.chunkHash(chunkX, chunkZ));
    }

    @Override
    public boolean isChunkLoaded(long hash) {
        return this.chunks.containsKey(hash);
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ) {
        return this.loadChunk(chunkX, chunkZ, false);
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ, boolean create) {
        long index = Level.chunkHash(chunkX, chunkZ);
        if (this.chunks.containsKey(index)) {
            return true;
        }

        return this.readOrCreateChunk(chunkX, chunkZ, create) != null;
    }

    @Nullable
    public LevelDBChunk readChunk(int chunkX, int chunkZ) {
        byte[] versionData = this.db.get(VERSION.getKey(chunkX, chunkZ, this.level.getDimensionData().getDimensionId()));
        if (versionData == null || versionData.length != 1) {
            versionData = this.db.get(VERSION_OLD.getKey(chunkX, chunkZ, this.level.getDimensionData().getDimensionId()));
            if (versionData == null || versionData.length != 1) {
                return null;
            }
        }

        ChunkBuilder chunkBuilder = new ChunkBuilder(chunkX, chunkZ, this);

        byte[] finalized = this.db.get(STATE_FINALIZATION.getKey(chunkX, chunkZ, this.level.getDimensionData().getDimensionId()));
        chunkBuilder.state(deserializeFinalizationState(finalized));

        byte chunkVersion = versionData[0];

        if (chunkVersion < 7) {
            chunkBuilder.dirty();
        }

        ChunkSerializers.deserializeChunk(this.db, chunkBuilder, chunkVersion);

        Data3dSerializer.deserialize(this.db, chunkBuilder);
        if (!chunkBuilder.hasBiome3d()) {
            Data2dSerializer.deserialize(this.db, chunkBuilder);
        }

        BlockEntitySerializer.loadBlockEntities(this.db, chunkBuilder);
        EntitySerializer.loadEntities(this.db, chunkBuilder);

        byte[] tickingData = this.db.get(PENDING_TICKS.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (tickingData != null && tickingData.length != 0) {
            loadBlockTickingQueue(tickingData, false);
        }

        byte[] randomTickingData = this.db.get(RANDOM_TICKS.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (randomTickingData != null && randomTickingData.length != 0) {
            loadBlockTickingQueue(randomTickingData, true);
        }

        LevelDBChunk chunk = chunkBuilder.build();

        if (chunkVersion <= 2) {
            chunk.setHeightmapOrBiomesDirty();
        }

        return chunk;
    }

    @Override
    public boolean unloadChunk(int chunkX, int chunkZ) {
        return this.unloadChunk(chunkX, chunkZ, true);
    }

    @Override
    public boolean unloadChunk(int chunkX, int chunkZ, boolean safe) {
        long index = Level.chunkHash(chunkX, chunkZ);
        BaseFullChunk chunk = this.chunks.get(index);
        if (chunk == null) {
            return false;
        }
        if (chunk instanceof LevelDBChunk levelDBChunk) {
            // async-chunks 统一管理发送/加载/保存 / async-chunks governs sending, loading and saving together
            if (Server.getInstance().asyncChunkSending) {
                // 主线程只序列化(实体存活,批次自包含),db.write 交给 executor;不等待、不同步写,消除卸载路径的 DbImpl.write park。
                // 槽内批次快照与当前 changes 一致才视为内容最新可跳过重复序列化(仅 containsKey 不够:可能是陈旧 autosave 批次);
                // 区块即将卸载,解除 chunkRef 以免滞留其内存
                // The main thread only serializes (entities alive, batch self-contained); db.write goes to the executor — no wait,
                // no sync write, removing the DbImpl.write park from the unload path. The staged batch counts as current only when
                // its snapshot equals the live changes counter (containsKey alone is insufficient: it may be a stale autosave batch);
                // the chunk is being evicted, so drop chunkRef to avoid retaining its memory
                boolean staged = false;
                PendingWrite pw = this.pendingWrites.get(index);
                if (pw != null) {
                    pw.lock.lock();
                    try {
                        staged = pw.batch != null && pw.changeSnapshot == levelDBChunk.getChanges();
                        pw.chunkRef = null;
                    } finally {
                        pw.lock.unlock();
                    }
                }
                if (!staged && levelDBChunk.hasChanged() && levelDBChunk.isGenerated()) {
                    this.stagePendingWrite(index, chunkX, chunkZ, levelDBChunk, false);
                    this.enqueueCommit(index);
                }
            } else {
                // 配置回退:排空该区块挂起写后按原行为同步兜底保存
                // Config fallback: drain this chunk's pending write, then sync-save as before
                this.commitPendingWrite(index);
                if (levelDBChunk.hasChanged()) {
                    this.saveChunkSync(chunkX, chunkZ, levelDBChunk);
                }
            }
        }
        if (!chunk.unload(false, safe)) {
            return false;
        }
        this.chunks.remove(index, chunk);
        return true;
    }

    @Override
    public void saveChunk(int chunkX, int chunkZ) {
        BaseFullChunk chunk = this.getChunk(chunkX, chunkZ);
        if (chunk != null) {
            this.saveChunk(chunkX, chunkZ, chunk);
        }
    }

    @Override
    public void saveChunk(int chunkX, int chunkZ, FullChunk chunk) {
        this.saveChunkFuture(chunkX, chunkZ, chunk);
    }

    public CompletableFuture<Void> saveChunkFuture(int chunkX, int chunkZ, FullChunk fullChunk) {
        if (!(fullChunk instanceof LevelDBChunk chunk)) {
            throw new IllegalArgumentException("Only LevelDB chunks are supported");
        }

        chunk.setX(chunkX);
        chunk.setZ(chunkZ);

        if (!chunk.isGenerated()) {
            return CompletableFuture.completedFuture(null);
        }

        long hash = Level.chunkHash(chunkX, chunkZ);
        this.stagePendingWrite(hash, chunkX, chunkZ, chunk, true);
        try {
            // commit 会等待/接手 in-flight 写,future 完成即代表本次内容(或更新内容)已落盘
            // commit waits for / takes over any in-flight write; future completion means this content (or newer) is durable
            return CompletableFuture.runAsync(() -> this.commitPendingWrite(hash), this.executor);
        } catch (RejectedExecutionException e) {
            this.commitPendingWrite(hash);
            return CompletableFuture.completedFuture(null);
        }
    }

    public void saveChunkSync(int chunkX, int chunkZ, FullChunk fullChunk) {
        if (!(fullChunk instanceof LevelDBChunk chunk)) {
            throw new IllegalArgumentException("Only LevelDB chunks are supported");
        }

        chunk.setX(chunkX);
        chunk.setZ(chunkZ);

        if (!chunk.isGenerated()) {
            return;
        }

        // 同步保存也经槽位:与挂起的异步写保持同区块全序(直接 db.write 会被稍后落盘的旧批次覆盖)
        // Sync saves also go through the slot: keeps the per-chunk total order with pending async writes (a direct db.write could be overwritten by an older batch landing later)
        long hash = Level.chunkHash(chunkX, chunkZ);
        this.stagePendingWrite(hash, chunkX, chunkZ, chunk, true);
        this.commitPendingWrite(hash);
    }

    /**
     * 主线程序列化并放入槽位;新批次替换未落盘的旧批次(合并重复保存)。keepChunkRef 仅在区块仍在缓存时为 true,
     * 卸载起源传 false 以免滞留已卸载区块内存
     * <p>
     * Serialize on the caller thread and stage into the slot; a new batch replaces an unwritten older one (merging duplicate saves).
     * keepChunkRef is true only while the chunk stays cached; unload-origin saves pass false to avoid retaining evicted chunk memory
     */
    private void stagePendingWrite(long hash, int chunkX, int chunkZ, LevelDBChunk chunk, boolean keepChunkRef) {
        long snapshot = chunk.getChanges();
        WriteBatch batch = this.save0(chunkX, chunkZ, chunk);
        this.pendingWrites.compute(hash, (h, pw) -> {
            if (pw == null) {
                pw = new PendingWrite();
            }
            pw.lock.lock();
            try {
                if (pw.batch != null) {
                    closeBatchQuietly(pw.batch, this.getName());
                }
                pw.batch = batch;
                pw.changeSnapshot = snapshot;
                pw.chunkRef = keepChunkRef ? chunk : null;
                pw.retries = 0;
            } finally {
                pw.lock.unlock();
            }
            return pw;
        });
        this.warnOnPendingWriteBacklog();
    }

    /**
     * 落盘该区块的挂起写(executor 任务与认领线程共用):取走槽内最新批次写库;写进行中则等待完成——
     * 这是读取入口可见性正确的关键(等它落盘再 db.get)。失败重试 ≤{@value MAX_PENDING_WRITE_RETRIES} 次(批次纯字节,重试安全)
     * <p>
     * Commit the chunk's pending write (shared by executor tasks and claiming threads): takes the newest staged batch and writes it;
     * if a write is in flight, waits for it — the key to read-entry visibility (db.get only after it lands).
     * Failed writes retry up to {@value MAX_PENDING_WRITE_RETRIES} times (batches are pure bytes, safe to retry)
     */
    private void commitPendingWrite(long hash) {
        PendingWrite pw = this.pendingWrites.get(hash);
        if (pw == null) {
            return;
        }
        boolean needRetry = false;
        pw.lock.lock();
        try {
            WriteBatch batch = pw.batch;
            if (batch != null) {
                pw.batch = null;
                try {
                    this.db.write(batch);
                    if (pw.chunkRef != null) {
                        pw.chunkRef.clearChangesIfUnmodified(pw.changeSnapshot);
                    }
                    closeBatchQuietly(batch, this.getName());
                } catch (Exception e) {
                    if (pw.retries < MAX_PENDING_WRITE_RETRIES) {
                        pw.retries++;
                        pw.batch = batch;
                        needRetry = true;
                        log.warn("Chunk write failed for {} at {}, {} (retry {}/{})", this.getName(),
                                Level.getHashX(hash), Level.getHashZ(hash), pw.retries, MAX_PENDING_WRITE_RETRIES, e);
                    } else {
                        closeBatchQuietly(batch, this.getName());
                        log.error("Giving up chunk write for {} at {}, {} after {} retries", this.getName(),
                                Level.getHashX(hash), Level.getHashZ(hash), MAX_PENDING_WRITE_RETRIES, e);
                    }
                }
            }
        } finally {
            pw.lock.unlock();
        }
        if (needRetry) {
            this.enqueueCommit(hash);
            return;
        }
        // 原子移除空槽;期间若有新批次放入则保留槽位(锁序恒为 CHM bucket → 槽位锁,无反向)
        // Atomically remove the empty slot; keep it if a new batch was staged meanwhile (lock order is always CHM bucket → slot lock, never reversed)
        this.pendingWrites.compute(hash, (h, cur) -> {
            if (cur != pw) {
                return cur;
            }
            cur.lock.lock();
            try {
                return cur.batch == null ? null : cur;
            } finally {
                cur.lock.unlock();
            }
        });
    }

    private void enqueueCommit(long hash) {
        try {
            this.executor.execute(() -> this.commitPendingWrite(hash));
        } catch (RejectedExecutionException e) {
            // executor 已关(关服极端时序):调用线程内落盘兜底;重试路径递归深度受重试上限约束
            // Executor already down (rare close-time ordering): commit inline as a fallback; retry recursion depth is bounded by the retry cap
            this.commitPendingWrite(hash);
        }
    }

    private static void closeBatchQuietly(WriteBatch batch, String levelName) {
        try {
            batch.close();
        } catch (IOException e) {
            log.error("Failed to close WriteBatch for {}", levelName, e);
        }
    }

    private void warnOnPendingWriteBacklog() {
        int size = this.pendingWrites.size();
        int max = Server.getInstance().maxPendingChunkWrites;
        if (size >= max) {
            if (!this.pendingWriteBacklogWarned) {
                this.pendingWriteBacklogWarned = true;
                log.warn("Pending chunk writes for {} reached {} (limit {}); chunk unloading will pause until the backlog drains", this.getName(), size, max);
            }
        } else if (this.pendingWriteBacklogWarned && size <= max / 2) {
            this.pendingWriteBacklogWarned = false;
        }
    }

    /**
     * 当前挂起未落盘的区块写数量(观测用)
     * <p>
     * Number of chunk writes staged but not yet durable (for observability)
     */
    public int getPendingWriteCount() {
        return this.pendingWrites.size();
    }

    @Override
    public boolean isChunkSaveBacklogged() {
        return this.pendingWrites.size() >= Server.getInstance().maxPendingChunkWrites;
    }

    private WriteBatch save0(int chunkX, int chunkZ, LevelDBChunk chunk) {
        WriteBatch writeBatch = this.db.createWriteBatch();

        if (chunk.isSubChunksDirty()) {
            ChunkSerializers.serializeChunk(writeBatch, chunk, CURRENT_LEVEL_CHUNK_VERSION);
        }

        if (chunk.isHeightmapOrBiomesDirty()) {
            if (chunk.has3dBiomes()) {
                Data3dSerializer.serialize(writeBatch, chunk);
            } else {
                Data2dSerializer.serialize(writeBatch, chunk);
            }
        }

        writeBatch.put(LevelDBKey.VERSION.getKey(chunkX, chunkZ, this.level.getDimension()), CHUNK_VERSION_SAVE_DATA);
        writeBatch.put(LevelDBKey.VERSION_OLD.getKey(chunkX, chunkZ, this.level.getDimension()), LEGACY_CHUNK_VERSION_SAVE_DATA);
        writeBatch.put(LevelDBKey.GENERATED_PRE_CAVES_AND_CLIFFS_BLENDING.getKey(chunkX, chunkZ, this.level.getDimension()), GENERATED_PRE_CAVES_AND_CLIFFS_BLENDING_SAVE_DATA);
        writeBatch.put(LevelDBKey.BLENDING_DATA.getKey(chunkX, chunkZ, this.level.getDimension()), BLENDING_DATA_SAVE_DATA);
        writeBatch.put(FINALIZATION_STATE_ENCODING_KEY, FINALIZATION_STATE_ENCODING_BEDROCK);
        writeBatch.put(STATE_FINALIZATION.getKey(chunkX, chunkZ, this.level.getDimensionData().getDimensionId()), serializeFinalizationState(chunk.getState()));

        BlockEntitySerializer.saveBlockEntities(writeBatch, chunk);
        EntitySerializer.saveEntities(this.db, writeBatch, chunk);

        Collection<BlockUpdateEntry> blockUpdateEntries = null;
        // TODO randomBlockUpdate
        //Collection<BlockUpdateEntry> randomBlockUpdateEntries = null;
        long currentTick = 0;

        LevelProvider provider;
        if ((provider = chunk.getProvider()) != null) {
            Level level = provider.getLevel();
            currentTick = level.getCurrentTick();
            //dirty?
            blockUpdateEntries = level.getPendingBlockUpdates(chunk);
            //randomBlockUpdateEntries = level.getPendingRandomBlockUpdates(chunk);
        }

        byte[] pendingScheduledTicksKey = PENDING_TICKS.getKey(chunkX, chunkZ, this.level.getDimension());
        List<BaseFullChunk.PendingBlockUpdate> deferredBlockUpdates = chunk.getDeferredBlockUpdates();
        if ((blockUpdateEntries != null && !blockUpdateEntries.isEmpty())
                || (deferredBlockUpdates != null && !deferredBlockUpdates.isEmpty())) {
            NbtMap ticks = saveBlockTickingQueue(blockUpdateEntries, deferredBlockUpdates, currentTick);
            if (ticks != null) {
                ByteBuf byteBuf = ByteBufAllocator.DEFAULT.ioBuffer();
                try {
                    try (NBTOutputStream outputStream = NbtUtils.createWriterLE(new ByteBufOutputStream(byteBuf))) {
                        outputStream.writeTag(ticks);
                    }
                    writeBatch.put(pendingScheduledTicksKey, Utils.convertByteBuf2Array(byteBuf));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                } finally {
                    byteBuf.release();
                }
            } else {
                writeBatch.delete(pendingScheduledTicksKey);
            }
        } else {
            writeBatch.delete(pendingScheduledTicksKey);
        }

       /* byte[] pendingRandomTicksKey = RANDOM_TICKS.getKey(chunkX, chunkZ);
        if (randomBlockUpdateEntries != null && !randomBlockUpdateEntries.isEmpty()) {
            CompoundTag ticks = saveBlockTickingQueue(randomBlockUpdateEntries, currentTick);
            if (ticks != null) {
                try {
                    writeBatch.put(pendingRandomTicksKey, NBTIO.write(ticks, ByteOrder.LITTLE_ENDIAN));
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            } else {
                writeBatch.delete(pendingRandomTicksKey);
            }
        } else {
            writeBatch.delete(pendingRandomTicksKey);
        }*/

        writeBatch.delete(DATA_2D_LEGACY.getKey(chunkX, chunkZ, this.level.getDimension()));
        writeBatch.delete(LEGACY_TERRAIN.getKey(chunkX, chunkZ, this.level.getDimension()));

        return writeBatch;
    }

    @Override
    public void saveChunks() {
        for (BaseFullChunk chunk : this.chunks.values()) {
            if (chunk.hasChanged()) {
                this.saveChunk(chunk.getX(), chunk.getZ(), chunk);
            }
        }
    }

    @Override
    public void unloadChunks() {
        this.unloadChunksUnsafe(false);
    }

    private void unloadChunksUnsafe(boolean wait) {
        Iterator<BaseFullChunk> iterator = this.chunks.values().iterator();
        while (iterator.hasNext()) {
            LevelDBChunk chunk = (LevelDBChunk) iterator.next();
            if (wait) {
                // 先认领落盘挂起写(含等待 in-flight);随后 hasChanged 通常已清零,避免关服时的二次序列化
                // Claim and commit the pending write first (waiting for any in-flight one); hasChanged is then usually cleared, avoiding a second serialization at close
                this.commitPendingWrite(Level.chunkHash(chunk.getX(), chunk.getZ()));
                // 落盘后仍脏(落盘后有改动/写失败放弃)才同步重存,此时实体仍存活
                // Only re-save synchronously if still dirty after the commit (post-write changes / write given up), while entities are still alive
                if (chunk.hasChanged()) {
                    this.saveChunkSync(chunk.getX(), chunk.getZ(), chunk);
                }
            }
            chunk.unload(level.isSaveOnUnloadEnabled(), false);
            iterator.remove();
        }
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ, boolean create) {
        long index = Level.chunkHash(chunkX, chunkZ);
        BaseFullChunk chunk = this.chunks.get(index);
        if (chunk == null) {
            chunk = this.readOrCreateChunk(chunkX, chunkZ, create);
        }
        return chunk;
    }

    @Override
    public boolean isOffThreadChunkReadSupported() {
        return !this.closed && this.level != null;
    }

    @Override
    public BaseFullChunk readChunkOffThread(int chunkX, int chunkZ) {
        // 不再 synchronized(this):LevelDB db.get 本身线程安全,粗粒度 monitor 会让主线程的 putChunkIfAbsent/readOrCreateChunk 阻塞在异步磁盘读之后。
        // 改持读锁允许并发读,仅与 close()(写锁)互斥,防止读到已释放的 db;读锁内复检 closed 收口 TOCTOU
        // No longer synchronized(this): LevelDB db.get is thread-safe, so a coarse monitor would block the main thread's putChunkIfAbsent/readOrCreateChunk behind an async disk read.
        // A read lock allows concurrent reads and excludes only close() (write lock), preventing use of a freed db; the closed re-check inside the lock closes the TOCTOU
        this.dbReadCloseLock.readLock().lock();
        try {
            // close 竞争 fast-path:持读锁后复检;Level.close() 的 awaitTermination 超时分支可能让任务在此期间执行,此时直接放弃,避免读到 level=null / db 已关
            // close-race fast-path: re-check under the read lock; Level.close()'s awaitTermination timeout branch may let a task run during shutdown — bail out to avoid touching a nulled level / closed db
            if (this.closed) {
                return null;
            }
            // 一次性快照 level 引用:close() 会把 level 置 null,快照保证本次解码全程使用同一引用,中途为 null 则放弃
            // Snapshot the level reference once: close() nulls level; the snapshot keeps a stable reference for the whole decode, bailing out if null
            Level levelSnapshot = this.level;
            if (levelSnapshot == null) {
                return null;
            }
            // 认领挂起写(读锁内,db 必然存活):卸载后未落盘即被异步重载时,先落盘再读
            // Claim the pending write (inside the read lock, db guaranteed alive): commit before reading if the chunk is async-reloaded before its unload write lands
            this.commitPendingWrite(Level.chunkHash(chunkX, chunkZ));
            // 纯读取+解码,不取 provider 锁、不修改 chunks 缓存;ticking 延迟到挂载阶段(主线程 initChunk)回放;挂载由 putChunkIfAbsent 完成
            // Pure read+decode without the provider monitor or cache mutation; ticking is deferred to mount (main-thread initChunk); mounting happens via putChunkIfAbsent
            return this.readChunkDeferred(chunkX, chunkZ, levelSnapshot);
        } finally {
            this.dbReadCloseLock.readLock().unlock();
        }
    }

    /**
     * 异步路径专用解码:与 {@link #readChunk} 等价,但 ticking 调度延迟到 {@link BaseFullChunk#initChunk()} 执行,避免在异步线程触碰 Level 游戏状态
     * <p>
     * Async-path decode: equivalent to {@link #readChunk} but defers ticking scheduling to {@link BaseFullChunk#initChunk()} so the off-thread path never touches Level game state
     */
    @Nullable
    private LevelDBChunk readChunkDeferred(int chunkX, int chunkZ, Level levelSnapshot) {
        byte[] versionData = this.db.get(VERSION.getKey(chunkX, chunkZ, levelSnapshot.getDimensionData().getDimensionId()));
        if (versionData == null || versionData.length != 1) {
            versionData = this.db.get(VERSION_OLD.getKey(chunkX, chunkZ, levelSnapshot.getDimensionData().getDimensionId()));
            if (versionData == null || versionData.length != 1) {
                return null;
            }
        }

        ChunkBuilder chunkBuilder = new ChunkBuilder(chunkX, chunkZ, this);

        byte[] finalized = this.db.get(STATE_FINALIZATION.getKey(chunkX, chunkZ, levelSnapshot.getDimensionData().getDimensionId()));
        chunkBuilder.state(deserializeFinalizationState(finalized));

        byte chunkVersion = versionData[0];

        if (chunkVersion < 7) {
            chunkBuilder.dirty();
        }

        ChunkSerializers.deserializeChunk(this.db, chunkBuilder, chunkVersion);

        Data3dSerializer.deserialize(this.db, chunkBuilder);
        if (!chunkBuilder.hasBiome3d()) {
            Data2dSerializer.deserialize(this.db, chunkBuilder);
        }

        BlockEntitySerializer.loadBlockEntities(this.db, chunkBuilder);
        EntitySerializer.loadEntities(this.db, chunkBuilder);

        // ticking 解析收集到 sink,通过 dataLoader 在 build() 时塞入 chunk.pendingBlockUpdates;实际调度留给主线程 initChunk
        // collect ticking into a sink and attach to chunk.pendingBlockUpdates via a dataLoader at build(); actual scheduling is left to the main-thread initChunk
        List<BaseFullChunk.PendingBlockUpdate> tickingSink = new ArrayList<>();
        byte[] tickingData = this.db.get(PENDING_TICKS.getKey(chunkX, chunkZ, levelSnapshot.getDimension()));
        if (tickingData != null && tickingData.length != 0) {
            this.loadBlockTickingQueueDeferred(tickingData, false, tickingSink);
        }
        byte[] randomTickingData = this.db.get(RANDOM_TICKS.getKey(chunkX, chunkZ, levelSnapshot.getDimension()));
        if (randomTickingData != null && randomTickingData.length != 0) {
            this.loadBlockTickingQueueDeferred(randomTickingData, true, tickingSink);
        }
        if (!tickingSink.isEmpty()) {
            List<BaseFullChunk.PendingBlockUpdate> sinkCopy = tickingSink;
            chunkBuilder.dataLoader((chunk, provider) -> chunk.setPendingBlockUpdates(sinkCopy));
        }

        LevelDBChunk chunk = chunkBuilder.build();

        if (chunkVersion <= 2) {
            chunk.setHeightmapOrBiomesDirty();
        }

        return chunk;
    }

    @Override
    public synchronized BaseFullChunk putChunkIfAbsent(int chunkX, int chunkZ, BaseFullChunk chunk) {
        if (!(chunk instanceof LevelDBChunk)) {
            throw new IllegalArgumentException("Only LevelDB chunks are supported");
        }
        long index = Level.chunkHash(chunkX, chunkZ);
        BaseFullChunk existing = this.chunks.get(index);
        if (existing != null) {
            return existing;
        }
        chunk.setProvider(this);
        chunk.setPosition(chunkX, chunkZ);
        this.chunks.put(index, chunk);
        return null;
    }

    @Override
    public LevelDBChunk getEmptyChunk(int chunkX, int chunkZ) {
        return LevelDBChunk.getEmptyChunk(chunkX, chunkZ, this);
    }

    public DB getDatabase() {
        return db;
    }

    @Override
    public void setChunk(int chunkX, int chunkZ, FullChunk chunk) {
        if (!(chunk instanceof LevelDBChunk)) throw new IllegalArgumentException("Only LevelDB chunks are supported");
        chunk.setProvider(this);
        chunk.setPosition(chunkX, chunkZ);
        long index = Level.chunkHash(chunkX, chunkZ);

        FullChunk oldChunk = this.chunks.get(index);
        if (oldChunk != null && !oldChunk.equals(chunk)) {
            this.unloadChunk(chunkX, chunkZ, false);
        }
        this.chunks.put(index, (LevelDBChunk) chunk);
    }

    @SuppressWarnings("unused")
    public static LevelDBChunkSection createChunkSection(int chunkY) {
        return new LevelDBChunkSection(chunkY);
    }

    private boolean chunkExists(int chunkX, int chunkZ) {
        // 认领挂起写,防止「卸载写未落盘」窗口内误判区块不存在 / Claim the pending write so an unwritten unload save can't make the chunk look absent
        this.commitPendingWrite(Level.chunkHash(chunkX, chunkZ));
        byte[] data = this.db.get(VERSION.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (data == null || data.length == 0) {
            data = this.db.get(VERSION_OLD.getKey(chunkX, chunkZ, this.level.getDimension()));
        }
        return data != null && data.length != 0;
    }

    @Override
    public boolean isChunkGenerated(int chunkX, int chunkZ) {
        BaseFullChunk chunk = this.getChunk(chunkX, chunkZ);
        return chunk != null && chunk.isGenerated();
    }

    @Override
    public boolean isChunkPopulated(int chunkX, int chunkZ) {
        BaseFullChunk chunk = this.getChunk(chunkX, chunkZ);
        return chunk != null && chunk.isPopulated();
    }

    private synchronized LevelDBChunk readOrCreateChunk(int chunkX, int chunkZ, boolean create) {
        // 认领挂起写:卸载后未落盘即被重载时,先把最新批次落盘再读,避免读到陈旧数据
        // Claim the pending write: if the chunk is reloaded before its unload write lands, commit the newest batch first so the read can't be stale
        this.commitPendingWrite(Level.chunkHash(chunkX, chunkZ));
        LevelDBChunk chunk = null;
        try {
            chunk = this.readChunk(chunkX, chunkZ);
        } catch (Exception ex) {
            Server.getInstance().getLogger().error("Failed to read chunk " + chunkX + ", " + chunkZ, ex);
        }

        if (chunk == null && create) {
            chunk = this.getEmptyChunk(chunkX, chunkZ);
        } else if (chunk == null) {
            return null;
        }

        this.chunks.put(Level.chunkHash(chunkX, chunkZ), chunk);
        return chunk;
    }

    @Override
    public synchronized void close() {
        if (this.closed) {
            return;
        }

        try {
            if (this.autoCompactionTask != null) {
                this.autoCompactionTask.cancel();
                this.autoCompactionTask = null;
            }

            gcLock.lock();

            try {
                this.unloadChunksUnsafe(true);
            } catch (Exception e) {
                log.error("Error unloading chunks during close for: {}", this.getName(), e);
            }
            this.closed = true;
            this.level = null;
            this.executor.shutdown();
            boolean drained = false;
            try {
                drained = this.executor.awaitTermination(10, TimeUnit.MINUTES);
                if (!drained) {
                    log.warn("LevelDB executor did not terminate in time, forcing shutdown for: {}", this.getName());
                    java.util.List<Runnable> droppedTasks = this.executor.shutdownNow();
                    if (!droppedTasks.isEmpty()) {
                        log.warn("Dropped {} pending tasks during forced shutdown", droppedTasks.size());
                    }
                    if (!this.executor.awaitTermination(5, TimeUnit.SECONDS)) {
                        log.error("LevelDB executor did not terminate even after forced shutdown for: {}", this.getName());
                    }
                }
            } catch (InterruptedException e) {
                this.executor.shutdownNow();
            }
            // executor 正常排空后兜底落盘残余槽位(理论上应为空);排空超时说明写路径已卡死,再写只会再挂,跳过并告警
            // After a clean drain, flush any leftover slots (should be empty in theory); on drain timeout the write path is wedged — more writes would wedge too, so skip and warn
            if (drained) {
                for (Long hash : new ArrayList<>(this.pendingWrites.keySet())) {
                    this.commitPendingWrite(hash);
                }
            } else if (!this.pendingWrites.isEmpty()) {
                log.warn("{} pending chunk writes could not be flushed before close for: {}", this.pendingWrites.size(), this.getName());
            }
        } finally {
            // 关库前拿写锁,与 in-flight 异步读(读锁)互斥,防止在 db.get() 进行中释放 db 造成原生崩溃。
            // closed 已置 true,新读会在读锁内复检时放弃;正常路径 Level.close() 已先排空 executor,此处 tryLock 立即成功。
            // 有界 tryLock:若某读卡在不可中断 I/O,超时后仍关库(与修复 #1 的有界排空一致,绝不永久挂起)
            // Acquire the write lock before closing the db, excluding in-flight async reads (read lock) so db is never freed mid db.get() (native crash).
            // closed is already true, so new reads bail on the in-lock re-check; on the normal path Level.close() drained the executor first, so tryLock succeeds immediately.
            // Bounded tryLock: if a read is wedged in uninterruptible I/O, close the db anyway after the timeout (matching fix #1's bounded drain — never hang forever)
            boolean dbLocked = false;
            try {
                dbLocked = this.dbReadCloseLock.writeLock().tryLock(5, TimeUnit.SECONDS);
                if (!dbLocked) {
                    log.warn("An async chunk read did not finish before close for: {}; closing database anyway", this.getName());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            try {
                this.db.close();
            } catch (IOException e) {
                log.error("Can not close database: {}", this.getName(), e);
            } finally {
                if (dbLocked) {
                    this.dbReadCloseLock.writeLock().unlock();
                }
                this.gcLock.unlock();
            }
        }
    }

    @Override
    public String getPath() {
        return path;
    }

    @Override
    public String getGenerator() {
        return this.levelData.getString("generatorName");
    }

    public Server getServer() {
        return this.level.getServer();
    }

    @Override
    public Level getLevel() {
        return level;
    }

    @Override
    public String getName() {
        return this.levelData.getString("LevelName");
    }

    @Override
    public boolean isRaining() {
        return this.levelData.getFloat("rainLevel") > 0;
    }

    @Override
    public void setRaining(boolean raining) {
        this.levelData.putFloat("rainLevel", raining ? 1.0f : 0);
    }

    @Override
    public int getRainTime() {
        return this.levelData.getInt("rainTime");
    }

    @Override
    public void setRainTime(int rainTime) {
        this.levelData.putInt("rainTime", rainTime);
    }

    @Override
    public boolean isThundering() {
        return this.levelData.getFloat("lightningLevel") > 0;
    }

    @Override
    public void setThundering(boolean thundering) {
        this.levelData.putFloat("lightningLevel", thundering ? 1.0f : 0);
    }

    @Override
    public int getThunderTime() {
        return this.levelData.getInt("lightningTime");
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.levelData.putInt("lightningTime", thunderTime);
    }

    @Override
    public long getCurrentTick() {
        return this.levelData.getLong("currentTick");
    }

    @Override
    public void setCurrentTick(long currentTick) {
        this.levelData.putLong("currentTick", currentTick);
    }

    @Override
    public long getTime() {
        return this.levelData.getLong("Time");
    }

    @Override
    public void setTime(long value) {
        this.levelData.putLong("Time", value);
    }

    @Override
    public long getSeed() {
        if (this.cachedSeed == null) {
            this.cachedSeed = this.levelData.getLong("RandomSeed");
        }
        return this.cachedSeed;
    }

    @Override
    public void setSeed(long value) {
        this.cachedSeed = null;
        this.levelData.putLong("RandomSeed", value);
    }

    @Override
    public Vector3 getSpawn() {
        if (this.spawn == null) {
            this.spawn = new Vector3(this.levelData.getInt("SpawnX"), this.levelData.getInt("SpawnY"), this.levelData.getInt("SpawnZ"));
        }
        return this.spawn;
    }

    @Override
    public void setSpawn(Vector3 pos) {
        this.levelData.putInt("SpawnX", (int) pos.x);
        this.levelData.putInt("SpawnY", (int) pos.y);
        this.levelData.putInt("SpawnZ", (int) pos.z);
        this.spawn = pos;
    }

    @Override
    public GameRules getGamerules() {
        GameRules rules = GameRules.getDefault();
        rules.readBedrockNBT(this.levelData);
        return rules;
    }

    public void setLevelData(CompoundTag levelData, GameRules gameRules) {
        this.levelData = levelData;

        this.setGameRules(gameRules);
    }

    @Override
    public void setGameRules(GameRules rules) {
        rules.writeBedrockNBT(this.levelData);
    }

    private int lastPosition = 0;

    @Override
    public void doGarbageCollection() {
        //leveldb不需要回收regions
    }

    public CompoundTag getLevelData() {
        return levelData;
    }

    @Override
    public void updateLevelName(String name) {
        if (!this.getName().equals(name)) {
            this.levelData.putString("LevelName", name);
        }
    }

    @Override
    public int getMaximumLayer() {
        return 1;
    }

    @Override
    public int getMinBlockY() {
        return this.level.getDimensionData().getMinHeight();
    }

    @Override
    public int getMaxBlockY() {
        return this.level.getDimensionData().getMaxHeight();
    }

    protected static BlockVector3 deserializeExtraDataKey(int chunkVersion, int key) {
        return chunkVersion >= 3 ? new BlockVector3((key >> 12) & 0xf, key & 0xff, (key >> 8) & 0xf)
                // pre-1.0, 7 bits were used because the build height limit was lower
                : new BlockVector3((key >> 11) & 0xf, key & 0x7f, (key >> 7) & 0xf);
    }

    protected StateBlockStorage[] deserializeLegacyExtraData(int chunkX, int chunkZ, int chunkVersion) {
        byte[] extraRawData = this.db.get(BLOCK_EXTRA_DATA.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (extraRawData == null || extraRawData.length == 0) {
            return null;
        }

        StateBlockStorage[] extraDataLayers = new StateBlockStorage[16];
        BinaryStream stream = new BinaryStream(extraRawData);
        int count = stream.getLInt();
        for (int i = 0; i < count; i++) {
            int posKey = stream.getLInt();
            int fullBlock = stream.getLShort();

            int blockId = fullBlock & 0xff;
            int blockData = (fullBlock >> 8) & 0xf;

            BlockVector3 pos = deserializeExtraDataKey(chunkVersion, posKey);
            int chunkY = pos.y >> 4;
            StateBlockStorage storage = extraDataLayers[chunkY];
            if (storage == null) {
                storage = new StateBlockStorage();
                extraDataLayers[chunkY] = storage;
            }
            storage.set(pos.x, pos.y & 0xF, pos.z, (blockId << Block.DATA_BITS) | blockData);
        }
        return extraDataLayers;
    }

    protected void loadBlockTickingQueue(byte[] data, boolean tickingQueueTypeIsRandom) {
        for (ParsedTickingEntry entry : parseBlockTickingQueue(data)) {
            // 随机刻队列当前未启用调度,与历史行为保持一致 / Random-tick queue scheduling stays disabled, matching legacy behavior
            if (tickingQueueTypeIsRandom) {
                continue;
            }
            entry.applyNow(this.level);
        }
    }

    /**
     * 解码阶段(可能在异步线程)解析 ticking 数据为延迟条目,不触碰 Level;挂载时由 {@link BaseFullChunk#initChunk()} 回放
     * <p>
     * Parse ticking data into deferred entries at decode time (potentially off-thread) without touching Level; replayed on mount by {@link BaseFullChunk#initChunk()}
     */
    protected void loadBlockTickingQueueDeferred(byte[] data, boolean tickingQueueTypeIsRandom, List<BaseFullChunk.PendingBlockUpdate> sink) {
        if (tickingQueueTypeIsRandom) {
            return;
        }
        for (ParsedTickingEntry entry : parseBlockTickingQueue(data)) {
            BaseFullChunk.PendingBlockUpdate pending = entry.toPending();
            if (pending != null) {
                sink.add(pending);
            }
        }
    }

    /**
     * 纯解析 ticking NBT:解析方块身份与计时,不设置坐标/Level 字段,不调度;线程安全(只读全局映射表)
     * <p>
     * Pure parse of ticking NBT: resolves block identity and timing without setting position/Level or scheduling; thread-safe (reads global mappings only)
     */
    private List<ParsedTickingEntry> parseBlockTickingQueue(byte[] data) {
        NbtMap ticks;
        try (NBTInputStream reader = NbtUtils.createReaderLE(new ByteBufInputStream(Unpooled.wrappedBuffer(data)))) {
            ticks = (NbtMap) reader.readTag();
        } catch (IOException e) {
            throw new ChunkException("Corrupted block ticking data", e);
        }

        int currentTick = ticks.getInt("currentTick");
        List<ParsedTickingEntry> entries = new ArrayList<>();
        for (NbtMap nbtMap : ticks.getList("tickList", NbtType.COMPOUND)) {
            Block block = null;

            NbtMap state = nbtMap.getCompound("blockState");
            //noinspection ResultOfMethodCallIgnored
            state.hashCode();
            if (state.containsKey("name")) {
                BlockStateSnapshot blockState = BlockStateMapping.get().getStateUnsafe(state);
                if (blockState == null) {
                    NbtMap updatedState = BlockStateMapping.get().updateVanillaState(state);
                    blockState = BlockStateMapping.get().getUpdatedOrCustom(state, updatedState);
                }
                block = Block.get(blockState.getLegacyId(), blockState.getLegacyData());
            } else if (nbtMap.containsKey("tileID")) {
                block = Block.get(nbtMap.getByte("tileID") & 0xff);
            }

            if (block == null) {
                log.debug("Unavailable block ticking entry skipped: {}", nbtMap);
                continue;
            }
            int x = nbtMap.getInt("x");
            int y = nbtMap.getInt("y");
            int z = nbtMap.getInt("z");
            int delay = (int) (nbtMap.getLong("time") - currentTick);
            int priority = nbtMap.getInt("p"); // Nukkit only

            entries.add(new ParsedTickingEntry(block, x, y, z, delay, priority));
        }
        return entries;
    }

    /**
     * 解析结果:同步路径立即调度,异步路径转 {@link BaseFullChunk.PendingBlockUpdate}
     * <p>
     * Parsed result: applied immediately on the sync path, or converted to a {@link BaseFullChunk.PendingBlockUpdate} on the async path
     */
    private static final class ParsedTickingEntry {
        final Block block;
        final int x;
        final int y;
        final int z;
        final int delay;
        final int priority;

        ParsedTickingEntry(Block block, int x, int y, int z, int delay, int priority) {
            this.block = block;
            this.x = x;
            this.y = y;
            this.z = z;
            this.delay = delay;
            this.priority = priority;
        }

        void applyNow(Level level) {
            Block block = this.block;
            block.x = this.x;
            block.y = this.y;
            block.z = this.z;
            block.level = level;
            level.scheduleUpdate(block, block, this.delay, this.priority, false);
        }

        BaseFullChunk.PendingBlockUpdate toPending() {
            return new BaseFullChunk.PendingBlockUpdate(this.block, this.x, this.y, this.z, this.delay, this.priority);
        }
    }

    @Nullable
    protected NbtMap saveBlockTickingQueue(Collection<BlockUpdateEntry> entries, long currentTick) {
        return this.saveBlockTickingQueue(entries, null, currentTick);
    }

    @Nullable
    protected NbtMap saveBlockTickingQueue(Collection<BlockUpdateEntry> entries,
                                           Collection<BaseFullChunk.PendingBlockUpdate> deferredEntries,
                                           long currentTick) {
        ArrayList<NbtMap> list = new ArrayList<>();
        if (entries != null) {
            for (BlockUpdateEntry entry : entries) {
                Block block = entry.block;

                NbtMap blockTag = BlockStateMapping.get().getBlockStateFromFullId(block.getFullId()).getVanillaState();
                Vector3 pos = entry.pos;
                int priority = entry.priority;

                NbtMapBuilder tag = NbtMap.builder()
                        .putInt("x", pos.getFloorX())
                        .putInt("y", pos.getFloorY())
                        .putInt("z", pos.getFloorZ())
                        .putCompound("blockState", blockTag)
                        .putLong("time", entry.delay - currentTick);

                if (priority != 0) {
                    tag.putInt("p", priority); // Nukkit only
                }

                list.add(tag.build());
            }
        }

        if (deferredEntries != null) {
            for (BaseFullChunk.PendingBlockUpdate entry : deferredEntries) {
                NbtMap blockTag = BlockStateMapping.get().getBlockStateFromFullId(entry.getBlock().getFullId()).getVanillaState();
                NbtMapBuilder tag = NbtMap.builder()
                        .putInt("x", entry.getX())
                        .putInt("y", entry.getY())
                        .putInt("z", entry.getZ())
                        .putCompound("blockState", blockTag)
                        .putLong("time", entry.getDelay());
                if (entry.getPriority() != 0) {
                    tag.putInt("p", entry.getPriority());
                }
                list.add(tag.build());
            }
        }

        return list.isEmpty() ? null : NbtMap.builder()
                .putInt("currentTick", 0)
                .putList("tickList", NbtType.COMPOUND, list).build();
    }

    public void forEachChunks(Function<FullChunk, Boolean> action) {
        forEachChunks(action, false);
    }

    public void forEachChunks(Function<FullChunk, Boolean> action, boolean skipCorrupted) {
        try (DBIterator iter = db.iterator()) {
            Set<Long> seenChunks = new HashSet<>();
            while (iter.hasNext()) {
                Entry<byte[], byte[]> entry = iter.next();
                byte[] key = entry.getKey();
                if (!isChunkVersionKeyForDimension(key, this.level.getDimensionData().getDimensionId())) {
                    continue;
                }

                int chunkX = Binary.readLInt(key);
                int chunkZ = Binary.readLInt(key, 4);
                long index = Level.chunkHash(chunkX, chunkZ);
                if (!seenChunks.add(index)) {
                    continue;
                }
                BaseFullChunk chunk = this.chunks.get(index);
                if (chunk == null) {
                    try {
                        chunk = this.readChunk(chunkX, chunkZ);
                    } catch (Exception e) {
                        if (!skipCorrupted) {
                            throw e;
                        }
                        log.error("Skipped corrupted chunk {} {}", chunkX, chunkZ, e);
                        continue;
                    }
                }

                if (chunk == null) {
                    continue;
                }

                if (!action.apply(chunk)) {
                    break;
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("iteration failed", e);
        }
    }

    static boolean isChunkVersionKeyForDimension(byte[] key, int currentDimension) {
        if (key.length != 9 && key.length != 13) {
            return false;
        }

        boolean hasDimension = key.length == 13;
        byte type = key[hasDimension ? 12 : 8];
        if (type != VERSION.getCode() && type != VERSION_OLD.getCode()) {
            return false;
        }
        if (!hasDimension) {
            return currentDimension == 0;
        }
        return Binary.readLInt(key, 8) == currentDimension;
    }

    private class AutoCompaction implements Runnable {
        @Override
        public void run() {
            if (!level.isAutoCompaction() || !canRun()) {
                return;
            }

            log.debug("Running AutoCompaction... ({})", path);
            boolean locked = false;
            try {
                locked = gcLock.tryLock(500, TimeUnit.MILLISECONDS);
                if (!locked) {
                    return;
                }

                if (!canRun()) {
                    return;
                }

                AtomicInteger count = new AtomicInteger();
                forEachChunks(chunk -> {
                    //TODO: chunk.compressBiomes();
                    boolean next;
                    if (chunk.compress()) {
                        chunk.setChanged();

                        count.incrementAndGet();

                        next = canRun();
                        if (next) {
//                            writeChunk((LevelDbChunk) chunk, true, true); //TODO: save subchunks
                        }
                    } else {
                        next = canRun();
                    }
                    return next;
                }, true);
                log.debug("{} chunks have been compressed ({})", count, path);
            } catch (InterruptedException e) {
                log.debug("AutoCompaction interrupted", e);
            } finally {
                if (locked) {
                    gcLock.unlock();
                }
            }
        }

        private boolean canRun() {
            return !closed && level != null && level.getPlayers().isEmpty();
        }
    }

    static {
        log.info("native LevelDB provider: {}", Server.getInstance().useNativeLevelDB && LevelDB.PROVIDER.isNative());
    }
}
