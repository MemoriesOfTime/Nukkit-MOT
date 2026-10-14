package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.WriteBatch;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/** Process-local coupling of native world-container records. Never replays item operations. */
final class ContainerChunkSaveGroups {
    private final LevelDBProvider provider;
    private final Thread owner;
    private final Map<Long, Group> members = new HashMap<>();
    private final Map<Long, Integer> preparing = new HashMap<>();
    private final ConcurrentLinkedQueue<Group> flushes = new ConcurrentLinkedQueue<>();
    private final AtomicLong batchBytes = new AtomicLong();
    private long nextId;
    int maxChunks = 8;
    int maxGroups = 256;
    long maxBytes = 64L << 20;
    long retryMillis = 30_000;
    private boolean closing;

    private enum State { OPEN, CAPTURING, WRITING, FAILED, DONE }
    private static final class Group {
        final long id;
        final LinkedHashMap<Long, LevelDBChunk> chunks = new LinkedHashMap<>();
        Group parent;
        int active;
        boolean accepted, queued, released;
        long retainedBytes;
        State state = State.OPEN;
        long retryAt;
        LevelDBProvider.ContainerSnapshot snapshot;
        CompletableFuture<Void> completion = new CompletableFuture<>();
        Group(long id) { this.id = id; }
    }

    ContainerChunkSaveGroups(LevelDBProvider provider) { this.provider = provider; this.owner = cn.nukkit.Server.getInstance().getPrimaryThread() == null
            ? Thread.currentThread() : cn.nukkit.Server.getInstance().getPrimaryThread(); }
    private Group root(Group group) { while (group.parent != null) group = group.parent; return group; }
    synchronized boolean lifecycleBlocked() { return members.values().stream().anyMatch(group -> root(group).active != 0 || root(group).state == State.CAPTURING); }
    synchronized boolean contains(long hash) { return members.containsKey(hash); }
    synchronized int captureCost(long hash) {
        Group group = members.get(hash);
        return group == null ? 1 : root(group).state == State.OPEN ? root(group).chunks.size() : 0;
    }
    boolean primary() { return Thread.currentThread() == owner; }

    /** Reserve before serialization: an unregistered preparing image is also an earlier writer. */
    synchronized AutoCloseable reserveStandalone(long hash) {
        if (provider.closed || closing && !primary()) throw new DBException("Chunk provider is closing");
        if (members.containsKey(hash)) throw new DBException("Chunk belongs to an unresolved container group");
        preparing.merge(hash, 1, Integer::sum);
        return () -> { synchronized (ContainerChunkSaveGroups.this) {
            preparing.compute(hash, (key, count) -> count == null || count == 1 ? null : count - 1);
        }};
    }

    synchronized LevelDBProvider.ContainerMutationScope begin(Collection<? extends FullChunk> requested) {
        if (!primary() || closing || provider.closed || requested.isEmpty()) return null;
        LinkedHashMap<Long, LevelDBChunk> union = new LinkedHashMap<>();
        Set<Group> previous = Collections.newSetFromMap(new IdentityHashMap<>());
        for (FullChunk candidate : requested) {
            if (!(candidate instanceof LevelDBChunk chunk) || chunk.getProvider() != provider
                    || !chunk.isGenerated() || chunk.isReadFailurePlaceholder()
                    || !provider.isMountedContainerChunk(chunk)) return null;
            long hash = Level.chunkHash(chunk.getX(), chunk.getZ());
            union.put(hash, chunk);
            Group group = members.get(hash);
            if (group != null) previous.add(root(group));
        }
        for (Group group : previous) {
            if (group.state != State.OPEN) { queue(group); return null; }
            union.putAll(group.chunks);
        }
        if (union.size() > maxChunks) { previous.forEach(this::queue); return null; }
        if (members.values().stream().map(this::root).distinct().count() >= maxGroups && previous.isEmpty()
                || batchBytes.get() >= maxBytes) {
            members.values().stream().map(this::root).distinct().limit(2).forEach(this::queue);
            return null;
        }
        for (var entry : union.entrySet()) {
            if (!provider.isMountedContainerChunk(entry.getValue()) || preparing.containsKey(entry.getKey())
                    || provider.hasUnacknowledgedStandaloneContainerWrite(entry.getKey())) return null;
        }
        Group merged = previous.isEmpty() ? new Group(++nextId) : previous.iterator().next();
        for (Group group : previous) {
            if (group == merged) continue;
            merged.active += group.active;
            merged.accepted |= group.accepted;
            group.parent = merged;
            CompletableFuture<Void> oldCompletion = group.completion;
            merged.completion.whenComplete((unused, failure) -> {
                if (failure == null) oldCompletion.complete(null); else oldCompletion.completeExceptionally(failure);
            });
        }
        merged.chunks.putAll(union);
        merged.chunks.keySet().forEach(hash -> members.put(hash, merged));
        merged.active++;

        Group scopeGroup = merged;
        return new LevelDBProvider.ContainerMutationScope() {
            private boolean settled, accepted;
            @Override public void accepted() { accepted = true; }
            @Override public void close() { synchronized (ContainerChunkSaveGroups.this) {
                if (settled) return;
                settled = true;
                Group group = root(scopeGroup);
                group.accepted |= accepted;
                group.active--;
                if (group.active == 0 && !group.accepted) retire(group);
            }}
        };
    }

