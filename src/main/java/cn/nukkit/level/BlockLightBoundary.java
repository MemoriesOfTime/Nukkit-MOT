package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunkSection;
import cn.nukkit.utils.Hash;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Main-thread, reconstructible work: saved light is never treated as an emitter. */
final class BlockLightBoundary {
    static final int WORK_PER_TICK = 4096;
    static final int READ_RETRY_DELAY_TICKS = 20;
    private record Edge(long a, long b) {
        static Edge of(long a, long b) { return a < b ? new Edge(a, b) : new Edge(b, a); }
    }
    private final Level level;
    private final Set<Edge> pending = new HashSet<>();
    private Iterator<Edge> cleanup;
    private boolean cleanupNeeded;
    private final LongOpenHashSet attemptedReads = new LongOpenHashSet();
    private List<Long> admittedRoots = List.of();
    private final LongOpenHashSet deferredChunks = new LongOpenHashSet();
    private final Map<Long, BaseFullChunk> lightReadMounts = new LinkedHashMap<>();
    private final Map<Long, Seed> seeds = new LinkedHashMap<>();
    private final LongArrayFIFOQueue cells = new LongArrayFIFOQueue();
    private final BlockLightPendingCells queued = new BlockLightPendingCells();

    // Lazy and bounded by the originally rejected coordinates, never a new read halo.
    private LinkedHashMap<Long, RejectedRead> rejectedReads;
    private long readRetryTick;
    private long requestingHash;
    private LevelProvider requestingProvider;
    private BaseFullChunk requestingSource;
    private Map<Long, Set<Integer>> requestingBucket;
    private boolean requestingRead;
    private boolean requestingRejected;

    private static final class RejectedRead {
        final long hash;
        final LevelProvider provider;
        final Map<Long, BaseFullChunk> sources = new LinkedHashMap<>();
        Map<Long, Set<Integer>> bucketMap;
        Set<Integer> bucket;
        long dueTick;

        RejectedRead(long hash, LevelProvider provider, long dueTick) {
            this.hash = hash;
            this.provider = provider;
            this.dueTick = dueTick;
        }
    }

    BlockLightBoundary(Level level) { this.level = level; }

    static boolean healthy(BaseFullChunk chunk) {
        return chunk != null && !chunk.isReadFailurePlaceholder() && chunk.getChunkLoadFailure() == null;
    }

    private BaseFullChunk loaded(long hash) {
        BaseFullChunk chunk = level.getChunkIfLoaded(Level.getHashX(hash), Level.getHashZ(hash));
        return healthy(chunk) ? chunk : null;
    }

    private boolean ready(BaseFullChunk chunk) {
        return healthy(chunk) && ((!chunk.isGenerated())
                || (chunk.isPopulated() && chunk.isLightPopulated()));
    }

    void request(long hash) {
        request(hash, null, null);
    }

    void request(long hash, Map<Long, Set<Integer>> bucket) {
        request(hash, null, bucket);
    }

    private boolean request(long hash, BaseFullChunk source, Map<Long, Set<Integer>> bucket) {
        if (loaded(hash) != null) return false;
        if (!attemptedReads.add(hash)) {
            // An empty lazy queue must not box duplicate hashes after recovery.
            RejectedRead rejected = rejectedReads == null || rejectedReads.isEmpty() ? null : rejectedReads.get(hash);
            if (rejected != null) authorize(rejected, source, bucket);
            return false;
        }
        // Mark before request: a synchronous completion/mount must not expand the read halo.
        lightReadMounts.put(hash, null);
        // Native rejection reports synchronously after ticket close. Stack-save the context
        // because provider callbacks may reenter; accepted reads need no future listener.
        long previousHash = requestingHash;
        var previousProvider = requestingProvider;
        BaseFullChunk previousSource = requestingSource;
        Map<Long, Set<Integer>> previousBucket = requestingBucket;
        boolean previousRead = requestingRead;
        boolean previousRejected = requestingRejected;
        requestingHash = hash;
        requestingProvider = level.getProvider();
        requestingSource = source;
        requestingBucket = bucket;
        requestingRead = true;
        requestingRejected = false;
        try {
            level.requestChunkLoadAsyncResult(Level.getHashX(hash), Level.getHashZ(hash));
            return requestingRejected;
        } finally {
            requestingHash = previousHash;
            requestingProvider = previousProvider;
            requestingSource = previousSource;
            requestingBucket = previousBucket;
            requestingRead = previousRead;
            requestingRejected = previousRejected;
        }
    }

