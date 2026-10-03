package cn.nukkit.level;

import cn.nukkit.block.Block;
import cn.nukkit.level.format.Chunk;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.generic.BaseFullChunk;
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
    private final LongOpenHashSet queued = new LongOpenHashSet();

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
        if (loaded(hash) != null || !attemptedReads.add(hash)) return;
        // Mark before request: a synchronous completion/mount must not expand the read halo.
        lightReadMounts.put(hash, null);
        // Outcomes, including failure/DISABLED/REJECTED, never authorize inline disk IO.
        level.requestChunkLoadAsyncResult(Level.getHashX(hash), Level.getHashZ(hash));
    }

    void defer(int x, int y, int z, List<Long> roots) {
        if (roots != admittedRoots) {
            // updateBlockLight creates a fresh snapshot list per invocation. Admit its
            // halos once, not for every cold cell reached by that invocation's wave.
            admittedRoots = roots;
            deferredChunks.clear();
            for (long root : roots) {
                for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                    request(Level.chunkHash(Level.getHashX(root) + dx, Level.getHashZ(root) + dz));
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
        request(cold);
    }

    void mounted(BaseFullChunk chunk, boolean requestCold) {
        if (!healthy(chunk)) return;
        long hash = Level.chunkHash(chunk.getX(), chunk.getZ());
        boolean lightOnly = lightReadMounts.containsKey(hash);
        if (lightOnly) lightReadMounts.put(hash, chunk);
        attemptedReads.remove(hash);
        // One 3x3 halo covers every possible 15-level contribution to the original chunk,
        // including diagonal paths. Light-only mounts and repair never expand that halo.
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            long other = Level.chunkHash(chunk.getX() + dx, chunk.getZ() + dz);
            Edge edge = Edge.of(hash, other);
            addEdge(edge);
            BaseFullChunk neighbour = loaded(other);
            if (ready(chunk) && ready(neighbour)) {
                seed(hash, chunk);
                seed(other, neighbour);
            } else if (neighbour == null && requestCold && !lightOnly) {
                request(other);
            }
        }
    }

    private void addEdge(Edge edge) {
        cleanup = null;
        cleanupNeeded = true;
        pending.add(edge);
    }

    private void seed(long hash, BaseFullChunk chunk) {
        Seed current = seeds.get(hash);
        if (current == null || current.chunk != chunk) seeds.put(hash, new Seed(chunk));
    }

    void tick() {
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
            queued.remove(cell); // Decreases must revisit nodes; no lifetime visited set.
            int x = Hash.hashBlockX(cell), y = Hash.hashBlockY(cell), z = Hash.hashBlockZ(cell);
            BaseFullChunk chunk = loaded(Level.chunkHash(x >> 4, z >> 4));
            if (!ready(chunk) || !level.isYInRange(y)) continue;
            int current = chunk.getBlockLight(x & 15, y, z & 15);
            int expected = Block.getBlockLight(chunk.getBlockId(x & 15, y, z & 15));
            expected = Math.max(expected, incoming(x - 1, y, z, current));
            expected = Math.max(expected, incoming(x + 1, y, z, current));
            expected = Math.max(expected, incoming(x, y - 1, z, current));
            expected = Math.max(expected, incoming(x, y + 1, z, current));
            expected = Math.max(expected, incoming(x, y, z - 1, current));
            expected = Math.max(expected, incoming(x, y, z + 1, current));
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
        int y = minY, offset;
        boolean done() { return y > maxY; }
        Seed(BaseFullChunk chunk) { this.chunk = chunk; }

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
                    enqueue((chunk.getX() << 4) + x, y, (chunk.getZ() << 4) + z);
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