    synchronized Set<Long> participantHashes(Collection<? extends FullChunk> requested) {
        Set<Long> hashes = new HashSet<>();
        for (FullChunk chunk : requested) {
            Group group = members.get(Level.chunkHash(chunk.getX(), chunk.getZ()));
            if (group != null) hashes.addAll(root(group).chunks.keySet());
        }
        return hashes;
    }

    /** Null means ordinary ungrouped saving. Failure is never translated to a durability ACK. */
    CompletableFuture<Void> save(long hash, LevelDBChunk requested) {
        Group group;
        synchronized (this) {
            group = members.get(hash);
            if (group == null) return null;
            group = root(group);
            if (group.chunks.get(hash) != requested || !provider.isMountedContainerChunk(requested))
                return CompletableFuture.failedFuture(new DBException("Container chunk generation changed"));
            if (group.active != 0)
                return CompletableFuture.failedFuture(new DBException("Container mutation is still provisional"));
            if (group.state == State.WRITING || group.state == State.FAILED && group.snapshot != null) {
                Long captured = group.snapshot.changes().get(hash);
                if (captured == null || requested.getChanges() != captured)
                    return CompletableFuture.failedFuture(new DBException("Container group does not cover this newer revision"));
                return group.completion.copy();
            }
            if (group.state == State.CAPTURING)
                return CompletableFuture.failedFuture(new DBException("Container group snapshot is still preparing"));
            if (!primary()) { queue(group); return group.completion.copy(); }
            if (closing || provider.closed)
                return CompletableFuture.failedFuture(new DBException("Container provider is closing"));
            if (group.state == State.FAILED) group.completion = new CompletableFuture<>();
            group.state = State.CAPTURING;
        }
        capture(group);
        return group.completion.copy();
    }

    private void capture(Group group) {
        try {
            for (LevelDBChunk chunk : group.chunks.values())
                if (!provider.isMountedContainerChunk(chunk)) throw new DBException("Container chunk generation changed during capture");
            LevelDBProvider.ContainerSnapshot snapshot = provider.captureContainerChunks(group.chunks);
            long bytes = Integer.toUnsignedLong(snapshot.batch().getApproximateSize());
            if (bytes > maxBytes || batchBytes.addAndGet(bytes) > maxBytes) {
                if (bytes <= maxBytes) batchBytes.addAndGet(-bytes);
                snapshot.batch().close();
                throw new DBException("Container group exceeds retained batch byte budget");
            }
            synchronized (this) {
                group.retainedBytes = bytes;
                group.snapshot = snapshot;
                group.state = State.WRITING;
            }
            submit(group);
        } catch (Throwable failure) { fail(group, failure); }
    }

    private void submit(Group group) {
        try { provider.executeContainerWrite(() -> write(group)); }
        catch (Throwable failure) { fail(group, failure); }
    }

    private void write(Group group) {
        Throwable failure = null;
        for (int attempt = 0; attempt < 3; attempt++) {
            try {
                provider.prepareContainerCleanup(group.snapshot, maxBytes - group.retainedBytes, bytes -> {
                    if (batchBytes.addAndGet(bytes) > maxBytes) {
                        batchBytes.addAndGet(-bytes);
                        throw new DBException("Container cleanup waits for retained byte budget");
                    }
                    group.retainedBytes += bytes;
                });
                provider.writeContainerSnapshot(group.snapshot);
                synchronized (this) {
                    // Later local mutations remain dirty; no covered transfer entered during write.
                    for (var entry : group.chunks.entrySet())
                        entry.getValue().clearChangesIfUnmodified(group.snapshot.changes().get(entry.getKey()));
                    group.state = State.DONE;
                    retire(group);
                }
                group.completion.complete(null);
                releaseBatch(group);
                return;
            } catch (Throwable problem) {
                // Snapshot tracks completed cleanup, including unknown write outcomes.
                failure = problem;
            }
        }
        fail(group, failure);
    }

