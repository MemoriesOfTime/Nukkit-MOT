package cn.nukkit.level.format.generic;

import cn.nukkit.Server;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.math.Vector3;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.ChunkException;
import cn.nukkit.utils.LevelException;
import com.google.common.collect.ImmutableMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.jetbrains.annotations.Nullable;

import java.io.*;
import java.lang.ref.WeakReference;
import java.nio.ByteOrder;
import java.nio.file.*;
import java.util.Iterator;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
@Log4j2
public abstract class BaseLevelProvider implements LevelProvider {

    protected static final String LEVEL_DAT = "level.dat";
    protected static final String LEVEL_DAT_BAK = "level.dat.bak";
    protected static final String REGION_DIR = "region";
    protected static final String DB_DIR = "db";

    protected Level level;
    protected final String path;
    protected final Path pathFastAccess;

    @Getter
    protected CompoundTag levelData;

    private Vector3 spawn;
    private Long cachedSeed;

    protected final AtomicReference<BaseRegionLoader> lastRegion = new AtomicReference<>();

    protected final Long2ObjectMap<BaseRegionLoader> regions = new Long2ObjectOpenHashMap<>();

    protected final ConcurrentMap<Long, BaseFullChunk> chunks = new ConcurrentHashMap<>();

    private final ThreadLocal<WeakReference<BaseFullChunk>> lastChunk = new ThreadLocal<>();

    public BaseLevelProvider(Level level, String path) throws IOException {
        this.level = level;
        this.path = path;
        this.pathFastAccess = Path.of(path);

        if (!Files.exists(this.pathFastAccess)) {
            Files.createDirectories(this.pathFastAccess);
        }

        this.levelData = loadLevelData();

        if (!this.levelData.contains("generatorName")) {
            this.levelData.putString("generatorName", Generator.getGenerator("DEFAULT").getSimpleName().toLowerCase(Locale.ROOT));
        }

        if (!this.levelData.contains("generatorOptions")) {
            this.levelData.putString("generatorOptions", "");
        }

        this.spawn = new Vector3(
                this.levelData.getInt("SpawnX"),
                this.levelData.getInt("SpawnY"),
                this.levelData.getInt("SpawnZ")
        );
    }

    protected boolean isAnvilWorld() {
        return Files.exists(pathFastAccess.resolve(REGION_DIR)) || !Files.exists(pathFastAccess.resolve(DB_DIR));
    }

    private CompoundTag loadLevelData() {
        CompoundTag data = tryLoadLevelDat(LEVEL_DAT);
        if (data == null) {
            data = tryLoadLevelDat(LEVEL_DAT_BAK);
        }

        if (data == null || !(data.get("Data") instanceof CompoundTag dataTag)) {
            throw new LevelException("Invalid level.dat");
        }

        return dataTag;
    }

    private CompoundTag tryLoadLevelDat(String fileName) {
        Path filePath = this.pathFastAccess.resolve(fileName);
        if (!Files.exists(filePath)) return null;

        try {
            if (isAnvilWorld()) {
                try (InputStream is = Files.newInputStream(filePath);
                     BufferedInputStream bis = new BufferedInputStream(is)) {
                    return NBTIO.readCompressed(bis, ByteOrder.BIG_ENDIAN);
                }
            } else {
                return NBTIO.readCompressed(Files.readAllBytes(filePath), ByteOrder.BIG_ENDIAN);
            }
        } catch (Exception e) {
            log.debug("Failed to load {}: {}", fileName, e.getMessage());
            return null;
        }
    }

