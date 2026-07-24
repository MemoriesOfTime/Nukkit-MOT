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
    // 读锁保护异步读，写锁保护 DB 关闭。/ Read lock guards reads; write lock guards DB close.
    private final ReadWriteLock dbReadCloseLock = new ReentrantReadWriteLock();
    private final ExecutorService executor;

    // 异步迁移任务；完成值 true 表示至少迁移一个 chunk，需主线程收尾 levelData。null 表示无需迁移或已收尾。
    // / Async migration; completed value true means ≥1 chunk migrated and main thread must finalize levelData. null = none/finalized.
    private volatile CompletableFuture<Boolean> migrationFuture;

    // 每区块单写槽：新 batch 覆盖旧 batch，读取前先落盘。
    // One slot per chunk: newest batch wins and commits before reads.
    private final ConcurrentHashMap<Long, PendingWrite> pendingWrites = new ConcurrentHashMap<>();
    private volatile boolean pendingWriteBacklogWarned;

    private static final int MAX_PENDING_WRITE_RETRIES = 3;

    // 主线程收尾等待迁移的最大时长；超时则下个 tick 再试。/ Max wait on finalization; on timeout retry next tick.
    private static final long MIGRATION_AWAIT_TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(5);

    // 关闭超时参数；包内可变以便测试注入。/ Mutable close timeouts for tests.
    long closeDrainTimeoutMillis = TimeUnit.MINUTES.toMillis(10);
    long closeSweepLockTimeoutMillis = TimeUnit.SECONDS.toMillis(5);
    long closeSweepBudgetMillis = TimeUnit.SECONDS.toMillis(60);
    long databaseCloseTimeoutMillis = TimeUnit.SECONDS.toMillis(5);

    // 字段仅在持有 lock 时读写 / Fields are read and written only while holding lock
    static final class PendingWrite {
        final ReentrantLock lock = new ReentrantLock();
        WriteBatch batch;
        long changeSnapshot;
        LevelDBChunk chunkRef;
        int retries;
        Throwable failure;
    }

    private enum PendingWriteCommit {
        COMMITTED,
        RETRY,
        FAILED
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

        this.gcLock = new ReentrantLock();

        ThreadFactoryBuilder builder = new ThreadFactoryBuilder();
        builder.setNameFormat("LevelDB Executor for " + this.getName());
        builder.setUncaughtExceptionHandler((thread, ex) -> Server.getInstance().getLogger().error("Exception in " + thread.getName(), ex));
        this.executor = Executors.newSingleThreadExecutor(builder.build());

        // 迁移丢给 executor 异步执行，避免动态加载世界时阻塞主线程。/ Off-thread to avoid blocking on dynamic world load.
        this.migrationFuture = this.migrateLegacyNukkitFinalizationStatesAsync();

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

    /**
     * 构造期做廉价短路检查，未命中则把昂贵的全表扫描与 DB 写入异步交给 executor；executor 绝不触碰 levelData。
     * <p>
     * Cheap short-circuit at construction; otherwise schedules the full-table scan + DB write on the
     * executor, which never touches levelData.
     */
    private CompletableFuture<Boolean> migrateLegacyNukkitFinalizationStatesAsync() {
        byte[] encodingMarker = this.db.get(FINALIZATION_STATE_ENCODING_KEY);
        if (Arrays.equals(encodingMarker, FINALIZATION_STATE_ENCODING_BEDROCK)) {
            return null;
        }
        int storageVersion = this.levelData.getInt("StorageVersion");
        if (storageVersion <= 0 || storageVersion >= CURRENT_STORAGE_VERSION) {
            return null;
        }

        return CompletableFuture.supplyAsync(this::doMigrateLegacyNukkitFinalizationStates, this.executor);
    }

    /**
     * 迁移工作体，在 executor 线程仅操作 DB；返回 true 触发主线程写 StorageVersion + saveLevelData。
     * <p>
     * Migration body; runs on the executor touching the DB only and returns true to trigger the main
     * thread to write StorageVersion + saveLevelData.
     */
    private boolean doMigrateLegacyNukkitFinalizationStates() {
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
                return false;
            }

            writeBatch.put(FINALIZATION_STATE_ENCODING_KEY, FINALIZATION_STATE_ENCODING_BEDROCK);
            this.db.write(writeBatch);
        } catch (IOException e) {
            throw new RuntimeException("Unable to migrate legacy Nukkit LevelDB finalization states for " + this.path, e);
        }

        // 用 path 而非 getName()：executor 线程上 getName() 会读非线程安全的 levelData。/ path avoids levelData read off-thread.
        log.info("Migrated {} legacy Nukkit LevelDB finalization states for {}", migrated, this.path);
        return true;
    }

    /**
     * 阻塞至异步迁移结束（或未启动时立即返回）；仅作 DB 屏障，绝不触碰 levelData，任意线程可调用。
     * <p>
     * Blocks until the async migration finishes (or returns at once if never started); a pure DB
     * barrier that never touches levelData, safe from any thread.
     */
    private void awaitMigration() {
        CompletableFuture<Boolean> future = this.migrationFuture;
        if (future == null) {
            return;
        }
        try {
            future.join();
        } catch (CompletionException e) {
            // 迁移失败不阻塞读路径，降级为旧值。/ Failure must not block reads; fall back to legacy values.
            log.error("Legacy Nukkit finalization state migration failed for {}; reads will use legacy values", this.path, e.getCause());
            this.migrationFuture = null;
        }
        // 不置 null：join 仅做屏障，收尾交给主线程。/ Barrier only; finalization is the main thread's job.
    }

    /**
     * 主线程独占收尾：迁移成功且未收尾时写 StorageVersion 并持久化 level.dat。
     * 仅由主线程调用（{@link Level#doTick} 起始处与 {@link #close} 兜底）；异步读线程改用 {@link #awaitMigration()}。
     * <p>
     * Main-thread-exclusive finalization: writes StorageVersion and persists level.dat when the
     * migration succeeded and has not been finalized. Main thread only (head of {@link Level#doTick}
     * and {@link #close}); async readers use {@link #awaitMigration()}.
     */
    @Override
    public void finalizeMigrationIfDone() {
        CompletableFuture<Boolean> future = this.migrationFuture;
        if (future == null) {
            return;
        }
        Boolean migrated;
        try {
            migrated = future.get(MIGRATION_AWAIT_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            // 卡住的迁移不无限阻塞主线程，记日志后下个 tick 再试。/ Don't block the main thread; retry next tick.
            log.warn("Legacy Nukkit finalization state migration still running after {}ms for {}; deferring finalization", MIGRATION_AWAIT_TIMEOUT_MILLIS, this.path);
            return;
        } catch (ExecutionException e) {
            log.error("Legacy Nukkit finalization state migration failed for {}; reads will use legacy values", this.path, e.getCause());
            this.migrationFuture = null;
            return;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("Interrupted while waiting for migration finalization for {}", this.path);
            return;
        }
        this.migrationFuture = null;

        if (Boolean.TRUE.equals(migrated)) {
            this.levelData.putInt("StorageVersion", CURRENT_STORAGE_VERSION);
            this.saveLevelData();
        }
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
            // async-chunks 同时控制保存。/ async-chunks also controls saving.
            if (Server.getInstance().asyncChunkSending) {
                // 仅复用匹配 changes 的 batch；卸载时释放 chunkRef。
                // Reuse only a batch matching changes; release chunkRef on unload.
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
                // 回退到同步保存前先排空。/ Drain before synchronous fallback.
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
            // future 仅在本 batch 或更新 batch 落盘后完成。/ Future completes after durable data.
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

        // 同步保存也经写槽，避免旧 batch 后落盘。/ Sync saves use the slot to preserve order.
        long hash = Level.chunkHash(chunkX, chunkZ);
        this.stagePendingWrite(hash, chunkX, chunkZ, chunk, true);
        this.commitPendingWrite(hash);
    }

    /**
     * 序列化并替换槽内 batch；{@code keepChunkRef} 控制是否保留区块引用。
     * Serializes and replaces the staged batch; {@code keepChunkRef} controls chunk retention.
     */
    private void stagePendingWrite(long hash, int chunkX, int chunkZ, LevelDBChunk chunk, boolean keepChunkRef) {
        // 写前等待迁移完成，避免与其 DB 写入并发。/ Await migration before writing to avoid racing its writes.
        this.awaitMigration();
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
                pw.failure = null;
            } finally {
                pw.lock.unlock();
            }
            return pw;
        });
        this.warnOnPendingWriteBacklog();
    }

    /**
     * 提交最新槽位；同一屏障内最多重试 {@value MAX_PENDING_WRITE_RETRIES} 次。
     * Commits the latest slot with bounded retries in one barrier.
     */
    private void commitPendingWrite(long hash) {
        for (;;) {
            PendingWrite pw = this.pendingWrites.get(hash);
            if (pw == null) {
                return;
            }
            PendingWriteCommit result;
            Throwable failure;
            pw.lock.lock();
            try {
                if (this.pendingWrites.get(hash) != pw) {
                    continue;
                }
                result = this.commitPendingWriteLocked(hash, pw);
                failure = pw.failure;
            } finally {
                pw.lock.unlock();
            }
            if (result == PendingWriteCommit.RETRY) {
                // 重试不越过提交屏障。/ Retries stay inside the commit barrier.
                continue;
            }
            if (result == PendingWriteCommit.FAILED) {
                throw new DBException("Failed to commit chunk at " + Level.getHashX(hash) + ", " + Level.getHashZ(hash), failure);
            }
            this.removeEmptyPendingWrite(hash, pw);
            if (this.pendingWrites.get(hash) == null) {
                return;
            }
        }
    }

    /**
     * 限时提交；锁超时或写失败返回 {@code false}。
     * Bounded commit; returns {@code false} on lock timeout or write failure.
     */
    private boolean tryCommitPendingWrite(long hash, long lockTimeoutMillis) {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(lockTimeoutMillis);
        for (;;) {
            PendingWrite pw = this.pendingWrites.get(hash);
            if (pw == null) {
                return true;
            }
            long remainingNanos = deadline - System.nanoTime();
            if (remainingNanos <= 0L) {
                return false;
            }
            PendingWriteCommit result;
            boolean acquired;
            try {
                acquired = pw.lock.tryLock(remainingNanos, TimeUnit.NANOSECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
            if (!acquired) {
                return false;
            }
            try {
                if (this.pendingWrites.get(hash) != pw) {
                    continue;
                }
                result = this.commitPendingWriteLocked(hash, pw);
            } finally {
                pw.lock.unlock();
            }
            if (result == PendingWriteCommit.RETRY) {
                try {
                    this.commitPendingWrite(hash);
                    return this.pendingWrites.get(hash) == null;
                } catch (DBException e) {
                    return false;
                }
            }
            if (result == PendingWriteCommit.FAILED) {
                return false;
            }
            this.removeEmptyPendingWrite(hash, pw);
            if (this.pendingWrites.get(hash) == null) {
                return true;
            }
        }
    }

    /**
     * 提交 batch；调用方须持有 {@code pw.lock}。/ Commits a batch with {@code pw.lock} held.
     */
    private PendingWriteCommit commitPendingWriteLocked(long hash, PendingWrite pw) {
        PendingWriteCommit result = PendingWriteCommit.COMMITTED;
        WriteBatch batch = pw.batch;
        if (batch != null) {
            pw.batch = null;
            try {
                this.db.write(batch);
                pw.failure = null;
                if (pw.chunkRef != null) {
                    pw.chunkRef.clearChangesIfUnmodified(pw.changeSnapshot);
                }
                closeBatchQuietly(batch, this.getName());
            } catch (Exception e) {
                if (pw.retries < MAX_PENDING_WRITE_RETRIES) {
                    pw.retries++;
                    pw.batch = batch;
                    pw.failure = e;
                    result = PendingWriteCommit.RETRY;
                    log.warn("Chunk write failed for {} at {}, {} (retry {}/{})", this.getName(),
                            Level.getHashX(hash), Level.getHashZ(hash), pw.retries, MAX_PENDING_WRITE_RETRIES, e);
                } else {
                    // 保留 batch 并失败屏障，供后续重试。/ Retain the batch and fail the barrier.
                    pw.batch = batch;
                    pw.failure = e;
                    result = PendingWriteCommit.FAILED;
                    log.error("Chunk write remains pending for {} at {}, {} after {} retries", this.getName(),
                            Level.getHashX(hash), Level.getHashZ(hash), MAX_PENDING_WRITE_RETRIES, e);
                }
            }
        }
        return result;
    }

    private void removeEmptyPendingWrite(long hash, PendingWrite pw) {
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
        Runnable commit = () -> {
            try {
                this.commitPendingWrite(hash);
            } catch (DBException ignored) {
                // 失败已记录；batch 留待重试。/ Failure logged; batch remains pending.
            }
        };
        try {
            this.executor.execute(commit);
        } catch (RejectedExecutionException e) {
            // executor 已关闭时同步兜底。/ Commit inline after executor shutdown.
            commit.run();
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

    /** 挂起的区块写数。/ Number of pending chunk writes. */
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
                // 先提交挂起写，避免关闭时重复序列化。/ Commit pending data before close-time save.
                this.commitPendingWrite(Level.chunkHash(chunk.getX(), chunk.getZ()));
                // 落盘后仍脏才同步重存。/ Sync-save only if still dirty.
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
        // 读锁允许并发读，并与 DB 关闭互斥。/ Read lock permits concurrent reads and excludes DB close.
        this.dbReadCloseLock.readLock().lock();
        try {
            // 持锁后复检关闭状态。/ Recheck closed state under the lock.
            if (this.closed) {
                return null;
            }
            // 固定本次解码的 level 引用。/ Snapshot level for this decode.
            Level levelSnapshot = this.level;
            if (levelSnapshot == null) {
                return null;
            }
            // 读前等待迁移完成，避免与其 DB 写入并发。/ Await migration before reading to avoid racing its writes.
            this.awaitMigration();
            // 读取前提交挂起写。/ Commit pending data before reading.
            this.commitPendingWrite(Level.chunkHash(chunkX, chunkZ));
            // 仅解码；缓存挂载与 ticking 留给主线程。/ Decode only; mount and ticking stay on the main thread.
            return this.readChunkDeferred(chunkX, chunkZ, levelSnapshot);
        } finally {
            this.dbReadCloseLock.readLock().unlock();
        }
    }

    /**
     * 异步解码；ticking 延迟到 {@link BaseFullChunk#initChunk()}。
     * Async decode with ticking deferred to {@link BaseFullChunk#initChunk()}.
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

        // 解析 ticking，由主线程挂载时调度。/ Parse ticking; schedule it during main-thread mount.
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
        // 检查前提交挂起写。/ Commit pending data before checking.
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
        // 读前等待迁移完成，避免与其 DB 写入并发。/ Await migration before reading to avoid racing its writes.
        this.awaitMigration();
        // 读取前提交挂起写。/ Commit pending data before reading.
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

            // 关闭前等待迁移，确保其 DB 写入在卸载与 executor 终止前完成。/ Await migration before unload + executor shutdown.
            this.awaitMigration();
            // 关服兜底：迁移可能恰在 doTick 跑到前完成，此处理顺 levelData。/ Close fallback when migration finishes before doTick runs.
            this.finalizeMigrationIfDone();
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
                drained = this.executor.awaitTermination(this.closeDrainTimeoutMillis, TimeUnit.MILLISECONDS);
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
            // 排空超时后限时抢救 batch；跳过被卡写入占用的槽。
            // On drain timeout, flush within a budget and skip slots held by wedged writes.
            if (drained) {
                for (Long hash : new ArrayList<>(this.pendingWrites.keySet())) {
                    this.commitPendingWrite(hash);
                }
            } else if (!this.pendingWrites.isEmpty()) {
                log.warn("Executor drain timed out with {} pending chunk writes for: {}; attempting bounded inline flush", this.pendingWrites.size(), this.getName());
                long deadline = System.currentTimeMillis() + this.closeSweepBudgetMillis;
                int abandoned = 0;
                for (Long hash : new ArrayList<>(this.pendingWrites.keySet())) {
                    if (System.currentTimeMillis() > deadline
                            || !this.tryCommitPendingWrite(hash, this.closeSweepLockTimeoutMillis)) {
                        abandoned++;
                    }
                }
                if (abandoned > 0) {
                    log.warn("{} pending chunk writes abandoned during close for: {}", abandoned, this.getName());
                }
            }
        } finally {
            // daemon 串行等待读取并关闭 DB；当前线程仅限时等待。
            // A daemon excludes readers and closes DB; this thread waits with a timeout.
            CompletableFuture<Void> databaseClose = this.startDatabaseClose();
            try {
                databaseClose.get(this.databaseCloseTimeoutMillis, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Interrupted while waiting for database close for: {}; cleanup will continue in background", this.getName());
            } catch (TimeoutException e) {
                log.warn("Database close did not finish in time for: {}; cleanup will continue in background", this.getName());
            } catch (ExecutionException e) {
                log.error("Unexpected error closing database: {}", this.getName(), e.getCause());
            } finally {
                this.gcLock.unlock();
            }
        }
    }

    private CompletableFuture<Void> startDatabaseClose() {
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Thread cleanup = new Thread(() -> {
            this.dbReadCloseLock.writeLock().lock();
            try {
                this.closeDatabase();
                completion.complete(null);
            } catch (Throwable t) {
                completion.completeExceptionally(t);
            } finally {
                this.dbReadCloseLock.writeLock().unlock();
            }
        }, "LevelDB close for " + this.getName());
        cleanup.setDaemon(true);
        cleanup.start();
        return completion;
    }

    private void closeDatabase() {
        try {
            this.db.close();
        } catch (IOException e) {
            log.error("Can not close database: {}", this.getName(), e);
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
            // 随机刻调度保持禁用。/ Random-tick scheduling remains disabled.
            if (tickingQueueTypeIsRandom) {
                continue;
            }
            entry.applyNow(this.level);
        }
    }

    /**
     * 解析延迟 ticking，由 {@link BaseFullChunk#initChunk()} 回放。
     * Parses deferred ticking for replay by {@link BaseFullChunk#initChunk()}.
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
     * 仅解析 ticking NBT，不修改 Level。/ Parses ticking NBT without mutating Level.
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
     * 可立即调度或转为 {@link BaseFullChunk.PendingBlockUpdate}。
     * Applies immediately or converts to {@link BaseFullChunk.PendingBlockUpdate}.
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
        // 遍历前等待迁移，避免与其 iterator/write 并发。/ Await migration before iterating to avoid racing its iterator/write.
        this.awaitMigration();
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