    /** Called only by native terminal rejection, in the original submitting thread. */
    void readRejected(long hash) {
        if (!requestingRead || requestingHash != hash || requestingProvider == null) return;
        if (level.getProvider() != requestingProvider || requestingProvider.getLevel() != level) {
            // A provider callback changed the epoch before queue admission rejected.
            // Release this stale attempt so a fresh owner can request, never retry it.
            attemptedReads.remove(hash);
            if (lightReadMounts.get(hash) == null) lightReadMounts.remove(hash);
            return;
        }
        requestingRejected = true;
        if (rejectedReads == null) rejectedReads = new LinkedHashMap<>();
        RejectedRead rejected = rejectedReads.get(hash);
        if (rejected == null || rejected.provider != requestingProvider) {
            rejected = new RejectedRead(hash, requestingProvider, readRetryTick + READ_RETRY_DELAY_TICKS);
            rejectedReads.put(hash, rejected);
        }
        authorize(rejected, requestingSource, requestingBucket);
    }

    private void authorize(RejectedRead rejected, BaseFullChunk source, Map<Long, Set<Integer>> bucket) {
        if (healthy(source) && source.getProvider() == rejected.provider && loaded(source.getIndex()) == source) {
            // The originating root/producer is adjacent by construction. No arbitrary cap
            // may discard a later live owner; replacement requires this fresh request.
            rejected.sources.put(source.getIndex(), source);
        }
        if (bucket != null) {
            synchronized (bucket) {
                Set<Integer> current = bucket.get(rejected.hash);
                if (current != null && !current.isEmpty()) {
                    rejected.bucketMap = bucket;
                    rejected.bucket = current;
                }
            }
        }
    }

    private boolean authorized(RejectedRead rejected) {
        if (level.getProvider() != rejected.provider || rejected.provider.getLevel() != level) return false;
        if (rejected.bucketMap != null) {
            synchronized (rejected.bucketMap) {
                if (rejected.bucketMap.get(rejected.hash) == rejected.bucket && !rejected.bucket.isEmpty()) return true;
            }
        }
        for (var entry : rejected.sources.entrySet()) {
            BaseFullChunk source = entry.getValue();
            if (healthy(source) && source.getProvider() == rejected.provider
                    && source.getIndex() == entry.getKey() && loaded(entry.getKey()) == source) return true;
        }
        return false;
    }

    private void retryRejectedRead() {
        if (rejectedReads == null || rejectedReads.isEmpty()) return;
        readRetryTick++;
        var iterator = rejectedReads.entrySet().iterator();
        RejectedRead rejected = iterator.next().getValue();
        if (readRetryTick - rejected.dueTick < 0) return;
        iterator.remove(); // At most one original coordinate is inspected/admitted per tick.
        if (loaded(rejected.hash) != null || !authorized(rejected)) {
            attemptedReads.remove(rejected.hash);
            if (lightReadMounts.get(rejected.hash) == null) lightReadMounts.remove(rejected.hash);
            return;
        }
        attemptedReads.remove(rejected.hash);
        // Keep the original source/bucket authorities through another queue rejection.
        rejected.dueTick = readRetryTick + READ_RETRY_DELAY_TICKS;
        rejectedReads.put(rejected.hash, rejected);
        BaseFullChunk source = rejected.sources.isEmpty() ? null : rejected.sources.values().iterator().next();
        boolean rejectedAgain = request(rejected.hash, source, rejected.bucketMap);
        // Success/inflight/FAILED/DISABLED/CANCELLED retains the original attempt policy.
        if (!rejectedAgain && rejectedReads != null) rejectedReads.remove(rejected.hash, rejected);
    }

    void defer(int x, int y, int z, List<Long> roots) {
        defer(x, y, z, roots, null);
    }

