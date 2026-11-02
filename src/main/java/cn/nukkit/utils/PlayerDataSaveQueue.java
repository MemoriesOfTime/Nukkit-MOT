package cn.nukkit.utils;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

/** Bounded native profile IO. FIFO registration never performs IO or waits for a worker. */
public final class PlayerDataSaveQueue implements AutoCloseable {
    @FunctionalInterface public interface Operation<T> { T run() throws Exception; }
    @FunctionalInterface public interface MigrationOperation<T> { T run(boolean recovering) throws Exception; }
    @FunctionalInterface public interface ObservedRead<T> { T run(BooleanSupplier current) throws Exception; }

    public interface ReadHandle<T> extends AutoCloseable {
        CompletionStage<T> completion();
        boolean isCurrent();
        /** Distinguishes a newer admitted generation from a real read/storage failure. */
        boolean isSuperseded();
        @Override void close();
    }

    /** Admission is distinct from the later storage acknowledgement. */
    public static final class Admission<T> {
        private final PlayerDataSaveQueue owner;
        private final Set<String> identities;
        private final long version;
        private final boolean accepted;
        private final CompletionStage<T> completion;
        private Admission(PlayerDataSaveQueue owner, Set<String> identities, long version,
                          boolean accepted, CompletionStage<T> completion) {
            this.owner = owner; this.identities = identities; this.version = version;
            this.accepted = accepted; this.completion = completion;
        }
        public boolean accepted() { return accepted; }
        public CompletionStage<T> completion() { return completion; }
    }

    public static final class CapacityException extends RejectedExecutionException {
        CapacityException(String message) { super(message); }
    }

    public static final class MigrationException extends IllegalStateException {
        private final String source;
        private final Set<String> keys;
        public MigrationException(String message) { this(message, null); }
        public MigrationException(String message, Throwable cause) { super(message, cause); source = null; keys = Set.of(); }
        private MigrationException(String source, Set<String> keys, Throwable cause) {
            super("Profile migration mutation was not acknowledged", cause); this.source = source; this.keys = keys;
        }
    }

    private final Object gate = new Object();
    private final Map<String, Identity> identities = new HashMap<>();
    private final Set<Write<?>> writes = new HashSet<>();
    private final Set<Read<?>> reads = new HashSet<>();
    private final Executor writeExecutor;
    private final Executor readExecutor;
    private final int capacity;
    private final int identityCapacity;
    private final ThreadLocal<Boolean> onWorker = ThreadLocal.withInitial(() -> false);
    private long sequence;
    private boolean closing;
    private boolean expired;

    private static final class Identity {
        CompletableFuture<Void> tail;
        long version;
        final Set<Read<?>> observations = Collections.newSetFromMap(new IdentityHashMap<>());
        int writes;
        Object migration;
        Throwable failure;
        Operation<?> failedSnapshot;
    }

    private final class Write<T> {
        final Set<String> keys;
        final String source;
        final Operation<T> operation;
        final CompletableFuture<T> result = new CompletableFuture<>();
        final CompletableFuture<Void> finished = new CompletableFuture<>();
        CompletableFuture<Void> predecessors;
        Executor executor;
        long version;
        boolean started;
        boolean terminal;
        boolean blockedByCheckpoint;
        Write(Set<String> keys, String source, Operation<T> operation) {
            this.keys = keys; this.source = source; this.operation = operation;
        }
    }

    private final class Read<T> implements ReadHandle<T> {
        final Set<String> keys;
        final ObservedRead<T> operation;
        final AtomicBoolean current = new AtomicBoolean(true);
        final AtomicBoolean superseded = new AtomicBoolean();
        final CompletableFuture<T> result = new CompletableFuture<>();
        final CompletableFuture<Void> finished = new CompletableFuture<>();
        CompletableFuture<Void> predecessors;
        Runnable task;
        boolean started;
        boolean ioFinished;
        boolean closed;
        boolean released;
        Read(Set<String> keys, ObservedRead<T> operation) { this.keys = keys; this.operation = operation; }
        @Override public CompletionStage<T> completion() { return result.minimalCompletionStage(); }
        @Override public boolean isCurrent() { return current.get(); }
        @Override public boolean isSuperseded() { return superseded.get(); }
        @Override public void close() { closeRead(this); }
    }

    public PlayerDataSaveQueue() { this(2, 256); }

    public PlayerDataSaveQueue(int threads, int capacity) {
        this(newWorkers(threads, capacity, "PlayerData-Write-"),
                newWorkers(threads, capacity, "PlayerData-Read-"), capacity);
    }

    PlayerDataSaveQueue(Executor writes, Executor reads, int capacity) {
        if (capacity < 1 || capacity > Integer.MAX_VALUE / 2) throw new IllegalArgumentException("Invalid capacity");
        this.writeExecutor = Objects.requireNonNull(writes);
        this.readExecutor = Objects.requireNonNull(reads);
        this.capacity = capacity;
        this.identityCapacity = capacity * 2;
    }

