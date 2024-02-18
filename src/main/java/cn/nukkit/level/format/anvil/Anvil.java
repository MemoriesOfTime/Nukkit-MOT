package cn.nukkit.level.format.anvil;

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
import it.unimi.dsi.fastutil.ints.IntSet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
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
    public Chunk getEmptyChunk(int chunkX, int chunkZ) {
        return Chunk.getEmptyChunk(chunkX, chunkZ, this);
    }

    @Override
    public void requestChunkTask(IntSet protocols, int x, int z) throws ChunkException {
        Chunk chunk = (Chunk) this.getChunk(x, z, false);
        if (chunk == null) {
            throw new ChunkException("Invalid Chunk Set");
        }

        long timestamp = chunk.getChanges();

        if (this.getServer().asyncChunkSending) {
            final Chunk chunkClone = chunk.cloneForChunkSending();
            this.level.getAsyncChuckExecutor().execute(() -> {
                NetworkChunkSerializer.serialize(protocols, chunkClone, networkChunkSerializerCallback -> {
                    getLevel().asyncChunkRequestCallback(networkChunkSerializerCallback.getProtocolId(),
                            timestamp,
                            x,
                            z,
                            networkChunkSerializerCallback.getSubchunks(),
                            networkChunkSerializerCallback.getStream().getBuffer()
                    );
                }, level.antiXrayEnabled(), getLevel().getDimensionData());
            });
        }else {
            NetworkChunkSerializer.serialize(protocols, chunk, networkChunkSerializerCallback -> {
                this.getLevel().chunkRequestCallback(networkChunkSerializerCallback.getProtocolId(),
                        timestamp,
                        x,
                        z,
                        networkChunkSerializerCallback.getSubchunks(),
                        networkChunkSerializerCallback.getStream().getBuffer()
                );
            }, level.antiXrayEnabled(), this.level.getDimensionData());
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