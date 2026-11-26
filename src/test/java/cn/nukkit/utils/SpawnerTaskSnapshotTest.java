package cn.nukkit.utils;

import cn.nukkit.MockServer;
import cn.nukkit.Player;
import cn.nukkit.entity.Entity;
import cn.nukkit.entity.mob.EntityWitch;
import cn.nukkit.lang.BaseLang;
import cn.nukkit.level.DimensionData;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.anvil.Anvil;
import cn.nukkit.level.generator.Flat;
import cn.nukkit.math.Vector3;
import com.sun.management.ThreadMXBean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.objenesis.ObjenesisStd;

import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import static org.mockito.Mockito.when;

class SpawnerTaskSnapshotTest {
    private static final int SPECIES = EntityWitch.NETWORK_ID; // Cap is one in the overworld.

    private static final ObjenesisStd OBJECTS = new ObjenesisStd();
    private World world;

    @BeforeEach
    void openWorld(@TempDir Path directory) throws IOException {
        MockServer.init();
        when(MockServer.get().getLanguage()).thenReturn(new BaseLang("eng"));
        String path = directory + "/";
        Anvil.generate(path, "spawn-cap", 0L, Flat.class);
        Level level = new Level(MockServer.get(), "spawn-cap", path, Anvil.class);
        level.setDimensionData(new DimensionData(0, 0, 255));
        world = new World(level);
    }

    @AfterEach
    void closeWorld() {
        if (world != null) world.level.getProvider().close();
    }

    @Test
    void projectedRadiusMatchesLegacyVectorAtBoundariesAndNonFiniteCoordinates() {
        Mob mob = world.add(1);
        mob.species = SPECIES;
        double[] coordinates = {0.0, -0.0, Math.nextDown(128.0), 128.0, Math.nextUp(128.0),
                -128.0, Double.MAX_VALUE, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY};
        for (double x : coordinates) {
            for (double height : coordinates) {
                mob.x = x;
                mob.y = height;
                world.player.z = 0;
                assertEquals(legacyAllowed(world), world.allowed());
                world.player.z = x;
                mob.x = 0;
                assertEquals(legacyAllowed(world), world.allowed());
            }
        }
    }

    @Test
    void nestedAndParallelScansDoNotReplaceCapturedMembers() {
        Mob first = world.add(0); // The native iterator visits the zero key first.
        world.add(1).species = SPECIES;
        first.onAlive = () -> {
            world.clear();
            assertTrue(world.allowed());
            assertTrue(CompletableFuture.supplyAsync(world::allowed).orTimeout(5, TimeUnit.SECONDS).join());
        };
        assertFalse(world.allowed());
        assertTrue(world.allowed());
    }

    @Test
    void failingPredicateReleasesSnapshotAndLevelOverrideControlsMembership() {
        Mob mob = world.add(0);
        mob.onAlive = () -> { throw new IllegalStateException("probe"); };
        assertThrows(IllegalStateException.class, world::allowed);
        world.clear();
        assertTrue(world.allowed());

        HookLevel level = OBJECTS.newInstance(HookLevel.class);
        level.setDimensionData(new DimensionData(0, 0, 255));
        Mob matching = OBJECTS.newInstance(Mob.class);
        matching.species = SPECIES;
        level.snapshot = new Entity[]{matching};
        assertFalse(SpawnerTask.entitySpawnAllowed(level, SPECIES, world.player));
        assertEquals(1, level.calls);
    }

    @Test
    void repeatedScansAvoidWorldSizedArrayAllocation() {
        for (int i = 0; i < 2048; i++) world.add(i);
        ScanBytes bytes = measure(world);
        assertTrue(bytes.legacy >= 2048 * 4, "baseline must materialize the world array");
        assertTrue(bytes.reused < bytes.legacy / 4, "steady-state allocation should be independent of world size");
    }

    @Test
    void oversizedWorldKeepsSingleSnapshotAllocationAndCanShrinkAgain() {
        int cutoff = 16_384;
        for (int i = 0; i < cutoff; i++) world.add(i);
        assertTrue(world.allowed());
        world.add(cutoff);
        ScanBytes bytes = measure(world);
        assertTrue(bytes.reused <= bytes.legacy + 512, "oversized scans must not repeatedly grow a buffer");
        assertTrue(bytes.reused >= cutoff * 4, "oversized arrays must not remain attached to the thread");

        world.clear();
        assertTrue(world.allowed());
        world.add(0).species = SPECIES;
        assertFalse(world.allowed());
    }

    private record ScanBytes(long legacy, long reused) {}

    private static ScanBytes measure(World world) {
        assumeTrue(ManagementFactory.getThreadMXBean() instanceof ThreadMXBean mx
                && mx.isThreadAllocatedMemorySupported());
        ThreadMXBean mx = (ThreadMXBean) ManagementFactory.getThreadMXBean();
        if (!mx.isThreadAllocatedMemoryEnabled()) mx.setThreadAllocatedMemoryEnabled(true);
        int repeats = 128;
        for (int i = 0; i < repeats; i++) {
            assertTrue(legacyAllowed(world));
            assertTrue(world.allowed());
        }
        long before = mx.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < repeats; i++) assertTrue(legacyAllowed(world));
        long baseline = (mx.getCurrentThreadAllocatedBytes() - before) / repeats;
        before = mx.getCurrentThreadAllocatedBytes();
        for (int i = 0; i < repeats; i++) assertTrue(world.allowed());
        long optimized = (mx.getCurrentThreadAllocatedBytes() - before) / repeats;
        System.out.printf("Spawn snapshot allocation (%d entities): legacy %d B/scan, reused %d B/scan%n",
                world.level.getEntities().length, baseline, optimized);
        return new ScanBytes(baseline, optimized);
    }

    private static boolean legacyAllowed(World world) {
        for (Entity entity : world.level.getEntities()) {
            if (entity.isAlive() && entity.getNetworkId() == SPECIES &&
                    new Vector3(world.player.x, entity.y, world.player.z).distanceSquared(entity) < 16_384) {
                return false;
            }
        }
        return true;
    }

    private static final class World {
        final Level level;
        final Player player = OBJECTS.newInstance(Player.class);

        World(Level level) { this.level = level; }

        Mob add(long id) {
            Mob mob = OBJECTS.newInstance(Mob.class);
            mob.register(level, id);
            return mob;
        }

        void clear() {
            for (Entity entity : level.getEntities()) {
                entity.closed = true;
                level.removeEntity(entity);
            }
        }

        boolean allowed() {
            return SpawnerTask.entitySpawnAllowed(level, SPECIES, player);
        }
    }

    private static class Mob extends Entity {
        int species;
        Runnable onAlive;

        Mob() { super(null, null); }

        void register(Level level, long id) {
            this.id = id;
            setLevel(level);
            level.addEntity(this);
        }

        @Override public int getNetworkId() { return species; }
        @Override public boolean isAlive() {
            if (onAlive != null) onAlive.run();
            return true;
        }
    }

    private static final class HookLevel extends Level {
        int calls;
        Entity[] snapshot;

        HookLevel() { super(null, null, null, null); }
        @Override public Entity[] getEntities() { calls++; return snapshot; }
        @Override public Entity[] getEntities(Entity[] destination) { throw new AssertionError("custom level"); }
    }
}
