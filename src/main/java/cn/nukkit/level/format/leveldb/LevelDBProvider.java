package cn.nukkit.level.format.leveldb;

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
import cn.nukkit.utils.*;
import com.google.common.collect.ImmutableMap;
import io.netty.buffer.*;
import it.unimi.dsi.fastutil.ints.IntSet;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import lombok.extern.log4j.Log4j2;
import net.daporkchop.ldbjni.DBProvider;
import net.daporkchop.ldbjni.LevelDB;
import net.daporkchop.lib.natives.FeatureBuilder;
import org.cloudburstmc.nbt.*;
import org.iq80.leveldb.*;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.ref.WeakReference;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Function;

import static cn.nukkit.level.format.leveldb.LevelDBConstants.*;
import static cn.nukkit.level.format.leveldb.LevelDBKey.*;

@Log4j2
public class LevelDBProvider implements LevelProvider {

    private static final DBProvider JAVA_LDB_PROVIDER = (DBProvider) FeatureBuilder.create(LevelDBProvider.class).addJava("net.daporkchop.ldbjni.java.JavaDBProvider").build();

    protected final Long2ObjectMap<LevelDBChunk> chunks = new Long2ObjectOpenHashMap<>();
    protected final ThreadLocal<WeakReference<LevelDBChunk>> lastChunk = new ThreadLocal<>();

    protected final DB db;

    protected Level level;

    protected final String path;

    protected final CompoundTag levelData;

    protected volatile boolean closed;
    protected final Lock gcLock;

    protected boolean saveChunksOnClose = true;

