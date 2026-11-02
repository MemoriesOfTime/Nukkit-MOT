package cn.nukkit.level.format.leveldb;

import org.iq80.leveldb.DB;
import cn.nukkit.nbt.tag.CompoundTag;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/** Owns one unopened/ready world DB until its main-thread Level is fully registered. */
public final class PreparedLevelDB {
    private static final ConcurrentHashMap<Path, PreparedLevelDB> RESERVED = new ConcurrentHashMap<>();
    private static final ThreadLocal<PreparedLevelDB> ACTIVATING = new ThreadLocal<>();

    @FunctionalInterface
    interface Opener { OpenedDatabase open(Path database) throws IOException; }

    record OpenedDatabase(DB database, CompoundTag metadata) {
        OpenedDatabase {
            java.util.Objects.requireNonNull(database, "database");
            java.util.Objects.requireNonNull(metadata, "metadata");
        }
    }

    private final Path path;
    private final Thread mainThread;
    private final Opener opener;
    private final CompletableFuture<Void> released = new CompletableFuture<>();
    private DB database;
    private CompoundTag metadata;
    private LevelDBProvider provider;
    private boolean opening;
    private boolean borrowed;
    private boolean cancelled;
    private boolean committed;
    private boolean closing;

    public static PreparedLevelDB reserve(Path finalWorldPath) throws IOException {
        return reserve(finalWorldPath, db -> {
            // Read first: if metadata is invalid, there is no native handle to leak.
            CompoundTag metadata = LevelDBProvider.readLevelData(db.getParent());
            return new OpenedDatabase(LevelDBProvider.openDB(db.toFile()), metadata);
        });
    }

    static PreparedLevelDB reserve(Path finalWorldPath, Opener opener) throws IOException {
        Path canonical = finalWorldPath.toFile().getCanonicalFile().toPath();
        PreparedLevelDB token = new PreparedLevelDB(canonical, opener);
        if (RESERVED.putIfAbsent(canonical, token) != null) {
            throw new IllegalStateException("World database preparation already reserved: " + canonical);
        }
        return token;
    }

    private PreparedLevelDB(Path path, Opener opener) {
        this.path = path;
        this.opener = opener;
        this.mainThread = Thread.currentThread();
    }

    /** Worker only, after the final directory rename; no Level or registry access. */
    public void open() throws IOException {
        if (Thread.currentThread() == mainThread) {
            throw new IllegalStateException("Native database open must run off the reservation thread");
        }
        synchronized (this) {
            if (cancelled || committed || opening || database != null) {
                throw new IllegalStateException("Database preparation is not openable");
            }
            opening = true;
        }
        OpenedDatabase opened = null;
        try {
            opened = opener.open(path.resolve("db"));
        } finally {
            boolean dispose;
            synchronized (this) {
                database = opened == null ? null : opened.database();
                metadata = opened == null ? null : opened.metadata();
                opening = false;
                dispose = cancelled;
                if (dispose) closing = true;
            }
            if (dispose) dispose();
        }
    }

    /** Only this main-thread call may hand the reserved DB to a provider constructor. */
    public <T> T activate(Supplier<T> initializeLevel) {
        requireMain();
        synchronized (this) {
            if (cancelled || committed || opening || database == null || borrowed) {
                throw new IllegalStateException("World database is not ready for activation");
            }
        }
        if (ACTIVATING.get() != null) throw new IllegalStateException("Nested prepared world activation");
        ACTIVATING.set(this);
        try {
            return initializeLevel.get();
        } finally {
            ACTIVATING.remove();
        }
    }

    static PreparedLevelDB forConstructor(String path) throws IOException {
        PreparedLevelDB token = RESERVED.get(Path.of(path).toFile().getCanonicalFile().toPath());
        if (token != null && ACTIVATING.get() != token) {
            throw new IllegalStateException("World database is being prepared: " + path);
        }
        return token;
    }

    synchronized CompoundTag metadata() {
        requireMain();
        if (cancelled || committed || opening || borrowed || database == null || metadata == null
                || ACTIVATING.get() != this) {
            throw new IllegalStateException("Prepared metadata cannot be adopted");
        }
        return metadata;
    }

    synchronized DB borrow() {
        requireMain();
        if (cancelled || committed || opening || borrowed || database == null || ACTIVATING.get() != this) {
            throw new IllegalStateException("Prepared database cannot be borrowed");
        }
        borrowed = true;
        return database;
    }

    synchronized void constructed(LevelDBProvider provider) {
        this.provider = provider;
    }

    /** Main only, after successful Level initialization, registration and owner configuration. */
    public synchronized void commit() {
        requireMain();
        if (cancelled || committed || !borrowed || provider == null) {
            throw new IllegalStateException("Prepared world was not fully constructed");
        }
        committed = true;
        database = null;
        metadata = null;
        provider = null;
        RESERVED.remove(path, this);
        released.complete(null);
    }

    /**
     * Cancel on the owner thread, retaining the normal provider save/drain lifecycle.
     * Native close is asynchronous; the path stays gated until it actually succeeds.
     */
    public CompletionStage<Void> cancel() {
        requireMain();
        synchronized (this) {
            if (committed || cancelled) return released.minimalCompletionStage();
            cancelled = true;
            if (opening) return released.minimalCompletionStage();
            if (closing) return released.minimalCompletionStage();
            closing = true;
        }
        // Provider lifecycle (chunks, task cancellation) stays on the main thread.
        try {
            if (provider != null) {
                provider.deferNativeCloseWait();
                provider.close();
            }
        } catch (Throwable error) {
            released.completeExceptionally(error);
            return released.minimalCompletionStage();
        }
        Thread cleanup = new Thread(this::dispose, "Prepared LevelDB close for " + path.getFileName());
        cleanup.setDaemon(true);
        cleanup.start();
        return released.minimalCompletionStage();
    }

    private void dispose() {
        try {
            if (provider != null) {
                provider.databaseClosed().toCompletableFuture().join();
            } else if (database != null) {
                database.close();
            }
            synchronized (this) {
                database = null;
                metadata = null;
                provider = null;
            }
            released.complete(null);
        } catch (Throwable error) {
            // An unknown/failed close never permits another load or directory rename.
            released.completeExceptionally(error);
        }
    }

    /** After cancellation and directory recovery, remove the gate; never before confirmed close. */
    public void release() {
        requireMain();
        synchronized (this) {
            if (!cancelled || !released.isDone() || released.isCompletedExceptionally()) {
                throw new IllegalStateException("Native close has not been confirmed");
            }
        }
        RESERVED.remove(path, this);
    }

    private void requireMain() {
        if (Thread.currentThread() != mainThread) {
            throw new IllegalStateException("World activation must stay on the reservation thread");
        }
    }
}
