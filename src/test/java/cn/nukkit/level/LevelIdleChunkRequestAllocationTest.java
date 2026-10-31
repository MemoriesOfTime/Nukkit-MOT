package cn.nukkit.level;

import cn.nukkit.GameVersion;
import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.network.protocol.BatchPacket;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.utils.MainLogger;
import com.sun.management.ThreadMXBean;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.objects.Object2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.objenesis.ObjenesisStd;

import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.management.ManagementFactory;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LevelIdleChunkRequestAllocationTest {
    private static final GameVersion VERSION = GameVersion.V1_20_0;
    private static final GameVersion[] VERSIONS = {
            GameVersion.V1_20_0, GameVersion.V1_20_10, GameVersion.V1_20_30,
            GameVersion.V1_20_40, GameVersion.V1_20_50, GameVersion.V1_20_60,
            GameVersion.V1_20_70, GameVersion.V1_20_80, GameVersion.V1_21_0,
            GameVersion.V1_21_20, GameVersion.V1_21_30, GameVersion.V1_21_40,
            GameVersion.V1_21_50, GameVersion.V1_21_60, GameVersion.V1_21_70,
            GameVersion.V1_21_80
    };

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 16})
    void emptyRequestsStayWithinNativeAllocationBudget(int buckets, @TempDir Path directory)
            throws Exception {
        java.lang.management.ThreadMXBean bean = ManagementFactory.getThreadMXBean();
        assumeTrue(bean instanceof ThreadMXBean mx && mx.isThreadAllocatedMemorySupported());

        // Mockito's instrumentation in the parent suite changes native allocation counts.
        // The child invokes the actual private method on an exact, uninstrumented Level.
        String classpath = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
        Path outputFile = directory.resolve("idle-chunk-requests-" + buckets + ".log");
        Process process = new ProcessBuilder(
                Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                "-ea", "-Xms256m", "-Xmx512m", "-cp", classpath,
                AllocationProbe.class.getName(), Integer.toString(buckets))
                .redirectErrorStream(true).redirectOutput(outputFile.toFile()).start();
        try {
            boolean completed = process.waitFor(60, TimeUnit.SECONDS);
            String output;
            try (InputStream stream = Files.newInputStream(outputFile)) {
                output = new String(stream.readNBytes(65_536), StandardCharsets.UTF_8);
            }
            assertTrue(completed, "Native allocation child timed out\n" + output);
            assertEquals(0, process.exitValue(), output);
        } finally {
            if (process.isAlive()) {
                process.destroyForcibly();
                assertTrue(process.waitFor(10, TimeUnit.SECONDS), "Native allocation child must be reaped");
            }
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 16})
    void emptyProtocolBucketsDoNotCreateTasksOrRecipientQueues(int count) throws Throwable {
        ContractFixture fixture = new ContractFixture();
        LongSet[] owners = new LongSet[count];
        for (int i = 0; i < count; i++) owners[i] = pending(fixture.level, VERSIONS[i]);

        process(fixture.level);

        assertEquals(count, fixture.buckets.pending.size());
        for (int i = 0; i < count; i++) {
            assertSame(owners[i], fixture.buckets.pending.get(VERSIONS[i]));
            assertTrue(owners[i].isEmpty());
        }
        assertTrue(fixture.buckets.tasks.isEmpty());
        assertTrue(fixture.buckets.queues.isEmpty());
        fixture.assertNoRequestSubmission();
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "empty", "disconnected"})
    void rejectedPendingRequestsStillCompleteTheirCleanup(String kind) throws Throwable {
        ContractFixture fixture = new ContractFixture();
        long hash = Level.chunkHash(3, 5);
        LongSet pending = pending(fixture.level, VERSION);
        pending.add(hash);
        ConcurrentMap<Long, Int2ObjectMap<Player>> queue = queue(fixture.level, VERSION);
        if (!kind.equals("missing")) {
            Int2ObjectMap<Player> recipients = new Int2ObjectOpenHashMap<>();
            if (kind.equals("disconnected")) {
                Player player = fixture.player(1, hash);
                when(player.isConnected()).thenReturn(false);
                recipients.put(1, player);
            }
            queue.put(hash, recipients);
        }
        fixture.buckets.started.put(hash, 99L);

        process(fixture.level);

        assertTrue(pending.isEmpty(), "Rejected requests must not remain pending after the drain");
        assertFalse(queue.containsKey(hash), "Empty or disconnected recipient owners must be removed");
        assertTrue(tasks(fixture.level, VERSION).isEmpty());
        assertEquals(99L, fixture.buckets.started.get(hash), "Recipient rejection precedes timeout handling");
        fixture.assertNoRequestSubmission();
    }

    @Test
    void alreadyRunningTaskKeepsItsOwnerAndTimestampWithoutResubmission() throws Throwable {
        ContractFixture fixture = new ContractFixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash);
        fixture.level.requestChunk(3, 5, player);
        Int2ObjectMap<Player> owner = queue(fixture.level, VERSION).get(hash);
        LongSet tasks = tasks(fixture.level, VERSION);
        tasks.add(hash);
        fixture.buckets.started.put(hash, 99L);

        process(fixture.level);

        assertTrue(pending(fixture.level, VERSION).isEmpty());
        assertTrue(tasks.contains(hash));
        assertSame(owner, queue(fixture.level, VERSION).get(hash));
        assertEquals(99L, fixture.buckets.started.get(hash));
        fixture.assertNoRequestSubmission();
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void expiredTaskClearsItsOldTimestampBeforeRetryingTheSameRecipients() throws Throwable {
        ContractFixture fixture = new ContractFixture();
        long hash = Level.chunkHash(3, 5);
        Player player = fixture.player(1, hash);
        fixture.level.requestChunk(3, 5, player);
        Int2ObjectMap<Player> owner = queue(fixture.level, VERSION).get(hash);
        LongSet tasks = tasks(fixture.level, VERSION);
        tasks.add(hash);
        fixture.buckets.started.put(hash, 99L);
        when(fixture.server.getTick()).thenReturn(300);
        MainLogger logger = mock(MainLogger.class);
        when(fixture.server.getLogger()).thenReturn(logger);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        doAnswer(call -> {
            assertFalse(fixture.buckets.started.containsKey(hash), "Expired timestamp is removed before lookup");
            assertTrue(tasks.contains(hash), "The retry has reserved a new task before lookup");
            return chunk;
        }).when(fixture.level).getChunk(3, 5);

        process(fixture.level);

        assertTrue(pending(fixture.level, VERSION).isEmpty());
        assertTrue(tasks.contains(hash));
        assertSame(owner, queue(fixture.level, VERSION).get(hash));
        assertEquals(300L, fixture.buckets.started.get(hash));
        assertEquals(2L, fixture.buckets.generations.get(hash), "Retry must retain upstream generation fencing");
        verify(fixture.level).getChunk(3, 5);
        verify(fixture.provider).requestChunkTask(
                argThat((ObjectSet<GameVersion> versions) -> versions.equals(Set.of(VERSION))), eq(3), eq(5), eq(2L));
        verify(logger).warning(contains("forcing retry"));
        verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
    }

    @Test
    void oneDrainCoalescesBothVersionsForEachCoordinate() throws Throwable {
        ContractFixture fixture = new ContractFixture();
        GameVersion secondVersion = GameVersion.V1_26_50;
        long firstHash = Level.chunkHash(3, 5);
        long secondHash = Level.chunkHash(4, 5);
        Player first = fixture.player(1, firstHash);
        Player second = fixture.player(2, firstHash);
        when(second.getGameVersion()).thenReturn(secondVersion);
        first.usedChunks.put(secondHash, false);
        second.usedChunks.put(secondHash, false);
        for (Player player : new Player[]{first, second}) {
            fixture.level.requestChunk(3, 5, player);
            fixture.level.requestChunk(4, 5, player);
        }
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(3, 5);
        doReturn(mock(BaseFullChunk.class)).when(fixture.level).getChunk(4, 5);

        process(fixture.level);

        Set<GameVersion> expectedVersions = Set.of(VERSION, secondVersion);
        verify(fixture.provider).requestChunkTask(
                argThat((ObjectSet<GameVersion> versions) -> versions.equals(expectedVersions)), eq(3), eq(5), eq(1L));
        verify(fixture.provider).requestChunkTask(
                argThat((ObjectSet<GameVersion> versions) -> versions.equals(expectedVersions)), eq(4), eq(5), eq(1L));
        verify(fixture.provider, times(2)).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
        verify(fixture.level, times(2)).getChunk(3, 5);
        verify(fixture.level, times(2)).getChunk(4, 5);
        for (Player player : new Player[]{first, second}) {
            GameVersion version = player.getGameVersion();
            assertTrue(pending(fixture.level, version).isEmpty());
            for (long hash : new long[]{firstHash, secondHash}) {
                assertTrue(tasks(fixture.level, version).contains(hash));
                assertSame(player, queue(fixture.level, version).get(hash).get(player.getLoaderId()));
                assertEquals(100L, fixture.buckets.started.get(hash));
            }
            verify(player, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        }
    }

    @Test
    void firstEligibleRequestAfterARejectedRequestStillGetsDelivered() throws Throwable {
        ContractFixture fixture = new ContractFixture();
        long rejectedHash = Level.chunkHash(3, 5);
        long eligibleHash = Level.chunkHash(4, 5);
        Player rejected = fixture.player(1, rejectedHash);
        when(rejected.isConnected()).thenReturn(false);
        Player eligible = fixture.player(2, eligibleHash);
        fixture.level.requestChunk(3, 5, rejected);
        fixture.level.requestChunk(4, 5, eligible);
        LongSet ordered = new LongLinkedOpenHashSet();
        ordered.add(rejectedHash);
        ordered.add(eligibleHash);
        fixture.buckets.pending.put(VERSION, ordered);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        BatchPacket packet = new BatchPacket();
        doReturn(chunk).when(fixture.level).getChunk(4, 5);
        when(chunk.getChunkPacket(VERSION)).thenReturn(packet);

        process(fixture.level);

        assertTrue(ordered.isEmpty());
        assertFalse(queue(fixture.level, VERSION).containsKey(rejectedHash));
        assertFalse(queue(fixture.level, VERSION).containsKey(eligibleHash));
        assertFalse(tasks(fixture.level, VERSION).contains(eligibleHash));
        verify(fixture.level, never()).getChunk(3, 5);
        verify(eligible).sendChunk(4, 5, packet);
        verify(rejected, never()).sendChunk(anyInt(), anyInt(), any(DataPacket.class));
        verify(fixture.provider, never()).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
    }

    private static final class Buckets {
        final Object2ObjectMap<GameVersion, LongSet> pending = new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, LongSet> tasks = new Object2ObjectOpenHashMap<>();
        final Object2ObjectMap<GameVersion, ConcurrentMap<Long, Int2ObjectMap<Player>>> queues =
                new Object2ObjectOpenHashMap<>();
        final Long2LongOpenHashMap started = new Long2LongOpenHashMap();
        final Long2LongOpenHashMap generations = new Long2LongOpenHashMap();

        void install(Level level) throws Exception {
            set(level, "pendingChunkRequests", pending);
            set(level, "chunkSendTasks", tasks);
            set(level, "chunkSendQueues", queues);
            set(level, "chunkSendTaskStartTick", started);
            set(level, "chunkSendGeneration", generations);
        }
    }

    private static final class ContractFixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final LevelProvider provider = mock(LevelProvider.class);
        final Server server = mock(Server.class);
        final Buckets buckets = new Buckets();

        ContractFixture() throws Exception {
            buckets.install(level);
            set(level, "server", server);
            set(level, "provider", provider);
            when(server.getTick()).thenReturn(100);
            doReturn(0).when(level).getDimension();
        }

        Player player(int loader, long hash) throws Exception {
            Player player = mock(Player.class);
            Field used = Player.class.getDeclaredField("usedChunks");
            used.setAccessible(true);
            used.set(player, new HashMap<Long, Boolean>());
            player.usedChunks.put(hash, false);
            when(player.getLoaderId()).thenReturn(loader);
            when(player.getGameVersion()).thenReturn(VERSION);
            when(player.isConnected()).thenReturn(true);
            when(player.getLevel()).thenReturn(level);
            return player;
        }

        void assertNoRequestSubmission() {
            verify(level, never()).getChunk(anyInt(), anyInt());
            verify(provider, never()).requestChunkTask(any(ObjectSet.class), anyInt(), anyInt(), anyLong());
        }
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static MethodHandle method(String name, Class<?> result, Class<?>... parameters)
            throws ReflectiveOperationException {
        return MethodHandles.privateLookupIn(Level.class, MethodHandles.lookup())
                .findVirtual(Level.class, name, MethodType.methodType(result, parameters));
    }

    private static void process(Level level) throws Throwable {
        method("processChunkRequest", void.class).invokeExact(level);
    }

    private static LongSet pending(Level level, GameVersion version) throws Throwable {
        return (LongSet) method("getPendingChunkRequests", LongSet.class, GameVersion.class)
                .invokeExact(level, version);
    }

    private static LongSet tasks(Level level, GameVersion version) throws Throwable {
        return (LongSet) method("getChunkSendTasks", LongSet.class, GameVersion.class)
                .invokeExact(level, version);
    }

    @SuppressWarnings("unchecked")
    private static ConcurrentMap<Long, Int2ObjectMap<Player>> queue(Level level, GameVersion version)
            throws Throwable {
        return (ConcurrentMap<Long, Int2ObjectMap<Player>>) method("getChunkSendQueue", ConcurrentMap.class,
                GameVersion.class).invokeExact(level, version);
    }

    /** Plain child entry point: no Mockito initialization, native overrides, server or disk provider. */
    public static final class AllocationProbe {
        private static final int WARM = 200_000;
        private static final int OPERATIONS = 10_000;
        private static final int ROUNDS = 7;
        private static final double BUDGET = 256;
        private static volatile long sink;

        public static void main(String[] args) throws Throwable {
            int count = Integer.parseInt(args[0]);
            require(count == 0 || count == 1 || count == 16, "Unexpected bucket count");
            Level level = new ObjenesisStd().newInstance(Level.class);
            require(level.getClass() == Level.class, "Exact native Level required");
            Buckets buckets = new Buckets();
            buckets.install(level);
            LongSet[] owners = new LongSet[count];
            for (int i = 0; i < count; i++) owners[i] = pending(level, VERSIONS[i]);
            require(buckets.pending.size() == count, "Protocol buckets must be distinct after normalization");
            MethodHandle process = method("processChunkRequest", void.class);
            java.lang.management.ThreadMXBean platform = ManagementFactory.getThreadMXBean();
            require(platform instanceof ThreadMXBean, "Thread allocation accounting unavailable");
            ThreadMXBean bean = (ThreadMXBean) platform;
            require(bean.isThreadAllocatedMemorySupported(), "Thread allocation accounting unsupported");
            bean.setThreadAllocatedMemoryEnabled(true);
            long thread = Thread.currentThread().getId();
            for (int i = 0; i < 100; i++) sink = bean.getThreadAllocatedBytes(thread);
            for (int pass = 0; pass < 2; pass++) {
                for (int i = 0; i < WARM; i++) process.invokeExact(level);
                verifyBuckets(buckets, owners);
            }
            double[] allocations = new double[ROUNDS];
            double[] controls = new double[ROUNDS];
            for (int round = 0; round < ROUNDS; round++) {
                controls[round] = measure(bean, thread, process, level, false);
                allocations[round] = measure(bean, thread, process, level, true);
                verifyBuckets(buckets, owners);
                System.out.printf(Locale.ROOT, "buckets=%d,round=%d,control=%.6f,B/op=%.6f%n",
                        count, round, controls[round], allocations[round]);
            }
            Arrays.sort(controls);
            Arrays.sort(allocations);
            double controlMedian = controls[ROUNDS / 2];
            double median = allocations[ROUNDS / 2];
            System.out.printf(Locale.ROOT, "buckets=%d,controlMedian=%.6f,median=%.6f,budget=%.0f,calls=%d%n",
                    count, controlMedian, median, BUDGET, 2L * WARM + (long) ROUNDS * OPERATIONS);
            require(controlMedian == 0, "Median allocation counter control must remain zero");
            require(median <= BUDGET, "Empty native chunk request allocation exceeds budget: " + median);
        }

        private static double measure(ThreadMXBean bean, long thread, MethodHandle process, Level level,
                                      boolean invoke) throws Throwable {
            long before = bean.getThreadAllocatedBytes(thread);
            for (int i = 0; i < OPERATIONS; i++) {
                if (invoke) process.invokeExact(level);
            }
            long allocated = bean.getThreadAllocatedBytes(thread) - before;
            sink = allocated;
            return allocated / (double) OPERATIONS;
        }

        private static void verifyBuckets(Buckets buckets, LongSet[] owners) {
            require(buckets.pending.size() == owners.length, "Pending bucket count changed");
            for (int i = 0; i < owners.length; i++) {
                require(buckets.pending.get(VERSIONS[i]) == owners[i] && owners[i].isEmpty(),
                        "Empty pending bucket identity/content changed");
            }
            require(buckets.tasks.isEmpty() && buckets.queues.isEmpty() && buckets.started.isEmpty(),
                    "Empty protocol buckets must not create tasks, recipients or timestamps");
        }

        private static void require(boolean condition, String message) {
            if (!condition) throw new AssertionError(message);
        }
    }
}