    private static ThreadPoolExecutor newWorkers(int threads, int capacity, String prefix) {
        if (threads < 1 || capacity < 1 || capacity > Integer.MAX_VALUE / 2) throw new IllegalArgumentException("Positive limits required");
        AtomicInteger ids = new AtomicInteger();
        return new ThreadPoolExecutor(threads, threads, 0, TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(capacity), task -> {
                    Thread thread = new Thread(task, prefix + ids.incrementAndGet());
                    thread.setDaemon(true);
                    return thread;
                }, new ThreadPoolExecutor.AbortPolicy());
    }

    public boolean isWorkerThread() { return onWorker.get(); }

    public <T> CompletionStage<T> submit(String identity, Operation<T> operation) {
        return admit(identity, operation).completion();
    }

    public <T> Admission<T> admit(String identity, Operation<T> operation) {
        return registerWrite(Set.of(key(identity)), null, operation, null, null);
    }

    /** Native migration consumes a single position shared by all source/target/name dependencies. */
    public <T> Admission<T> migrate(String source, Collection<String> dependencies, Operation<T> operation) {
        return migrate(source, dependencies, operation, null);
    }

    /** Legacy serializer migration keeps its original MAIN read affinity without worker waits. */
    public <T> Admission<T> migrate(String source, Collection<String> dependencies, Operation<T> operation, Executor executor) {
        Set<String> keys = keys(dependencies);
        String previous = key(source);
        keys.add(previous);
        return registerWrite(keys, previous, operation, executor, null);
    }

    /** A matching failed native mutation requires a fresh verified storage ACK, never SKIPPED alone. */
    public <T> Admission<T> migrateVerified(String source, Collection<String> dependencies,
                                           MigrationOperation<T> operation, Executor executor) {
        Set<String> keys = keys(dependencies); String previous = key(source); keys.add(previous);
        AtomicBoolean recovering = new AtomicBoolean();
        return registerWrite(keys, previous, () -> operation.run(recovering.get()), executor, recovering);
    }

    private <T> Admission<T> registerWrite(Set<String> keys, String source, Operation<T> operation, Executor executor, AtomicBoolean recovering) {
        Write<T> entry = new Write<>(Set.copyOf(keys), source, Objects.requireNonNull(operation));
        entry.executor = executor == null ? writeExecutor : executor;
        Throwable refused;
        synchronized (gate) {
            refused = admissionFailure(keys, writes.size() >= capacity, true);
            if (refused == null && source == null && keys.stream().map(identities::get).filter(Objects::nonNull).anyMatch(state -> state.migration != null))
                refused = new RejectedExecutionException("Profile identity is being migrated");
            if (refused == null) {
                // Install the whole union atomically. No worker waits for an earlier runnable to start.
                entry.predecessors = preceding(keys);
                entry.version = ++sequence;
                for (String key : keys) {
                    Identity identity = state(key);
                    for (Read<?> read : identity.observations) { read.superseded.set(true); read.current.set(false); }
                    identity.tail = entry.finished;
                    identity.version = entry.version;
                    identity.writes++;
                    if (source != null) identity.migration = entry;
                }
                writes.add(entry);
            }
        }
        if (refused != null) return rejected(refused);
        // FIFO prerequisites become eligible off-pool. B/C cannot occupy workers waiting for A.
        entry.predecessors.whenComplete((ignored, failure) -> {
            if (source == null && hasMigrationFailure(failure)) {
                finishWrite(entry, null, new MigrationException("Profile migration predecessor was not acknowledged", unwrap(failure)));
                return;
            }
            if (source != null && failure != null) {
                List<Throwable> barriers;
                synchronized (gate) {
                    barriers = entry.keys.stream().map(identities::get).filter(Objects::nonNull)
                            .map(state -> state.failure).filter(Objects::nonNull).distinct().toList();
                }
                boolean matching = !barriers.isEmpty() && barriers.stream().allMatch(error -> sameMigration(error, source, entry.keys));
                if (!matching || recovering == null) {
                    // No mutation ran: preserve an earlier failed checkpoint and its snapshot as the authority.
                    entry.blockedByCheckpoint = true;
                    finishWrite(entry, null, unwrap(failure));
                    return;
                }
                recovering.set(true);
            }
            dispatchWrite(entry);
        });
        return new Admission<>(this, entry.keys, entry.version, true, entry.result.minimalCompletionStage());
    }

