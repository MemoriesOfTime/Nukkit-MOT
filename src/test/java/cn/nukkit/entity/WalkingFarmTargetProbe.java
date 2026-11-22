package cn.nukkit.entity;

import cn.nukkit.MockServer;
import cn.nukkit.entity.passive.EntityCow;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Flat;
import com.sun.management.ThreadMXBean;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.Collections;
import java.util.Locale;
import java.util.Map;

import static org.mockito.Mockito.when;

/**
 * Measures the real checkTarget path with 150 native cows and no players.
 * Run each classpath (base-313 or candidate) with the Mockito startup javaagent.
 */
public final class WalkingFarmTargetProbe {

    private enum CacheState {
        WARM,
        DIRTY,
        DIRTY_WITH_MEMBERSHIP_WRITE
    }

    private static final class World extends Level {
        BaseFullChunk stored;
        long chunkQueries;

        World(String path) throws IOException {
            super(MockServer.get(), "farm-probe", path, Anvil.class);
            this.isBeingConverted = true;
        }

        @Override
        public Map<Long, Entity> getChunkEntities(int x, int z, boolean load) {
            if (load) throw new AssertionError("Target acquisition loaded chunks");
            chunkQueries++;
            return x == 0 && z == 0 ? stored.getEntities() : Collections.emptyMap();
        }
    }

    public static void main(String[] args) throws IOException {
        MockServer.init();
        when(MockServer.get().getLanguage()).thenReturn(new BaseLang("eng"));
        String path = Path.of(args[0]).toAbsolutePath() + "/";
        int iterations = args.length > 1 ? Integer.parseInt(args[1]) : 100_000;
        Anvil.generate(path, "farm-probe", 0, Flat.class);
        World world = new World(path);
        try {
            run(world, iterations);
        } finally {
            world.getProvider().close();
        }
    }

    private static void run(World world, int iterations) {
        WalkingPlayerTargetSearchTest.Fixture fixture = new WalkingPlayerTargetSearchTest.Fixture();
        EntityCow[] cows = new EntityCow[150];
        world.stored = fixture.chunk(0, 0);
        for (int i = 0; i < cows.length; i++) {
            cows[i] = fixture.cow(5.5, 5.5);
            cows[i].level = world;
        }
        ThreadMXBean allocations = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        allocations.setThreadAllocatedMemoryEnabled(true);
        System.out.printf(Locale.ROOT, "farm-runtime,%s,%s,cows=%d,iterations=%d,classes=%s%n",
                System.getProperty("java.version"), System.getProperty("os.arch"), cows.length, iterations,
                EntityWalking.class.getProtectionDomain().getCodeSource().getLocation());
        for (CacheState state : CacheState.values()) {
            for (int i = 0; i < 20_000; i++) scan(state, cows[i % cows.length], world, cows[149]);
            for (int sample = 0; sample < 5; sample++) {
                world.chunkQueries = 0;
                long beforeBytes = allocations.getCurrentThreadAllocatedBytes();
                long beforeTime = System.nanoTime();
                for (int i = 0; i < iterations; i++) scan(state, cows[i % cows.length], world, cows[149]);
                long elapsed = System.nanoTime() - beforeTime;
                long bytes = allocations.getCurrentThreadAllocatedBytes() - beforeBytes;
                System.out.printf(Locale.ROOT, "farm-search,state=%s,sample=%d,ns/op=%.3f,B/op=%.3f,chunk-queries/op=%.3f%n",
                        state, sample, elapsed / (double) iterations, bytes / (double) iterations,
                        world.chunkQueries / (double) iterations);
            }
        }
    }

    private static void scan(CacheState state, EntityWalking cow, World world, EntityCow changed) {
        if (state == CacheState.DIRTY_WITH_MEMBERSHIP_WRITE) {
            world.stored.getEntities().put(changed.getId(), changed);
        }
        if (state != CacheState.WARM) {
            world.setDirtyNearby(cow);
        }
        cow.followTarget = null;
        cow.target = null;
        cow.stayTime = 999;
        cow.checkTarget();
        if (cow.followTarget != null || cow.target != null) throw new AssertionError("Farm without players acquired a target");
    }
}