    void defer(int x, int y, int z, List<Long> roots, BaseFullChunk source) {
        if (roots != admittedRoots) {
            // updateBlockLight creates a fresh snapshot list per invocation. Admit its
            // halos once, not for every cold cell reached by that invocation's wave.
            admittedRoots = roots;
            deferredChunks.clear();
            for (long root : roots) {
                BaseFullChunk rootSource = loaded(root);
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    request(Level.chunkHash(Level.getHashX(root) + dx, Level.getHashZ(root) + dz), rootSource, null);
                }
            }
        }
        long cold = Level.chunkHash(x >> 4, z >> 4);
        if (!deferredChunks.add(cold)) return;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long neighbour = Level.chunkHash((x >> 4) + dx, (z >> 4) + dz);
            if (loaded(neighbour) != null) addEdge(Edge.of(cold, neighbour));
        }
        // A stronger loaded emitter can resume a wave outside the original root halo.
        request(cold, source, null);
    }

    void mounted(BaseFullChunk chunk, boolean requestCold) {
        if (!healthy(chunk)) return;
        long hash = Level.chunkHash(chunk.getX(), chunk.getZ());
        boolean lightOnly = lightReadMounts.containsKey(hash);
        if (lightOnly) lightReadMounts.put(hash, chunk);
        attemptedReads.remove(hash);
        if (rejectedReads != null) rejectedReads.remove(hash);
        // One 3x3 halo covers every possible 15-level contribution to the original chunk,
        // including diagonal paths. Light-only mounts and repair never expand that halo.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long other = Level.chunkHash(chunk.getX() + dx, chunk.getZ() + dz);
            Edge edge = Edge.of(hash, other);
            addEdge(edge);
            BaseFullChunk neighbour = loaded(other);
            if (ready(chunk) && ready(neighbour)) {
                seed(hash, chunk, other, neighbour);
                seed(other, neighbour, hash, chunk);
            } else if (neighbour == null && requestCold && !lightOnly) {
                request(other, chunk, null);
            }
        }
    }

    private void addEdge(Edge edge) {
        cleanup = null;
        cleanupNeeded = true;
        pending.add(edge);
    }

    private void seed(long hash, BaseFullChunk chunk, long neighbourHash, BaseFullChunk neighbour) {
        Seed current = seeds.get(hash);
        if (current == null || current.chunk != chunk) {
            current = new Seed(chunk);
            seeds.put(hash, current);
        }
        current.neighbourMounted(neighbourHash, neighbour);
    }

    void tick() {
        retryRejectedRead();
        int scans = WORK_PER_TICK;
        var iterator = seeds.entrySet().iterator();
        while (iterator.hasNext() && scans > 0) {
            var entry = iterator.next();
            Seed seed = entry.getValue();
            if (loaded(entry.getKey()) != seed.chunk || !ready(seed.chunk)) {
                iterator.remove();
                continue;
            }
            scans -= seed.scan(scans);
            if (seed.done()) iterator.remove();
        }
        for (int work = 0; work < WORK_PER_TICK && !cells.isEmpty(); work++) {
            long cell = cells.dequeueLong();
            int x = Hash.hashBlockX(cell), y = Hash.hashBlockY(cell), z = Hash.hashBlockZ(cell);
            queued.remove(cell); // Decreases must revisit nodes; no lifetime visited set.
            BaseFullChunk chunk = loaded(Level.chunkHash(x >> 4, z >> 4));
            if (!ready(chunk) || !level.isYInRange(y)) continue;
            long sample = interiorSample(chunk, x, y, z);
            int current, expected;
            if (sample >= 0) {
                current = (int) (sample >>> 32);
                expected = (int) sample;
            } else {
                current = chunk.getBlockLight(x & 15, y, z & 15);
                expected = Block.getBlockLight(chunk.getBlockId(x & 15, y, z & 15));
                expected = Math.max(expected, incoming(x - 1, y, z, current));
                expected = Math.max(expected, incoming(x + 1, y, z, current));
                expected = Math.max(expected, incoming(x, y - 1, z, current));
                expected = Math.max(expected, incoming(x, y + 1, z, current));
                expected = Math.max(expected, incoming(x, y, z - 1, current));
                expected = Math.max(expected, incoming(x, y, z + 1, current));
            }
            if (current == expected) continue;
            chunk.setBlockLight(x & 15, y, z & 15, expected);
            enqueue(x - 1, y, z); enqueue(x + 1, y, z);
            enqueue(x, y - 1, z); enqueue(x, y + 1, z);
            enqueue(x, y, z - 1); enqueue(x, y, z + 1);
        }
        if (seeds.isEmpty() && cells.isEmpty() && cleanupNeeded) {
            if (cleanup == null) cleanup = pending.iterator();
            for (int work = 0; work < 64 && cleanup.hasNext(); work++) {
                Edge edge = cleanup.next();
                if (ready(loaded(edge.a)) && ready(loaded(edge.b))) cleanup.remove();
            }
            if (!cleanup.hasNext()) { cleanup = null; cleanupNeeded = false; }
        }
    }

    private long interiorSample(BaseFullChunk chunk, int x, int y, int z) {
        int lx = x & 15, ly = y & 15, lz = z & 15;
        if (chunk.getClass() != LevelDBChunk.class || lx == 0 || lx == 15
                || ly == 0 || ly == 15 || lz == 0 || lz == 15
                || !level.isYInRange(y - 1) || !level.isYInRange(y + 1)) return -1;
        ChunkSection section = ((LevelDBChunk) chunk).getSection(y >> 4);
        return section.getClass() == LevelDBChunkSection.class
                ? ((LevelDBChunkSection) section).sampleInteriorBlockLight(lx, ly, lz) : -1;
    }

    private int incoming(int x, int y, int z, int current) {
        if (!level.isYInRange(y)) return 0;
        BaseFullChunk neighbour = loaded(Level.chunkHash(x >> 4, z >> 4));
        // Unknown outer faces retain their saved contribution until the neighbour mounts.
        // They are not darkness, and reconciliation must not recursively request IO.
        if (!ready(neighbour)) return current;
        int id = neighbour.getBlockId(x & 15, y, z & 15);
        int filter = id < 0 || id >= Block.MAX_BLOCK_ID ? 15 : Block.lightFilter[id];
        return Math.max(0, neighbour.getBlockLight(x & 15, y, z & 15) - Math.max(1, filter));
    }

    private void enqueue(int x, int y, int z) {
        if (!level.isYInRange(y)) return;
        if (loaded(Level.chunkHash(x >> 4, z >> 4)) == null) return;
        long hash = Hash.hashBlock(x, y, z);
        if (queued.add(hash)) cells.enqueue(hash);
    }

    void unloaded(int x, int z) {
        long hash = Level.chunkHash(x, z);
        seeds.remove(hash);
        lightReadMounts.remove(hash);
        attemptedReads.remove(hash);
        if (rejectedReads != null) rejectedReads.remove(hash);
        // If both endpoints leave memory, mount reconstructs the edge from coordinates.
        cleanup = null;
        cleanupNeeded = true;
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long other = Level.chunkHash(x + dx, z + dz);
            if (loaded(other) == null) pending.remove(Edge.of(hash, other));
        }
    }

    int pendingBoundaries() { return pending.size(); }
    boolean hasWork() { return !seeds.isEmpty() || !cells.isEmpty(); }

    private final class Seed {
        final BaseFullChunk chunk;
        final int minY = level.getMinBlockY(), maxY = level.getMaxBlockY();
        final Map<Long, BaseFullChunk> neighbours = new LinkedHashMap<>();
        int y = minY, offset;
        boolean done() { return y > maxY; }
        Seed(BaseFullChunk chunk) { this.chunk = chunk; }

        void neighbourMounted(long hash, BaseFullChunk neighbour) {
            if (neighbours.put(hash, neighbour) == neighbour) return;
            // A newly mounted generation opens a face this scan may already have passed.
            // Duplicate notifications retain progress; each of the eight neighbours can
            // rewind only when its canonical chunk instance changes.
            y = minY;
            offset = 0;
        }

        int scan(int budget) {
            int work = 0;
            while (!done() && work < budget) {
                if ((y & 15) == 0 && offset == 0 && darkSection(y)) {
                    // Palette/light-array inspection also consumes budget; a dark world
                    // must not drain an unbounded number of chunk seeds in one tick.
                    work += Math.min(256, budget - work);
                    y += 16;
                    continue;
                }
                int x = offset & 15, z = offset >> 4;
                if (chunk.getBlockLight(x, y, z) != 0
                        || Block.getBlockLight(chunk.getBlockId(x, y, z)) != 0) {
                    int worldX = (chunk.getX() << 4) + x, worldZ = (chunk.getZ() << 4) + z;
                    enqueue(worldX, y, worldZ);
                    // Saved light in this chunk may already be correct. Seed the opposite
                    // face too: an unchanged cell cannot otherwise wake its dark neighbour.
                    // Each face is scanned from both chunks; stable relaxation never loops.
                    if (x == 0) enqueue(worldX - 1, y, worldZ);
                    if (x == 15) enqueue(worldX + 1, y, worldZ);
                    if (z == 0) enqueue(worldX, y, worldZ - 1);
                    if (z == 15) enqueue(worldX, y, worldZ + 1);
                }
                work++;
                if (++offset == 256) { offset = 0; y++; }
            }
            return work;
        }

        private boolean darkSection(int blockY) {
            if (!(chunk instanceof Chunk sectioned)) return false;
            ChunkSection[] sections = sectioned.getSections();
            int index = (blockY >> 4) + sectioned.getSectionOffset();
            if (sections == null || index < 0 || index >= sections.length) return false;
            ChunkSection section = sections[index];
            if (section == null) return false;
            if (section.maybeHasLightSource()) return false;
            for (byte value : section.getLightArray()) if (value != 0) return false;
            return true;
        }
    }
}
