package cn.nukkit.level.format.anvil;

import cn.nukkit.GameVersion;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.generic.BaseLevelProvider;
import cn.nukkit.level.format.generic.BaseRegionLoader;
import cn.nukkit.level.format.generic.serializer.NetworkChunkSerializer;
import cn.nukkit.level.generator.Generator;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.ChunkException;
import it.unimi.dsi.fastutil.objects.ObjectSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.regex.Pattern;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class Anvil extends BaseLevelProvider {

    public Anvil(Level level, String path) throws IOException {
        super(level, path);
    }

    public static String getProviderName() {
        return "anvil";
    }

    public static byte getProviderOrder() {
        return ORDER_YZX;
    }

    public static boolean usesChunkSection() {
        return true;
    }

    public static boolean isValid(String path) {
        boolean isValid = (new File(path + "/level.dat").exists()) && new File(path + "/region/").isDirectory();
        if (isValid) {
            for (File file : Objects.requireNonNull(new File(path + "/region/").listFiles((dir, name) -> Pattern.matches("^.+\\.mc[r|a]$", name)))) {
                if (!file.getName().endsWith(".mca")) {
                    isValid = false;
                    break;
                }
            }
        }
        return isValid;
    }

    public static void generate(String path, String name, long seed, Class<? extends Generator> generator) throws IOException {
        generate(path, name, seed, generator, new HashMap<>());
    }

    public static void generate(String path, String name, long seed, Class<? extends Generator> generator, Map<String, String> options) throws IOException {
        if (!new File(path + "/region").exists()) {
            new File(path + "/region").mkdirs();
        }

        CompoundTag levelData = new CompoundTag("Data")
                .putCompound("GameRules", new CompoundTag())
                .putLong("DayTime", 0)
                .putInt("GameType", 0)
                .putString("generatorName", Generator.getGeneratorName(generator))
                .putString("generatorOptions", options.getOrDefault("preset", ""))
                .putInt("generatorVersion", 1)
                .putBoolean("hardcore", false)
                .putBoolean("initialized", true)
                .putLong("LastPlayed", System.currentTimeMillis() / 1000)
                .putString("LevelName", name)
                .putBoolean("raining", false)
                .putInt("rainTime", 0)
                .putLong("RandomSeed", seed)
                .putInt("SpawnX", 128)
                .putInt("SpawnY", 70)
                .putInt("SpawnZ", 128)
                .putBoolean("thundering", false)
                .putInt("thunderTime", 0)
                .putInt("version", 19133)
                .putLong("Time", 0)
                .putLong("SizeOnDisk", 0);

        NBTIO.writeGZIPCompressed(new CompoundTag().putCompound("Data", levelData), new FileOutputStream(path + "level.dat"), ByteOrder.BIG_ENDIAN);
    }

    @Override
    public String getGenerator() {
        String generator = super.getGenerator();
        if ("normal".equalsIgnoreCase(generator)) { // anvil 不支持384世界高度，强制使用旧的生成器
            generator = "oldnormal";
        }
        return generator;
    }

    @Override
    public Chunk getEmptyChunk(int chunkX, int chunkZ) {
        return Chunk.getEmptyChunk(chunkX, chunkZ, this);
    }

    @Override
    public void requestChunkTask(ObjectSet<GameVersion> protocols, int x, int z) throws ChunkException {
        this.requestChunkTask(protocols, x, z, 0L);
    }

    @Override
    public void requestChunkTask(ObjectSet<GameVersion> protocols, int x, int z, long sequence) throws ChunkException {
        Chunk chunk = (Chunk) this.getChunk(x, z, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        // 池可能为 null（配置关，或世界早于热重载打开开关而创建）：回退同步序列化，与 SubChunkRequestProcessor 一致
        // The executor can be null (config off, or the world predates a hot reload turning it on):
        // serialize synchronously, same fallback as SubChunkRequestProcessor
        ExecutorService executor = this.getServer().asyncChunkSending ? this.level.getAsyncChuckExecutor() : null;
        if (executor == null) {
            this.requestChunkTaskSync(protocols, chunk, timestamp, x, z);
            return;
        }

        final Chunk chunkClone = chunk.cloneForChunkSending();
        try {
            executor.execute(() -> {
                try {
                    NetworkChunkSerializer.serialize(protocols, chunkClone, networkChunkSerializerCallback -> {
                        getLevel().asyncChunkRequestCallback(networkChunkSerializerCallback.getGameVersion(),
                                timestamp,
                                x,
                                z,
                                networkChunkSerializerCallback.getSubchunks(),
                                networkChunkSerializerCallback.getStream().getBuffer(),
                                networkChunkSerializerCallback.getSubChunkBlobs(),
                                networkChunkSerializerCallback.getBiomes(),
                                networkChunkSerializerCallback.getBlockEntities(),
                                sequence
                        );
                    }, level.antiXrayEnabled(), getLevel().getDimensionData(), getLevel().isClientCacheDeliveryWanted());
                } catch (Throwable t) {
                    getLevel().getServer().getLogger().error("Async chunk serialization failed for chunk (" + x + ", " + z + ")", t);
                    for (GameVersion gv : protocols) {
                        getLevel().onAsyncChunkRequestFailed(gv, x, z);
                    }
                }
            });
        } catch (RejectedExecutionException e) {
            // 关服中池已停：同步兜底，不让请求丢失 / pool already down during shutdown: sync fallback
            this.requestChunkTaskSync(protocols, chunk, timestamp, x, z);
        }
    }

    /**
     * 主线程同步序列化（async-chunks 关闭 / 世界早于开关创建 / 执行器已关停）
     * <p>
     * Main-thread serialization (async-chunks off / world predates the setting / executor shut down)
     */
    private void requestChunkTaskSync(ObjectSet<GameVersion> protocols, Chunk chunk, long timestamp, int x, int z) {
        try {
            NetworkChunkSerializer.serialize(protocols, chunk, networkChunkSerializerCallback -> {
                this.getLevel().chunkRequestCallback(networkChunkSerializerCallback.getGameVersion(),
                        timestamp,
                        x,
                        z,
                        networkChunkSerializerCallback.getSubchunks(),
                        networkChunkSerializerCallback.getStream().getBuffer(),
                        networkChunkSerializerCallback.getSubChunkBlobs(),
                        networkChunkSerializerCallback.getBiomes(),
                        networkChunkSerializerCallback.getBlockEntities()
                );
            }, level.antiXrayEnabled(), this.level.getDimensionData(), this.level.isClientCacheDeliveryWanted());
        } catch (Throwable t) {
            getLevel().getServer().getLogger().error("Sync chunk serialization failed for chunk (" + x + ", " + z + ")", t);
            for (GameVersion gv : protocols) {
                getLevel().onAsyncChunkRequestFailed(gv, x, z);
            }
        }
    }

    private int lastPosition = 0;

    @Override
    public void doGarbageCollection(long time) {
        long start = System.currentTimeMillis();
        int maxIterations = size();
        if (lastPosition > maxIterations) lastPosition = 0;
        int i;
        synchronized (chunks) {
            Iterator<BaseFullChunk> iter = chunks.values().iterator();
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
                if (chunk.isGenerated() && chunk.isPopulated() && chunk instanceof Chunk) {
                    chunk.compress();
                    if (System.currentTimeMillis() - start >= time) break;
                }
            }
        }
        lastPosition += i;
    }

    @Override
    public synchronized BaseFullChunk loadChunk(long index, int chunkX, int chunkZ, boolean create) {
        int regionX = getRegionIndexX(chunkX);
        int regionZ = getRegionIndexZ(chunkZ);
        BaseRegionLoader region = this.loadRegion(regionX, regionZ);
        BaseFullChunk chunk;
        try {
            chunk = region.readChunk(chunkX - (regionX << 5), chunkZ - (regionZ << 5));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        if (chunk == null) {
            if (create) {
                chunk = this.getEmptyChunk(chunkX, chunkZ);
                putChunk(index, chunk);
            }
        } else {
            putChunk(index, chunk);
        }
        return chunk;
    }

    @Override
    public boolean isOffThreadChunkReadSupported() {
        return this.level != null;
    }

    @Override
    public synchronized BaseFullChunk readChunkOffThread(int chunkX, int chunkZ) {
        // synchronized 于 provider:region 共享 RandomAccessFile(seek+read 非原子),须与主线程 loadChunk/saveChunk/GC 串行
        // synchronized on the provider: regions share a RandomAccessFile (seek+read is not atomic), so reads must serialize with main-thread loadChunk/saveChunk/GC
        int regionX = getRegionIndexX(chunkX);
        int regionZ = getRegionIndexZ(chunkZ);
        BaseRegionLoader region = this.loadRegion(regionX, regionZ);
        try {
            return region.readChunk(chunkX - (regionX << 5), chunkZ - (regionZ << 5));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public synchronized void saveChunk(int X, int Z) {
        BaseFullChunk chunk = this.getChunk(X, Z);
        if (chunk != null) {
            try {
                this.loadRegion(X >> 5, Z >> 5).writeChunk(chunk);
            } catch (Exception e) {
                throw new ChunkException("Error saving chunk (" + X + ", " + Z + ')', e);
            }
        }
    }


    @Override
    public synchronized void saveChunk(int x, int z, FullChunk chunk) {
        if (!(chunk instanceof Chunk)) {
            throw new ChunkException("Invalid Chunk class");
        }
        int regionX = x >> 5;
        int regionZ = z >> 5;
        this.loadRegion(regionX, regionZ);
        chunk.setX(x);
        chunk.setZ(z);
        try {
            this.getRegion(regionX, regionZ).writeChunk(chunk);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static ChunkSection createChunkSection(int y) {
        ChunkSection cs = new ChunkSection(y);
        cs.hasSkyLight = true;
        return cs;
    }

    protected synchronized BaseRegionLoader loadRegion(int x, int z) {
        BaseRegionLoader tmp = lastRegion.get();
        if (tmp != null && x == tmp.getX() && z == tmp.getZ()) {
            return tmp;
        }
        long index = Level.chunkHash(x, z);
        synchronized (regions) {
            BaseRegionLoader region = this.regions.get(index);
            if (region == null) {
                try {
                    region = new RegionLoader(this, x, z);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
                this.regions.put(index, region);
            }
            lastRegion.set(region);
            return region;
        }
    }

    @Override
    public int getMaximumLayer() {
        return 1;
    }
}