    public <T> ReadHandle<T> read(Collection<String> dependencies, ObservedRead<T> operation) {
        Set<String> keys = keys(dependencies);
        Read<T> entry = new Read<>(Set.copyOf(keys), Objects.requireNonNull(operation));
        Throwable refused;
        synchronized (gate) {
            refused = admissionFailure(keys, reads.size() >= capacity, false);
            if (refused == null) {
                entry.predecessors = preceding(keys);
                for (String key : keys) state(key).observations.add(entry);
                reads.add(entry);
            }
        }
        if (refused != null) {
            entry.current.set(false); entry.closed = entry.ioFinished = entry.released = true;
            entry.result.completeExceptionally(refused); entry.finished.complete(null);
            return entry;
        }
        entry.predecessors.whenComplete((ignored, failure) -> {
            boolean cancelled;
            synchronized (gate) { cancelled = entry.closed; }
            if (cancelled) finishRead(entry, null, new CancellationException("Profile read closed before activation"));
            else if (failure != null) finishRead(entry, null,
                    new IllegalStateException("Cannot read a profile after an unsuccessful save", unwrap(failure)));
            else dispatchRead(entry);
        });
        return entry;
    }

    private Throwable admissionFailure(Set<String> keys, boolean full, boolean write) {
        if (closing) return new RejectedExecutionException("Profile IO is closing");
        long fresh = keys.stream().filter(k -> !identities.containsKey(k)).count();
        if (full || identities.size() + fresh > identityCapacity)
            return new CapacityException(write ? "Profile write capacity exhausted" : "Profile read capacity exhausted");
        return null;
    }

    private Identity state(String key) { return identities.computeIfAbsent(key, ignored -> new Identity()); }

    private CompletableFuture<Void> preceding(Set<String> keys) {
        return CompletableFuture.allOf(keys.stream().map(identities::get).filter(Objects::nonNull).map(state -> state.tail)
                .filter(Objects::nonNull).distinct().toArray(CompletableFuture[]::new));
    }

    private <T> void dispatchWrite(Write<T> entry) {
        synchronized (gate) { if (entry.terminal) return; }
        try {
            entry.executor.execute(() -> {
                synchronized (gate) {
                    if (entry.terminal) return;
                    if (!expired) entry.started = true;
                }
                if (!entry.started) { finishWrite(entry, null, new TimeoutException("Profile IO deadline expired")); return; }
                T value = null; Throwable failure = null;
                onWorker.set(true);
                try { value = entry.operation.run(); }
                catch (Throwable error) { failure = error; }
                finally { onWorker.remove(); }
                finishWrite(entry, value, failure);
            });
        } catch (RuntimeException | Error failure) { finishWrite(entry, null, failure); }
    }

    private <T> void finishWrite(Write<T> entry, T value, Throwable failure) {
        if (entry.source != null && entry.started && failure != null)
            failure = new MigrationException(entry.source, entry.keys, failure);
        synchronized (gate) {
            if (entry.terminal) return;
            entry.terminal = true;
            writes.remove(entry);
            for (String key : entry.keys) {
                Identity identity = identities.get(key);
                identity.writes--;
                if (identity.migration == entry) identity.migration = null;
                if (entry.blockedByCheckpoint) {
                    // An operation that never ran cannot replace a failed snapshot or poison a healthy target.
                    if (identity.failure == null && identity.tail == entry.finished) identity.tail = null;
                    cleanIdentity(key, identity);
                    continue;
                }
                identity.failure = failure;
                identity.failedSnapshot = failure == null ? null : entry.operation;
                if (identity.tail == entry.finished && failure == null) identity.tail = null;
                cleanIdentity(key, identity);
            }
        }
        // No plugin/completion observer runs under the queue monitor.
        if (failure == null) { entry.finished.complete(null); entry.result.complete(value); }
        else { entry.finished.completeExceptionally(failure); entry.result.completeExceptionally(failure); }
    }

    private <T> void dispatchRead(Read<T> entry) {
        synchronized (gate) {
            if (entry.closed || entry.ioFinished) return;
            entry.task = () -> {
                synchronized (gate) {
                    if (entry.closed || entry.ioFinished) return;
                    entry.started = true;
                }
                T value = null; Throwable failure = null;
                onWorker.set(true);
                try {
                    if (!entry.current.get()) throw new CancellationException("Profile observation superseded");
                    value = entry.operation.run(entry.current::get);
                } catch (Throwable error) { failure = error; }
                finally { onWorker.remove(); }
                finishRead(entry, value, failure);
            };
        }
        try { readExecutor.execute(entry.task); }
        catch (RuntimeException | Error failure) { finishRead(entry, null, failure); }
    }

    private <T> void finishRead(Read<T> entry, T value, Throwable failure) {
        synchronized (gate) {
            if (entry.ioFinished) return;
            entry.ioFinished = true;
            if (failure != null) entry.current.set(false);
            if (entry.closed) releaseRead(entry);
        }
        entry.finished.complete(null);
        if (failure == null) entry.result.complete(value);
        else entry.result.completeExceptionally(failure);
    }