    private void fail(Group group, Throwable failure) {
        synchronized (this) {
            group.state = State.FAILED;
            group.retryAt = System.currentTimeMillis() + retryMillis;
        }
        provider.reportContainerGroupFailure(group.id, group.chunks.keySet(), failure);
        group.completion.completeExceptionally(failure);
        // Keep all membership and immutable bytes: exception cannot prove the batch did not land.
    }

    private synchronized void retire(Group group) {
        group.chunks.keySet().forEach(hash -> { if (members.get(hash) == group) members.remove(hash); });
    }
    private void releaseBatch(Group group) {
        synchronized (this) {
            if (group.snapshot == null || group.released) return;
            group.released = true;
            batchBytes.addAndGet(-group.retainedBytes);
        }
        try { group.snapshot.batch().close(); } catch (Exception ignored) { }
    }

    /** DB close holds its exclusive lifetime lock: no worker can still use a native batch. */
    void dispose() {
        List<Group> groups;
        synchronized (this) {
            groups = members.values().stream().map(this::root).distinct().toList();
            members.clear(); flushes.clear(); preparing.clear();
        }
        for (Group group : groups) {
            group.completion.completeExceptionally(new DBException("Container provider closed without group ACK"));
            releaseBatch(group);
        }
    }
    private synchronized void queue(Group original) {
        Group group = root(original);
        if (!group.queued && group.state != State.WRITING && group.state != State.CAPTURING) {
            group.queued = true;
            flushes.add(group);
        }
    }
    synchronized boolean deferDetach(long hash) {
        Group group = members.get(hash);
        if (group == null) return false;
        queue(group);
        return true;
    }

    /** Called on the provider's main thread, with a bounded number of whole captures per tick. */
    void maintain() {
        if (!primary() || closing || provider.closed) return;
        synchronized (this) {
            long now = System.currentTimeMillis();
            members.values().stream().map(this::root).distinct()
                    .filter(group -> group.state == State.FAILED && now >= group.retryAt).limit(2).forEach(this::queue);
        }
        for (int i = 0; i < 2; i++) {
            Group queued = flushes.poll();
            if (queued == null) break;
            Group group;
            synchronized (this) {
                group = root(queued);
                group.queued = false;
                if (group.active != 0) { queue(group); continue; }
                if (group.state == State.FAILED && group.snapshot != null) {
                    if (System.currentTimeMillis() < group.retryAt) continue;
                    group.completion = new CompletableFuture<>();
                    group.state = State.WRITING;
                    submit(group);
                    continue;
                }
                if (group.state != State.OPEN && group.state != State.FAILED) continue;
            }
            var first = group.chunks.entrySet().iterator().next();
            save(first.getKey(), first.getValue());
            break; // At most one bounded whole-group capture per level tick.
        }
    }

    void prepareClose() {
        List<Group> captures = new ArrayList<>(), retries = new ArrayList<>();
        synchronized (this) {
            if (closing || members.values().stream().anyMatch(group -> root(group).active != 0
                    || root(group).state == State.CAPTURING))
                throw new DBException("Cannot close provider during container capture or mutation");
            if (!primary() && members.values().stream().anyMatch(group -> root(group).snapshot == null))
                throw new DBException("Uncaptured container groups must close on their owner thread");
            closing = true;
            for (Group group : members.values().stream().map(this::root).distinct().toList()) {
                if (group.state == State.OPEN || group.state == State.FAILED && group.snapshot == null) {
                    if (group.state == State.FAILED) group.completion = new CompletableFuture<>();
                    group.state = State.CAPTURING;
                    captures.add(group);
                } else if (group.state == State.FAILED) {
                    group.completion = new CompletableFuture<>(); group.state = State.WRITING;
                    retries.add(group);
                }
            }
        }
        // Snapshot while the level is still valid. Close must never serialize after level=null.
        captures.forEach(this::capture);
        retries.forEach(this::submit);
    }
}
