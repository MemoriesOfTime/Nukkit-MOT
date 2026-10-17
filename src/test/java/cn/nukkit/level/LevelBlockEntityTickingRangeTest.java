package cn.nukkit.level;

import cn.nukkit.Server;
import cn.nukkit.blockentity.BlockEntity;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.utils.serverconfig.ServerConfig;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LevelBlockEntityTickingRangeTest {
    @TempDir Path directory;

    @Test
    void pausesWithoutDroppingOrDuplicatingQueueEntryAndResumesEveryTick() throws Exception {
        Fixture f = new Fixture(3, 40);
        ChunkLoader loader = f.loader(0, 0);
        CountingBlockEntity be = f.entity(8, 0);
        for (int i = 0; i < 100; i++) f.level.tickBlockEntities();
        assertEquals(0, be.updates);
        assertTrue(be.scheduledForBlockEntityUpdate.get());
        be.scheduleUpdate();
        assertEquals(1, f.queue.size());
        when(loader.getX()).thenReturn(8 * 16.0);
        for (int i = 0; i < 20; i++) f.level.tickBlockEntities();
        assertEquals(20, be.updates);
        assertTrue(be.scheduledForBlockEntityUpdate.get());
        assertEquals(1, f.queue.size());
        when(loader.getX()).thenReturn(0.0);
        f.level.tickBlockEntities();
        assertEquals(20, be.updates);
        verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
    }

    @ParameterizedTest
    @CsvSource({"-3,-3,1", "2,2,1", "3,0,0", "0,3,0", "-4,0,0", "0,-4,0"})
    void usesExactHalfOpenRandomTickBoundary(int x, int z, int expected) throws Exception {
        Fixture f = new Fixture(3, 40);
        f.loader(0, 0);
        CountingBlockEntity be = f.entity(x, z);
        f.level.tickBlockEntities();
        assertEquals(expected, be.updates);
    }

    @Test
    void sharesBudgetDependentRangeAndRadiusCap() throws Exception {
        Fixture f = new Fixture(8, 40); // one loader -> 39 candidates -> range 4
        f.loader(0, 0);
        CountingBlockEntity inside = f.entity(3, 0);
        CountingBlockEntity outside = f.entity(4, 0);
        f.level.tickBlockEntities();
        assertEquals(1, inside.updates);
        assertEquals(0, outside.updates);
        f.loader(100, 100); // two loaders -> 19 candidates -> range 3
        f.level.tickBlockEntities();
        assertEquals(1, inside.updates);
        Fixture capped = new Fixture(1, 400);
        capped.loader(0, 0);
        CountingBlockEntity edge = capped.entity(-1, -1);
        CountingBlockEntity beyond = capped.entity(-2, 0);
        capped.level.tickBlockEntities();
        assertEquals(1, edge.updates);
        assertEquals(0, beyond.updates);
    }

    @Test
    void overlappingLoadersDoNotMultiplyTicksAndRemovalDoesNotLeaveCoverage() throws Exception {
        Fixture f = new Fixture(3, 40);
        f.loader(-10, -10);
        f.loader(-9, -9);
        CountingBlockEntity be = f.entity(-10, -10);
        // Legacy tickChunks can retain positive entries; they must not keep BE active.
        Long2IntOpenHashMap stale = new Long2IntOpenHashMap();
        stale.put(Level.chunkHash(-10, -10), 2);
        set(f.level, "chunkTickList", stale);
        f.level.tickBlockEntities();
        assertEquals(1, be.updates);
        f.loaders.clear();
        f.level.tickBlockEntities();
        assertEquals(1, be.updates);
        assertTrue(be.scheduledForBlockEntityUpdate.get());
    }

    @Test
    void retainsExistingLoaderCoordinateConversionAtNegativeFraction() throws Exception {
        Fixture f = new Fixture(1, 40);
        ChunkLoader loader = f.loader(0, 0);
        when(loader.getX()).thenReturn(-0.5);
        when(loader.getZ()).thenReturn(-16.5);
        CountingBlockEntity inside = f.entity(0, -1);
        CountingBlockEntity outside = f.entity(-2, -1);
        f.level.tickBlockEntities();
        assertEquals(1, inside.updates);
        assertEquals(0, outside.updates);
    }

    @ParameterizedTest
    @CsvSource({"0", "-1"})
    void disabledChunkTickBudgetPausesOrdinaryEntities(int budget) throws Exception {
        Fixture f = new Fixture(3, budget);
        f.loader(0, 0);
        CountingBlockEntity be = f.entity(0, 0);
        f.level.tickBlockEntities();
        assertEquals(0, be.updates);
        assertEquals(1, f.queue.size());
    }

    @Test
    void optOutRunsWithoutLoadersButStillHonorsFalseAndValidity() throws Exception {
        Fixture f = new Fixture(3, 0);
        CountingBlockEntity be = f.entity(100, 100);
        assertFalse(be.alwaysTick());
        be.forceTick = true;
        f.level.tickBlockEntities();
        assertEquals(1, be.updates);
        be.keepUpdating = false;
        f.level.tickBlockEntities();
        assertEquals(2, be.updates);
        assertFalse(be.scheduledForBlockEntityUpdate.get());
        assertTrue(f.queue.isEmpty());
        be.scheduleUpdate();
        be.closed = true;
        f.level.tickBlockEntities();
        assertEquals(2, be.updates);
        assertFalse(be.scheduledForBlockEntityUpdate.get());
        assertTrue(f.queue.isEmpty());
    }

    @Test
    void closedDistantEntityIsRemovedBeforeRadiusCheck() throws Exception {
        Fixture f = new Fixture(3, 40);
        CountingBlockEntity be = f.entity(100, 100);
        be.closed = true;
        f.level.tickBlockEntities();
        assertEquals(0, be.updates);
        assertFalse(be.scheduledForBlockEntityUpdate.get());
        assertTrue(f.queue.isEmpty());
    }

    @Test
    void invalidDistantEntityIsRemovedBeforeRadiusCheck() throws Exception {
        Fixture f = new Fixture(3, 40);
        CountingBlockEntity be = f.entity(100, 100);
        be.setLevel(null);
        f.level.tickBlockEntities();
        assertEquals(0, be.updates);
        assertFalse(be.scheduledForBlockEntityUpdate.get());
        assertTrue(f.queue.isEmpty());
    }

    @Test
    void ordinaryFalseReturnRemovesEntryAndAllowsRescheduling() throws Exception {
        Fixture f = new Fixture(3, 40);
        f.loader(0, 0);
        CountingBlockEntity be = f.entity(0, 0);
        be.keepUpdating = false;
        f.level.tickBlockEntities();
        assertFalse(be.scheduledForBlockEntityUpdate.get());
        assertTrue(f.queue.isEmpty());
        be.keepUpdating = true;
        be.scheduleUpdate();
        f.level.tickBlockEntities();
        assertEquals(2, be.updates);
        assertEquals(1, f.queue.size());
    }

    @Test
    void rollbackRestoresEveryLoadedEntityAndCanBeReenabled() throws Exception {
        Fixture f = new Fixture(3, 0);
        CountingBlockEntity be = f.entity(100, 100);
        f.server.blockEntityTickingRange = false;
        f.level.tickBlockEntities();
        assertEquals(1, be.updates);
        f.server.blockEntityTickingRange = true;
        f.level.tickBlockEntities();
        assertEquals(1, be.updates);
        assertTrue(be.scheduledForBlockEntityUpdate.get());
    }

    @Test
    void defaultOnIsSavedAndExplicitOffSurvivesYamlReload() throws Exception {
        Path file = directory.resolve("nukkit-mot.yml");
        ServerConfig config = ConfigManager.create(ServerConfig.class, it -> it.configure(opt -> {
            opt.configurer(new YamlSnakeYamlConfigurer());
            opt.bindFile(file.toFile());
        }));
        config.save();
        assertTrue(config.chunkSettings().blockEntityTickingRange());
        assertTrue(Files.readString(file).contains("block-entity-ticking-range: true"));
        Files.writeString(file, "chunk-settings:\n  block-entity-ticking-range: false\n");
        config.load();
        assertFalse(config.chunkSettings().blockEntityTickingRange());
        config.save();
        assertTrue(Files.readString(file).contains("block-entity-ticking-range: false"));
    }

    private static void set(Level level, String name, Object value) throws Exception {
        Field field = Level.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(level, value);
    }

    private static final class Fixture {
        final Level level = mock(Level.class, CALLS_REAL_METHODS);
        final Server server = mock(Server.class);
        final Int2ObjectOpenHashMap<ChunkLoader> loaders = new Int2ObjectOpenHashMap<>();
        final ConcurrentLinkedQueue<BlockEntity> queue = new ConcurrentLinkedQueue<>();
        final FullChunk chunk = mock(FullChunk.class);

        Fixture(int radius, int budget) throws Exception {
            server.blockEntityTickingRange = true;
            set(level, "server", server);
            set(level, "chunkTickRadius", radius);
            set(level, "chunksPerTicks", budget);
            set(level, "loaders", loaders);
            set(level, "updateBlockEntities", queue);
            set(level, "blockEntityTickingChunks", new LongOpenHashSet());
            LevelProvider provider = mock(LevelProvider.class);
            when(chunk.getProvider()).thenReturn(provider);
            when(provider.getLevel()).thenReturn(level);
            doNothing().when(level).addBlockEntity(any(BlockEntity.class));
        }

        ChunkLoader loader(int x, int z) {
            ChunkLoader loader = mock(ChunkLoader.class);
            when(loader.getX()).thenReturn(x * 16.0);
            when(loader.getZ()).thenReturn(z * 16.0);
            loaders.put(loaders.size() + 1, loader);
            return loader;
        }

        CountingBlockEntity entity(int x, int z) {
            CountingBlockEntity be = new CountingBlockEntity(chunk, new CompoundTag()
                    .putInt("x", x * 16).putInt("y", 64).putInt("z", z * 16));
            be.scheduleUpdate();
            return be;
        }
    }

    private static final class CountingBlockEntity extends BlockEntity {
        int updates;
        boolean keepUpdating = true;
        boolean forceTick;

        CountingBlockEntity(FullChunk chunk, CompoundTag nbt) { super(chunk, nbt); }

        @Override public boolean isBlockEntityValid() { return true; }
        @Override public boolean alwaysTick() { return forceTick || super.alwaysTick(); }
        @Override public boolean onUpdate() { updates++; return keepUpdating; }
    }
}