    private void closeRead(Read<?> entry) {
        boolean finish;
        synchronized (gate) {
            if (entry.closed) return;
            entry.closed = true; entry.superseded.set(true); entry.current.set(false);
            for (String key : entry.keys) {
                Identity identity = identities.get(key);
                if (identity != null) { identity.observations.remove(entry); cleanIdentity(key, identity); }
            }
            // A waiting predecessor still retains its completion node: keep the bounded credit until it exits.
            finish = !entry.started && !entry.ioFinished && (entry.task != null || entry.predecessors.isDone());
            if (finish) {
                if (readExecutor instanceof ThreadPoolExecutor executor && entry.task != null) executor.remove(entry.task);
                entry.ioFinished = true;
            }
            if (entry.ioFinished) releaseRead(entry);
        }
        entry.result.completeExceptionally(new CancellationException("Profile read closed"));
        if (finish) entry.finished.complete(null);
    }

    private void releaseRead(Read<?> entry) {
        if (entry.released) return;
        entry.released = true; reads.remove(entry);
    }

    private void cleanIdentity(String key, Identity identity) {
        if (identity.writes == 0 && identity.observations.isEmpty() && identity.failure == null
                && identity.migration == null) identities.remove(key, identity);
    }


    /** A native login may account for its own initial save without accepting a later external write. */
    public boolean isLatest(Admission<?> admission) {
        if (admission == null || !admission.accepted || admission.owner != this) return false;
        synchronized (gate) {
            return admission.identities.stream().allMatch(key -> {
                Identity identity = identities.get(key);
                return identity != null && identity.version == admission.version;
            });
        }
    }

    public CompletionStage<Void> flush(String identity) {
        synchronized (gate) {
            Identity state = identities.get(key(identity));
            return state == null || state.tail == null ? CompletableFuture.completedStage(null)
                    : state.tail.minimalCompletionStage();
        }
    }

    public CompletionStage<Void> flush(Collection<String> dependencies) {
        synchronized (gate) { return preceding(keys(dependencies)).minimalCompletionStage(); }
    }

    public CompletionStage<Void> flushAll() {
        synchronized (gate) {
            return CompletableFuture.allOf(identities.values().stream().map(state -> state.tail)
                    .filter(Objects::nonNull).distinct().toArray(CompletableFuture[]::new)).minimalCompletionStage();
        }
    }

    /** Deadline expiry never acknowledges or interrupts an already-running write. */
    public boolean close(Duration timeout) {
        List<Read<?>> observed;
        CompletableFuture<Void> drained;
        synchronized (gate) {
            closing = true;
            observed = new ArrayList<>(reads);
            drained = CompletableFuture.allOf(java.util.stream.Stream.concat(
                    writes.stream().map(entry -> entry.finished.handle((ok, error) -> null)),
                    reads.stream().map(entry -> entry.finished.handle((ok, error) -> null)))
                    .toArray(CompletableFuture[]::new));
        }
        observed.forEach(Read::close);
        boolean finished = true;
        try { drained.get(Math.max(0, timeout.toNanos()), TimeUnit.NANOSECONDS); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); finished = false; }
        catch (ExecutionException | TimeoutException failure) { finished = false; }
        synchronized (gate) { expired = true; finished &= identities.values().stream().noneMatch(state -> state.failure != null); }
        if (writeExecutor instanceof ExecutorService executor) executor.shutdown();
        if (readExecutor instanceof ExecutorService executor) executor.shutdown();
        return finished;
    }

    @Override public void close() { close(Duration.ofSeconds(5)); }

    private static Set<String> keys(Collection<String> values) {
        Set<String> keys = new HashSet<>(); values.forEach(value -> keys.add(key(value)));
        if (keys.isEmpty()) throw new IllegalArgumentException("At least one profile identity required");
        return keys;
    }
    private static String key(String identity) { return Objects.requireNonNull(identity).toLowerCase(Locale.ROOT); }
    private static <T> Admission<T> rejected(Throwable error) {
        return new Admission<>(null, Set.of(), 0, false, CompletableFuture.failedStage(error));
    }
    private static Throwable unwrap(Throwable error) {
        while ((error instanceof CompletionException || error instanceof ExecutionException) && error.getCause() != null)
            error = error.getCause();
        return error;
    }
    private static boolean sameMigration(Throwable failure, String source, Set<String> keys) {
        boolean found = false;
        while (failure != null) {
            if (failure instanceof MigrationException migration && migration.source != null) {
                if (!migration.source.equals(source) || !migration.keys.equals(keys)) return false;
                found = true;
            }
            failure = failure.getCause();
        }
        return found;
    }
    private static boolean hasMigrationFailure(Throwable failure) {
        while (failure != null) {
            if (failure instanceof MigrationException) return true;
            failure = failure.getCause();
        }
        return false;
    }
}
