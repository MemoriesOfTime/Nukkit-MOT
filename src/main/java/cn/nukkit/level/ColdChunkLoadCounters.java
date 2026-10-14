package cn.nukkit.level;

import cn.nukkit.Server;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** Optional, bounded RAM diagnostics. Call only after a loaded-chunk lookup misses. */
public final class ColdChunkLoadCounters {
    public enum Kind { SYNCHRONOUS, ASYNCHRONOUS, DEFERRED }

    public record Snapshot(long started, long completed, long totalNanos, long maxNanos) {}

    private static final int MAX_SITES = 256;
    private static final String OVERFLOW = "world=*|kind=*|thread=*|site=<overflow>";
    private static final Map<String, Counter> COUNTERS = new LinkedHashMap<>();
    private static final Scope NOOP = new Scope(null, 0);
    private static final Set<String> ACCESSORS = Set.of("getChunk", "getChunkIfLoaded", "loadChunk",
            "forceLoadChunk", "forceLoadChunkSynchronously", "queueAsyncChunkLoad", "requestChunkLoad", "loadChunkAsync",
            "getBlock", "getBlockIfLoaded", "getBlockIdAt", "getBlockDataAt", "getFullBlock");

    private ColdChunkLoadCounters() {}

    public static Scope begin(String world, Kind kind) {
        Server server = Server.getInstance();
        if (server == null || !server.coldChunkLoadCounters) return NOOP;
        long startedAt = System.nanoTime();
        String site = Walker.INSTANCE.walk(frames -> frames.filter(ColdChunkLoadCounters::meaningful)
                .limit(4).map(frame -> frame.getClassName() + "." + frame.getMethodName() + ":" + frame.getLineNumber())
                .collect(Collectors.joining(" <- ")));
        String key = "world=" + world + "|kind=" + kind + "|thread="
                + (server.isPrimaryThread() ? "main" : "worker") + "|site=" + site;
        synchronized (COUNTERS) {
            Counter counter = COUNTERS.get(key);
            if (counter == null) {
                if (COUNTERS.size() >= MAX_SITES - 1) key = OVERFLOW;
                counter = COUNTERS.computeIfAbsent(key, ignored -> new Counter());
            }
            counter.started++;
            return new Scope(counter, startedAt);
        }
    }

    public static Map<String, Snapshot> snapshot() {
        synchronized (COUNTERS) {
            Map<String, Snapshot> result = new LinkedHashMap<>();
            COUNTERS.forEach((key, counter) -> result.put(key,
                    new Snapshot(counter.started, counter.completed, counter.totalNanos, counter.maxNanos)));
            return Map.copyOf(result);
        }
    }

    /** In-flight scopes from the previous sampling interval finish in their detached counters. */
    public static void reset() {
        synchronized (COUNTERS) {
            COUNTERS.clear();
        }
    }

    private static boolean meaningful(StackWalker.StackFrame frame) {
        String name = frame.getClassName();
        if (name.equals(ColdChunkLoadCounters.class.getName())
                || name.startsWith(ColdChunkLoadCounters.class.getName() + "$")
                || name.startsWith("cn.nukkit.level.format.")
                || name.equals("cn.nukkit.block.Block") || name.equals("cn.nukkit.level.Position")
                || name.startsWith("java.") || name.startsWith("jdk.")) return false;
        return !name.equals("cn.nukkit.level.Level") || !ACCESSORS.contains(frame.getMethodName());
    }

    private static final class Walker {
        static final StackWalker INSTANCE = StackWalker.getInstance();
    }

    private static final class Counter {
        long started, completed, totalNanos, maxNanos;
    }

    public static final class Scope implements AutoCloseable {
        private final Counter counter;
        private final long startedAt;
        private boolean closed;

        private Scope(Counter counter, long startedAt) {
            this.counter = counter;
            this.startedAt = startedAt;
        }

        @Override
        public void close() {
            if (counter == null) return;
            long elapsed = Math.max(0, System.nanoTime() - startedAt);
            synchronized (COUNTERS) {
                if (closed) return;
                closed = true;
                counter.completed++;
                counter.totalNanos += elapsed;
                counter.maxNanos = Math.max(counter.maxNanos, elapsed);
            }
        }
    }
}