    public LevelDBProvider(Level level, String path) {
        this.level = level;
        this.path = path;
        Path dirPath = Paths.get(path);
        try {
            Files.createDirectories(dirPath);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        try (InputStream stream = Files.newInputStream(dirPath.resolve("level.dat"))) {
            //noinspection ResultOfMethodCallIgnored
            stream.skip(8);
            this.levelData = NBTIO.read(stream, ByteOrder.LITTLE_ENDIAN);
        } catch (IOException e) {
            throw new LevelException("Invalid level.dat", e);
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

        gcLock = new ReentrantLock();
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
    public void requestChunkTask(IntSet protocols, int chunkX, int chunkZ) {
        LevelDBChunk chunk = this.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        if (this.getServer().asyncChunkSending) {
            final BaseChunk chunkClone = chunk.cloneForChunkSending();
            this.level.getAsyncChuckExecutor().execute(() -> {
                NetworkChunkSerializer.serialize(protocols, chunkClone, networkChunkSerializerCallback -> {
                    getLevel().asyncChunkRequestCallback(networkChunkSerializerCallback.getProtocolId(),
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
                this.getLevel().chunkRequestCallback(networkChunkSerializerCallback.getProtocolId(),
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
        LevelDBChunk chunk;
        WeakReference<LevelDBChunk> lastChunk = this.lastChunk.get();
        if (lastChunk != null) {
            chunk = lastChunk.get();
            if (chunk != null && chunk.getProvider() != null && chunk.getX() == chunkX && chunk.getZ() == chunkZ) {
                return chunk;
            }
        }

        long index = Level.chunkHash(chunkX, chunkZ);
        synchronized (this.chunks) {
            chunk = this.chunks.get(index);
            if (chunk != null) {
                this.lastChunk.set(new WeakReference<>(chunk));
            }
        }
        return chunk;
    }

    @Override
    public BaseFullChunk getLoadedChunk(long hash) {
        LevelDBChunk chunk;
        WeakReference<LevelDBChunk> lastChunk = this.lastChunk.get();
        if (lastChunk != null) {
            chunk = lastChunk.get();
            if (chunk != null && chunk.getProvider() != null && chunk.getIndex() == hash) {
                return chunk;
            }
        }

        synchronized (chunks) {
            chunk = this.chunks.get(hash);
            if (chunk != null) {
                this.lastChunk.set(new WeakReference<>(chunk));
            }
        }
        return chunk;
    }

    @Override
    public Map<Long, BaseFullChunk> getLoadedChunks() {
        synchronized (this.chunks) {
            return ImmutableMap.copyOf(chunks);
        }
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
        synchronized (this.chunks) {
            return this.chunks.containsKey(hash);
        }
    }

    @Override
    public void saveChunks() {
        synchronized (this.chunks) {
            for (LevelDBChunk chunk : this.chunks.values()) {
                if (chunk.getChanges() == 0) {
                    continue;
                }
                chunk.setChanged(false);
                this.saveChunk(chunk.getX(), chunk.getZ());
            }
        }
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ) {
        return this.loadChunk(chunkX, chunkZ, false);
    }

    @Override
    public boolean loadChunk(int chunkX, int chunkZ, boolean create) {
        LevelDBChunk chunk;
        WeakReference<LevelDBChunk> lastChunk = this.lastChunk.get();
        if (lastChunk != null) {
            chunk = lastChunk.get();
            if (chunk != null && chunk.getProvider() != null && chunk.getX() == chunkX && chunk.getZ() == chunkZ) {
                return true;
            }
        }

        long index = Level.chunkHash(chunkX, chunkZ);
        synchronized (this.chunks) {
            chunk = this.chunks.get(index);
            if (chunk != null) {
                this.lastChunk.set(new WeakReference<>(chunk));
                return true;
            }

            try {
                chunk = this.readChunk(chunkX, chunkZ);
            } catch (Exception e) {
                throw new ChunkException("corrupted chunk: " + chunkX + "," + chunkZ, e);
            }

            if (chunk == null && create) {
                chunk = LevelDBChunk.getEmptyChunk(chunkX, chunkZ, this);
            }

            if (chunk != null) {
                this.chunks.put(index, chunk);
                this.lastChunk.set(new WeakReference<>(chunk));
                return true;
            }
        }
        return false;
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
        if (finalized == null) {
            chunkBuilder.state(ChunkState.FINISHED);
        } else {
            chunkBuilder.state(ChunkState.values()[Unpooled.wrappedBuffer(finalized).readIntLE()]);
        }

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

        byte[] randomTickingData = this.db.get(PENDING_RANDOM_TICKS.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (randomTickingData != null && randomTickingData.length != 0) {
            loadBlockTickingQueue(randomTickingData, true);
        }

        LevelDBChunk chunk = chunkBuilder.build();

        if (chunkVersion <= 2) {
            chunk.setHeightmapOrBiomesDirty();
        }

        return chunk;
    }

    private void writeChunk(int chunkX, int chunkZ, FullChunk fullChunk) {
        if (!(fullChunk instanceof LevelDBChunk chunk)) {
            throw new ChunkException("Invalid Chunk class");
        }

        chunk.setX(chunkX);
        chunk.setZ(chunkZ);

        if (!chunk.isGenerated()) {
            return;
        }

        chunk.setChanged(false);

        try (WriteBatch writeBatch = this.db.createWriteBatch()) {
            writeBatch.put(VERSION.getKey(chunkX, chunkZ, this.getLevel().getDimensionData().getDimensionId()), CHUNK_VERSION_SAVE_DATA);

            chunk.ioLock.lock();

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

            writeBatch.put(STATE_FINALIZATION.getKey(chunkX, chunkZ, this.level.getDimensionData().getDimensionId()), Binary.writeLInt(chunk.getState().ordinal()));

            BlockEntitySerializer.saveBlockEntities(writeBatch, chunk);

            EntitySerializer.saveEntities(writeBatch, chunk);

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
            if (blockUpdateEntries != null && !blockUpdateEntries.isEmpty()) {
                NbtMap ticks = saveBlockTickingQueue(blockUpdateEntries, currentTick);
                if (ticks != null) {
                    ByteBuf byteBuf = ByteBufAllocator.DEFAULT.ioBuffer();
                    NBTOutputStream outputStream = NbtUtils.createWriterLE(new ByteBufOutputStream(byteBuf));
                    outputStream.writeTag(ticks);
                    writeBatch.put(pendingScheduledTicksKey, Utils.convertByteBuf2Array(byteBuf));
                } else {
                    writeBatch.delete(pendingScheduledTicksKey);
                }
            } else {
                writeBatch.delete(pendingScheduledTicksKey);
            }

           /* byte[] pendingRandomTicksKey = PENDING_RANDOM_TICKS.getKey(chunkX, chunkZ);
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

            this.db.write(writeBatch);
        } catch (IOException e) {
            throw new RuntimeException("Failed to save chunk", e);
        } finally {
            chunk.ioLock.unlock();
        }
    }

    @Override
    public boolean unloadChunk(int chunkX, int chunkZ) {
        return this.unloadChunk(chunkX, chunkZ, true);
    }

    @Override
    public boolean unloadChunk(int chunkX, int chunkZ, boolean safe) {
        long index = Level.chunkHash(chunkX, chunkZ);
        synchronized (this.chunks) {
            LevelDBChunk chunk = this.chunks.get(index);
            if (chunk != null && chunk.unload(false, safe)) {
                WeakReference<LevelDBChunk> lastChunk = this.lastChunk.get();
                if (lastChunk != null && lastChunk.get() == chunk) {
                    this.lastChunk.remove();
                }
                this.chunks.remove(index);
                return true;
            }
        }
        return false;
    }

    @Override
    public void saveChunk(int chunkX, int chunkZ) {
        if (this.isChunkLoaded(chunkX, chunkZ)) {
            this.saveChunk(chunkX, chunkZ, this.getChunk(chunkX, chunkZ));
        }
    }

    @Override
    public void saveChunk(int chunkX, int chunkZ, FullChunk chunk) {
        this.writeChunk(chunkX, chunkZ, chunk);
    }

    @Override
    public void unloadChunks() {
        this.unloadChunks(true);
    }

    public void unloadChunks(boolean save) {
        synchronized (this.chunks) {
            Iterator<LevelDBChunk> iter = this.chunks.values().iterator();
            while (iter.hasNext()) {
                iter.next().unload(save, false);
                iter.remove();
            }
        }
    }

    @Override
    public LevelDBChunk getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    @Override
    public LevelDBChunk getChunk(int chunkX, int chunkZ, boolean create) {
        LevelDBChunk chunk;
        WeakReference<LevelDBChunk> lastChunk = this.lastChunk.get();
        if (lastChunk != null) {
            chunk = lastChunk.get();
            if (chunk != null && chunk.getProvider() != null && chunk.getX() == chunkX && chunk.getZ() == chunkZ) {
                return chunk;
            }
        }

        long index = Level.chunkHash(chunkX, chunkZ);
        synchronized (this.chunks) {
            chunk = this.chunks.get(index);
            if (chunk != null) {
                this.lastChunk.set(new WeakReference<>(chunk));
                return chunk;
            }

            try {
                chunk = this.readChunk(chunkX, chunkZ);
            } catch (Exception e) {
                throw new ChunkException("corrupted chunk: " + chunkX + "," + chunkZ, e);
            }

            if (chunk == null && create) {
                chunk = LevelDBChunk.getEmptyChunk(chunkX, chunkZ, this);
            }

            if (chunk != null) {
                this.lastChunk.set(new WeakReference<>(chunk));
                this.chunks.put(index, chunk);
            }
        }
        return chunk;
    }

    @Override
    public BaseFullChunk getEmptyChunk(int chunkX, int chunkZ) {
        return LevelDBChunk.getEmptyChunk(chunkX, chunkZ, this);
    }

    public DB getDatabase() {
        return db;
    }

    @Override
    public void setChunk(int chunkX, int chunkZ, FullChunk chunk) {
        if (!(chunk instanceof LevelDBChunk)) {
            throw new ChunkException("Invalid Chunk class");
        }
        chunk.setProvider(this);
        chunk.setPosition(chunkX, chunkZ);
        long index = chunk.getIndex();

        synchronized (this.chunks) {
            LevelDBChunk oldChunk = this.chunks.get(index);
            if (!chunk.equals(oldChunk)) {
                if (oldChunk != null) {
                    oldChunk.unload(false, false);
                }

                LevelDBChunk newChunk = (LevelDBChunk) chunk;
                this.chunks.put(index, newChunk);
                this.lastChunk.set(new WeakReference<>(newChunk));
            }
        }
    }

    @SuppressWarnings("unused")
    public static LevelDBChunkSection createChunkSection(int chunkY) {
        return new LevelDBChunkSection(chunkY);
    }

    private boolean chunkExists(int chunkX, int chunkZ) {
        byte[] data = this.db.get(VERSION.getKey(chunkX, chunkZ, this.level.getDimension()));
        if (data == null || data.length == 0) {
            data = this.db.get(VERSION_OLD.getKey(chunkX, chunkZ, this.level.getDimension()));
        }
        return data != null && data.length != 0;
    }

    @Override
    public boolean isChunkGenerated(int chunkX, int chunkZ) {
        if (!this.chunkExists(chunkX, chunkZ)) {
            return false;
        }
        LevelDBChunk chunk = this.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            return false;
        }
        return chunk.isGenerated();
    }

    @Override
    public boolean isChunkPopulated(int chunkX, int chunkZ) {
        if (!this.chunkExists(chunkX, chunkZ)) {
            return false;
        }
        LevelDBChunk chunk = this.getChunk(chunkX, chunkZ, false);
        if (chunk == null) {
            return false;
        }
        return chunk.isPopulated();
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;

        try {
            gcLock.lock();

            this.unloadChunks(saveChunksOnClose);
            try {
                this.db.close();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            this.level = null;
        } finally {
            gcLock.unlock();
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
        return this.levelData.getLong("RandomSeed");
    }

    @Override
    public void setSeed(long value) {
        this.levelData.putLong("RandomSeed", value);
    }

    @Override
    public Vector3 getSpawn() {
        return new Vector3(this.levelData.getInt("SpawnX"), this.levelData.getInt("SpawnY"), this.levelData.getInt("SpawnZ"));
    }

    @Override
    public void setSpawn(Vector3 pos) {
        this.levelData.putInt("SpawnX", (int) pos.x);
        this.levelData.putInt("SpawnY", (int) pos.y);
        this.levelData.putInt("SpawnZ", (int) pos.z);
    }

    @Override
    public GameRules getGamerules() {
        GameRules rules = GameRules.getDefault();
        rules.readBedrockNBT(this.levelData);
        return rules;
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

    @Override
    public void doGarbageCollection(long time) {
        long start = System.currentTimeMillis();
        int maxIterations = this.chunks.size();
        if (lastPosition > maxIterations) lastPosition = 0;
        int i;
        synchronized (chunks) {
            Iterator<LevelDBChunk> iter = chunks.values().iterator();
            if (lastPosition != 0) {
                int tmpI = lastPosition;
                while (tmpI-- != 0 && iter.hasNext()) iter.next();
            }
            for (i = 0; i < maxIterations; i++) {
                if (!iter.hasNext()) {
                    iter = chunks.values().iterator();
                }
                if (!iter.hasNext()) break;
                BaseFullChunk chunk = iter.next();
                if (chunk == null) continue;
                if (chunk.isGenerated() && chunk.isPopulated()) {
                    chunk.compress();
                    if (System.currentTimeMillis() - start >= time) break;
                }
            }
        }
        lastPosition += i;
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
        BinaryStream stream = new BinaryStream();
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
            storage.set(pos, (blockId << Block.DATA_BITS) | blockData);
        }
        return extraDataLayers;
    }

    protected void loadBlockTickingQueue(byte[] data, boolean tickingQueueTypeIsRandom) {
        NbtMap ticks;
        try {
            ticks = (NbtMap) NbtUtils.createReaderLE(new ByteBufInputStream(Unpooled.wrappedBuffer(data))).readTag();
        } catch (IOException e) {
            throw new ChunkException("Corrupted block ticking data", e);
        }

        int currentTick = ticks.getInt("currentTick");
        for (NbtMap state : ticks.getList("tickList", NbtType.COMPOUND)) {
            Block block = null;
            //noinspection ResultOfMethodCallIgnored
            state.hashCode();

            if (state.containsKey("name")) {
                BlockStateSnapshot blockState = BlockStateMapping.get().getStateUnsafe(state);
                if (blockState == null) {
                    NbtMap updatedState = BlockStateMapping.get().updateVanillaState(state);
                    blockState = BlockStateMapping.get().getUpdatedOrCustom(state, updatedState);
                }
                block = blockState.getBlock();
            } else if (state.containsKey("tileID")) {
                block = Block.get(state.getByte("tileID") & 0xff);
            }

            if (block == null) {
                log.debug("Unavailable block ticking entry skipped: {}", state);
                continue;
            }
            block.x = state.getInt("x");
            block.y = state.getInt("y");
            block.z = state.getInt("z");
            block.level = level;

            int delay = (int) (state.getLong("time") - currentTick);
            int priority = state.getInt("p"); // Nukkit only

            if (!tickingQueueTypeIsRandom) {
                level.scheduleUpdate(block, block, delay, priority, false);
            }/* else {
                level.scheduleRandomUpdate(block, block, delay, priority, false);
            }*/
        }
    }

    @Nullable
    protected NbtMap saveBlockTickingQueue(Collection<BlockUpdateEntry> entries, long currentTick) {
        ArrayList<NbtMap> list = new ArrayList<>();
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

        return list.isEmpty() ? null : NbtMap.builder()
                .putInt("currentTick", 0)
                .putList("tickList", NbtType.COMPOUND, list).build();
    }

    public void forEachChunks(Function<FullChunk, Boolean> action) {
        forEachChunks(action, false);
    }

    public void forEachChunks(Function<FullChunk, Boolean> action, boolean skipCorrupted) {
        try (DBIterator iter = db.iterator()) {
            while (iter.hasNext()) {
                Entry<byte[], byte[]> entry = iter.next();
                byte[] key = entry.getKey();
                if (key.length != 9) {
                    continue;
                }

                byte type = key[8];
                if (/*type != NEW_VERSION.getCode() &&*/ type != VERSION_OLD.getCode()) {
                    continue;
                }

                int chunkX = Binary.readLInt(key);
                int chunkZ = Binary.readLInt(key, 4);
                long index = Level.chunkHash(chunkX, chunkZ);
                LevelDBChunk chunk;
                synchronized (this.chunks) {
                    chunk = this.chunks.get(index);
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

//                        if (chunk != null) {
//                            this.chunks.put(index, chunk);
//                        }
                    }
                }

                if (chunk == null) {
                    continue;
                }

                if (!action.apply(chunk)) {
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException("iteration failed", e);
        }
    }

    static {
        log.info("native LevelDB provider: {}", Server.getInstance().useNativeLevelDB && LevelDB.PROVIDER.isNative());
    }
}
