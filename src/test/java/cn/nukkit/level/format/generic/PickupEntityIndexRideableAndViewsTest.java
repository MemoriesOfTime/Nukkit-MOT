package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.EntityRideable;
import cn.nukkit.entity.item.EntityItem;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.utils.collection.nb.Long2ObjectNonBlockingMap;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Comparator;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PickupEntityIndexRideableAndViewsTest {

    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @AfterAll
    static void cleanup() {
        MockServer.reset();
    }

    @Test
    void rideableAndGeneralPresenceShareSingleScanAndTrackMutations() {
        Long2ObjectNonBlockingMap<Entity> backing = spy(new Long2ObjectNonBlockingMap<>());
        PickupEntityIndex index = new PickupEntityIndex(backing);
        Entity mob = mock(Entity.class);
        Entity rideable = mock(Entity.class, withSettings().extraInterfaces(EntityRideable.class));
        EntityItem item = mock(EntityItem.class);

        assertFalse(index.hasAnyEntities());
        assertFalse(index.hasRideableEntities());
        assertFalse(index.hasPickupEntities(false));

        index.put(1L, mob);
        clearInvocations(backing);
        assertTrue(index.hasAnyEntities());
        assertFalse(index.hasRideableEntities());
        assertFalse(index.hasPickupEntities(false));
        verify(backing, times(1)).values();

        index.put(2L, rideable);
        assertTrue(index.hasAnyEntities());
        assertTrue(index.hasRideableEntities());
        assertFalse(index.hasPickupEntities(false));

        index.replace(2L, item);
        assertFalse(index.hasRideableEntities());
        assertTrue(index.hasPickupEntities(true));

        index.clear();
        assertFalse(index.hasAnyEntities());
        assertFalse(index.hasRideableEntities());
        assertFalse(index.hasPickupEntities(false));
    }

    @Test
    void collectionViewsAreReusedAndCloneGetsIndependentViews() {
        Long2ObjectNonBlockingMap<String> map = new Long2ObjectNonBlockingMap<>();
        map.put(10L, "alpha");
        assertSame(map.values(), map.values());
        assertSame(map.keySet(), map.keySet());
        assertSame(map.entrySet(), map.entrySet());
        assertSame(map.fastEntrySet(), map.fastEntrySet());

        Long2ObjectNonBlockingMap<String> cloned = map.clone();
        assertNotSame(map.values(), cloned.values());
        assertNotSame(map.keySet(), cloned.keySet());
        assertNotSame(map.entrySet(), cloned.entrySet());
        cloned.values().clear();
        assertEquals(1, map.size(), "Clearing cloned values view must not mutate original map");
        assertEquals(0, cloned.size());

        PickupEntityIndex index = new PickupEntityIndex(new Long2ObjectNonBlockingMap<>());
        assertSame(index.values(), index.values());
        assertSame(index.keySet(), index.keySet());
        assertSame(index.entrySet(), index.entrySet());
    }

    @Test
    void emptyChunkIterationAndNonRideableChunkScanAllocateZeroBytes() throws Exception {
        if (isChunkInstrumentedByAgent()) {
            runCleanChildProbe();
            return;
        }
        verifyZeroAllocationScan();
    }

    public static void main(String[] args) throws Exception {
        MockServer.init();
        try {
            verifyZeroAllocationScan();
        } finally {
            MockServer.reset();
        }
    }

    private static boolean isChunkInstrumentedByAgent() {
        LevelDBChunk probe = createInitializedChunk(0, 0);
        for (int i = 0; i < 64; i++) {
            probe.hasEntities();
        }
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        long tid = Thread.currentThread().getId();
        long before = mx.getThreadAllocatedBytes(tid);
        for (int i = 0; i < 64; i++) {
            probe.hasEntities();
        }
        return mx.getThreadAllocatedBytes(tid) - before > 256L;
    }

    private static void runCleanChildProbe() throws Exception {
        String javaBin = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        Process proc = new ProcessBuilder(javaBin, "-cp", System.getProperty("java.class.path"),
                PickupEntityIndexRideableAndViewsTest.class.getName())
                .redirectErrorStream(true)
                .start();
        String output;
        try (var in = proc.getInputStream()) {
            output = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
        assertTrue(proc.waitFor(30, TimeUnit.SECONDS), "Child allocation probe timed out");
        assertEquals(0, proc.exitValue(), () -> "Child allocation probe failed:\n" + output);
    }

    private static LevelDBChunk createInitializedChunk(int x, int z) {
        return new LevelDBChunk(null, x, z) {
            {
                for (int i = 0; i < this.sections.length; i++) {
                    if (this.sections[i] == null) {
                        this.sections[i] = EmptyChunkSection.bySectionY(i);
                    }
                }
            }
        };
    }

    private static void verifyZeroAllocationScan() throws Exception {
        Path tempWorld = Files.createTempDirectory("rideable-index-probe");
        try {
            LevelDBChunk emptyChunk = createInitializedChunk(0, 0);
            Entity dummy = mock(Entity.class);
            when(dummy.getId()).thenReturn(99L);
            emptyChunk.addEntity(dummy);
            emptyChunk.removeEntity(dummy);
            assertFalse(emptyChunk.hasEntities());

            LevelDBChunk mobChunk = createInitializedChunk(1, 0);
            Entity mob = mock(Entity.class);
            when(mob.getId()).thenReturn(101L);
            mobChunk.addEntity(mob);
            assertTrue(mobChunk.hasEntities());
            assertFalse(mobChunk.hasRideableEntities());

            when(Server.getInstance().getLanguage()).thenReturn(new cn.nukkit.lang.BaseLang("eng"));
            Anvil.generate(tempWorld.toString() + "/", "stub", 0L, Flat.class);
            StubLevel level = new StubLevel(Server.getInstance(), "stub", tempWorld.toString() + "/", emptyChunk, mobChunk);
            Entity distantBoat = mock(Entity.class, withSettings().extraInterfaces(EntityRideable.class));
            when(distantBoat.getId()).thenReturn(500L);
            when(distantBoat.getLevel()).thenReturn(level);
            level.addEntity(distantBoat);
            assertTrue(level.hasRideableEntities());

            SimpleAxisAlignedBB box = new SimpleAxisAlignedBB(14.0, 64.0, 14.0, 18.0, 66.0, 18.0);
            for (int i = 0; i < 10_000; i++) {
                assertEquals(0, runDirectChunkPresencePass(emptyChunk, mobChunk));
                assertEquals(0, level.getNearbyRideables(box, mob).length);
            }

            ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
            long tid = Thread.currentThread().getId();
            long beforeDirect = mx.getThreadAllocatedBytes(tid);
            int sink = 0;
            for (int i = 0; i < 5_000; i++) {
                sink += runDirectChunkPresencePass(emptyChunk, mobChunk);
            }
            long directAllocated = mx.getThreadAllocatedBytes(tid) - beforeDirect;
            assertEquals(0, sink);
            // ThreadMXBean.getThreadAllocatedBytes(long) allocates 96 B internally for its 1-element id/result arrays;
            // without empty-chunk & view reuse, 5,000 probes allocated >440,000 B.
            assertTrue(directAllocated <= 256L,
                    "Expected zero-allocation chunk entity/rideable probes, got " + directAllocated + " B across 5,000 ticks");

            long beforeLevel = mx.getThreadAllocatedBytes(tid);
            for (int i = 0; i < 5_000; i++) {
                sink += level.getNearbyRideables(box, mob).length;
            }
            long perLevelCall = (mx.getThreadAllocatedBytes(tid) - beforeLevel) / 5_000;
            assertEquals(0, sink);
            assertTrue(perLevelCall <= 64L,
                    "Expected <=64 B/call, got " + perLevelCall + " B/call");
        } finally {
            try (var walk = Files.walk(tempWorld)) {
                walk.sorted(Comparator.reverseOrder()).map(Path::toFile).forEach(File::delete);
            }
        }
    }

    private static int runDirectChunkPresencePass(LevelDBChunk emptyChunk, LevelDBChunk mobChunk) {
        int sink = 0;
        for (Entity e : emptyChunk.getEntities().values()) {
            sink += e.hashCode();
        }
        if (mobChunk.hasRideableEntities()) {
            sink++;
        }
        return sink;
    }

    static class StubLevel extends Level {
        final LevelDBChunk emptyChunk;
        final LevelDBChunk mobChunk;

        StubLevel(Server server, String name, String path, LevelDBChunk emptyChunk, LevelDBChunk mobChunk) {
            super(server, name, path, Anvil.class);
            this.emptyChunk = emptyChunk;
            this.mobChunk = mobChunk;
        }

        @Override
        public Map<Long, Entity> getChunkEntities(int chunkX, int chunkZ, boolean loadChunks) {
            if ((chunkX == 0 || chunkX == 1) && (chunkZ == 0 || chunkZ == 1)) {
                return (chunkX == 0 ? emptyChunk : mobChunk).getEntities();
            }
            return Collections.emptyMap();
        }
    }
}