    public abstract BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create);

    public int size() {
        return this.chunks.size();
    }

    @Override
    public void unloadChunks() {
        Iterator<BaseFullChunk> iterator = chunks.values().iterator();
        while (iterator.hasNext()) {
            BaseFullChunk chunk = iterator.next();

            if (chunk.hasChanged()) {
                this.saveChunk(chunk.getX(), chunk.getZ());
                chunk.setChanged(false);
            }

            if (chunk.unload(level.getAutoSave(), false)) {
                iterator.remove();
            }
        }
    }

    @Override
    public String getGenerator() {
        return this.levelData.getString("generatorName");
    }

    @Override
    public Map<String, Object> getGeneratorOptions() {
        return Map.of("preset", levelData.getString("generatorOptions"));
    }

    @Override
    public Map<Long, BaseFullChunk> getLoadedChunks() {
        return ImmutableMap.copyOf(chunks);
    }

    @Override
    public boolean isChunkLoaded(int X, int Z) {
        return isChunkLoaded(Level.chunkHash(X, Z));
    }

    public void putChunk(long index, BaseFullChunk chunk) {
        chunks.put(index, chunk);
    }

    @Override
    public boolean isChunkLoaded(long hash) {
        return this.chunks.containsKey(hash);
    }

    public BaseRegionLoader getRegion(int x, int z) {
        long index = Level.chunkHash(x, z);
        synchronized (regions) {
            return this.regions.get(index);
        }
    }

    protected static int getRegionIndexX(int chunkX) {
        return chunkX >> 5;
    }

    protected static int getRegionIndexZ(int chunkZ) {
        return chunkZ >> 5;
    }

    @Override
    public String getPath() {
        return path;
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
        return this.levelData.getBoolean("raining");
    }

    @Override
    public void setRaining(boolean raining) {
        this.levelData.putBoolean("raining", raining);
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
        return this.levelData.getBoolean("thundering");
    }

    @Override
    public void setThundering(boolean thundering) {
        this.levelData.putBoolean("thundering", thundering);
    }

    @Override
    public int getThunderTime() {
        return this.levelData.getInt("thunderTime");
    }

    @Override
    public void setThunderTime(int thunderTime) {
        this.levelData.putInt("thunderTime", thunderTime);
    }

    @Override
    public long getCurrentTick() {
        return this.levelData.getLong("Time");
    }

    @Override
    public void setCurrentTick(long currentTick) {
        this.levelData.putLong("Time", currentTick);
    }

    @Override
    public long getTime() {
        return this.levelData.getLong("DayTime");
    }

    @Override
    public void setTime(long value) {
        this.levelData.putLong("DayTime", value);
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
        return spawn;
    }

    @Override
    public void setSpawn(Vector3 pos) {
        this.levelData.putInt("SpawnX", (int) pos.x);
        this.levelData.putInt("SpawnY", (int) pos.y);
        this.levelData.putInt("SpawnZ", (int) pos.z);
        spawn = pos;
    }

    @Override
    public GameRules getGamerules() {
        GameRules rules = GameRules.getDefault();
        if (this.levelData.contains("GameRules")) {
            rules.readNBT(this.levelData.getCompound("GameRules"));
        }
        return rules;
    }

    @Override
    public void setGameRules(GameRules rules) {
        this.levelData.putCompound("GameRules", rules.writeNBT());
    }

    @Override
    public void doGarbageCollection() {
        int limit = (int) (System.currentTimeMillis() - 50);
        synchronized (regions) {
            if (regions.isEmpty()) {
                return;
            }

            ObjectIterator<BaseRegionLoader> iter = regions.values().iterator();
            while (iter.hasNext()) {
                BaseRegionLoader loader = iter.next();

                if (loader.lastUsed <= limit) {
                    try {
                        loader.close();
                    } catch (IOException e) {
                        throw new RuntimeException("Unable to close RegionLoader", e);
                    }
                    lastRegion.set(null);
                    iter.remove();
                }
            }
        }
    }

    @Override
    public void saveChunks() {
        for (BaseFullChunk chunk : this.chunks.values()) {
            if (chunk.hasChanged()) {
                this.saveChunk(chunk.getX(), chunk.getZ());
                chunk.setChanged(false);
            }
        }
    }

    @Override
    public void saveLevelData() {
        Path levelDatPath = this.pathFastAccess.resolve(LEVEL_DAT);
        Path levelDatBakPath = this.pathFastAccess.resolve(LEVEL_DAT_BAK);

        if (Files.exists(levelDatPath)) {
            try {
                Files.copy(levelDatPath, levelDatBakPath, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                Server.getInstance().getLogger().logException(e);
            }
        }

        Path tempPath;
        try {
            tempPath = Files.createTempFile(this.pathFastAccess, "level", ".dat.tmp");
        } catch (IOException e) {
            throw new RuntimeException("Failed to create temporary file for level.dat", e);
        }

        try (OutputStream os = Files.newOutputStream(tempPath,
                StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING)) {

            CompoundTag saveData = new CompoundTag().putCompound("Data", this.levelData);
            NBTIO.writeGZIPCompressed(saveData, os);

            os.flush();
            if (os instanceof FileOutputStream fos) {
                fos.getFD().sync();
            }
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw new RuntimeException("Failed to write level.dat", e);
        }

        try {
            Files.move(tempPath, levelDatPath, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            try {
                Files.deleteIfExists(tempPath);
            } catch (IOException ex) {
                e.addSuppressed(ex);
            }
            throw new RuntimeException("Failed to atomically move level.dat", e);
        }
    }

    @Override
    public void updateLevelName(String name) {
        if (!this.getName().equals(name)) {
            this.levelData.putString("LevelName", name);
        }
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

        synchronized (this) {
            if (this.chunks.containsKey(index)) {
                return true;
            }
            return loadChunk(index, chunkX, chunkZ, create) != null;
        }
    }

    @Override
    public boolean unloadChunk(int X, int Z) {
        return this.unloadChunk(X, Z, true);
    }

    @Override
    public boolean unloadChunk(int X, int Z, boolean safe) {
        long index = Level.chunkHash(X, Z);
        BaseFullChunk chunk = this.chunks.get(index);
        if (chunk == null) {
            return true;
        }

        if (chunk.unload(false, safe)) {
            lastChunk.remove();
            this.chunks.remove(index);
            return true;
        }
        return false;
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ) {
        return this.getChunk(chunkX, chunkZ, false);
    }

    @Override
    public BaseFullChunk getLoadedChunk(int chunkX, int chunkZ) {
        BaseFullChunk tmp = this.getThreadLastChunk();
        if (tmp != null && tmp.getX() == chunkX && tmp.getZ() == chunkZ) {
            return tmp;
        }
        long index = Level.chunkHash(chunkX, chunkZ);
        lastChunk.set(new WeakReference<>(tmp = chunks.get(index)));
        return tmp;
    }

    @Override
    public BaseFullChunk getLoadedChunk(long hash) {
        BaseFullChunk tmp = this.getThreadLastChunk();
        if (tmp != null && tmp.getIndex() == hash) {
            return tmp;
        }
        lastChunk.set(new WeakReference<>(tmp = chunks.get(hash)));
        return tmp;
    }

    @Override
    public BaseFullChunk getChunk(int chunkX, int chunkZ, boolean create) {
        BaseFullChunk tmp = this.getThreadLastChunk();
        if (tmp != null && tmp.getX() == chunkX && tmp.getZ() == chunkZ) {
            return tmp;
        }
        long index = Level.chunkHash(chunkX, chunkZ);
        lastChunk.set(new WeakReference<>(tmp = chunks.get(index)));
        if (tmp == null) {
            tmp = this.loadChunk(index, chunkX, chunkZ, create);
            lastChunk.set(new WeakReference<>(tmp));
        }
        return tmp;
    }

    @Nullable
    protected final BaseFullChunk getThreadLastChunk() {
        var ref = lastChunk.get();
        if (ref == null) {
            return null;
        }
        return ref.get();
    }

    @Override
    public void setChunk(int chunkX, int chunkZ, FullChunk chunk) {
        if (!(chunk instanceof BaseFullChunk baseChunk)) {
            throw new ChunkException("Invalid Chunk class");
        }

        long index = Level.chunkHash(chunkX, chunkZ);

        BaseFullChunk oldChunk = this.chunks.remove(index);
        if (oldChunk != null && oldChunk != baseChunk) {
            oldChunk.setProvider(null);
        }

        baseChunk.setProvider(this);
        baseChunk.setPosition(chunkX, chunkZ);
        this.chunks.put(index, baseChunk);
    }

    @Override
    public boolean isChunkPopulated(int chunkX, int chunkZ) {
        BaseFullChunk chunk = this.getChunk(chunkX, chunkZ);
        return chunk != null && chunk.isPopulated();
    }

    @Override
    public synchronized void close() {
        this.unloadChunks();
        synchronized (regions) {
            ObjectIterator<BaseRegionLoader> iter = this.regions.values().iterator();
            while (iter.hasNext()) {
                try {
                    iter.next().close();
                } catch (IOException e) {
                    throw new RuntimeException("Unable to close RegionLoader", e);
                }
                lastRegion.set(null);
                iter.remove();
            }
        }
        this.level = null;
    }

    @Override
    public boolean isChunkGenerated(int chunkX, int chunkZ) {
        BaseRegionLoader region = this.getRegion(chunkX >> 5, chunkZ >> 5);
        return region != null
                && region.chunkExists(chunkX - (region.getX() << 5), chunkZ - (region.getZ() << 5))
                && this.getChunk(chunkX - (region.getX() << 5), chunkZ - (region.getZ() << 5), true).isGenerated();
    }
}