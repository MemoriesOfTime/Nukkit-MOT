package cn.nukkit.scheduler;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.block.Block;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.SimpleAxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.utils.BlockUpdateEntry;
import cn.nukkit.utils.serverconfig.ServerConfig;
import cn.nukkit.utils.serverconfig.category.PerformanceSettings;
import eu.okaeri.configs.ConfigManager;
import eu.okaeri.configs.yaml.snakeyaml.YamlSnakeYamlConfigurer;
import org.cloudburstmc.nbt.NBTOutputStream;
import org.cloudburstmc.nbt.NbtMap;
import org.cloudburstmc.nbt.NbtUtils;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.IntConsumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockUpdateSchedulerBudgetTest {
    @TempDir Path directory;
    private static final AxisAlignedBB SAVE_AREA = new SimpleAxisAlignedBB(-2, 0, -2, 16, 319, 16);

    @Test
    void continuationKeepsMotsBucketOrderBeforeLaterDueBucket() {
        Fixture unlimited = new Fixture(false, 2);
        Fixture limited = new Fixture(true, 2);
        for (int x = 0; x < 7; x++) {
            unlimited.add(x, 1);
            limited.add(x, 1);
        }
        unlimited.add(100, 2);
        limited.add(100, 2);
        unlimited.scheduler.tick(1);
        unlimited.scheduler.tick(2);
        assertEquals(2, limited.scheduler.tick(1));
        assertEquals(5, limited.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        for (BlockUpdateEntry entry : limited.scheduler.getPendingBlockUpdates(SAVE_AREA)) {
            assertTrue(limited.scheduler.contains(entry));
            assertFalse(limited.scheduler.isBlockTickPending(entry.pos, entry.block));
        }
        assertEquals(2, limited.scheduler.tick(2));
        assertEquals(2, limited.scheduler.tick(3));
        assertEquals(2, limited.scheduler.tick(4));
        assertEquals(unlimited.executed, limited.executed);
        assertEquals(0, limited.scheduler.getPendingCount());
        assertTrue(limited.scheduler.getPendingBlockUpdates(SAVE_AREA).isEmpty());
    }

    @Test
    void capCoversAllDueBucketsAndSkippedTicksDoNotStrandOverdueWork() {
        Fixture f = new Fixture(true, 2);
        for (int tick = 1; tick <= 5; tick++) f.add(tick, tick);
        assertEquals(2, f.scheduler.tick(100000));
        assertEquals(2, f.scheduler.tick(100001));
        assertEquals(1, f.scheduler.tick(100002));
        assertEquals(List.of(1, 2, 3, 4, 5), f.executed);
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void disabledSwitchDrainsExistingContinuationAndRestoresUnlimitedExecution() {
        Fixture f = new Fixture(true, 2);
        for (int x = 0; x < 7; x++) f.add(x, 1);
        assertEquals(2, f.scheduler.tick(1));
        f.server.scheduledBlockUpdateBudget = false;
        assertEquals(5, f.scheduler.tick(2));
        assertEquals(7, new HashSet<>(f.executed).size());
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void cancellationOfDeferredEntryDoesNotReappearOrConsumeExecutionBudget() {
        Fixture f = new Fixture(true, 2);
        for (int x = 0; x < 7; x++) f.add(x, 1);
        f.scheduler.tick(1);
        BlockUpdateEntry cancelled = f.scheduler.getPendingBlockUpdates(SAVE_AREA).iterator().next();
        int cancelledX = cancelled.pos.getFloorX();
        assertTrue(f.scheduler.remove(cancelled));
        assertFalse(f.scheduler.contains(cancelled));
        assertFalse(f.scheduler.getPendingBlockUpdates(SAVE_AREA).contains(cancelled));
        assertEquals(2, f.scheduler.tick(2));
        assertEquals(2, f.scheduler.tick(3));
        assertFalse(f.executed.contains(cancelledX));
        assertEquals(6, new HashSet<>(f.executed).size());
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void activeGuardAndReentrantFutureEntryStaySeparateFromContinuation() {
        Fixture f = new Fixture(true, 2);
        for (int x = 0; x < 5; x++) f.add(x, 1);
        Set<Integer> requeued = new HashSet<>();
        f.callback = x -> {
            BlockUpdateEntry entry = f.entries.get(x);
            assertTrue(f.scheduler.isBlockTickPending(entry.pos, entry.block));
            assertFalse(f.scheduler.contains(entry), "current active entry is not a future deadline");
            assertFalse(f.scheduler.getPendingBlockUpdates(SAVE_AREA).contains(entry), "executing entry is not saved twice");
            if (requeued.add(x)) f.scheduler.add(new BlockUpdateEntry(entry.pos, entry.block, 1, 0));
        };
        for (int tick = 1; tick <= 5; tick++) assertEquals(2, f.scheduler.tick(tick));
        assertEquals(10, f.executed.size());
        for (int x = 0; x < 5; x++) {
            int coordinate = x;
            assertEquals(2, f.executed.stream().filter(n -> n == coordinate).count());
        }
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void pendingSnapshotContainsRemainderAndRealLevelDbNbtReloadKeepsEveryEntry() throws Exception {
        MockServer.init();
        Fixture f = new Fixture(true, 2);
        for (int x = 0; x < 7; x++) f.add(x, 1);
        assertEquals(2, f.scheduler.tick(1));
        Collection<BlockUpdateEntry> snapshot = f.scheduler.getPendingBlockUpdates(SAVE_AREA);
        Set<Integer> expected = new HashSet<>();
        for (BlockUpdateEntry entry : snapshot) expected.add(entry.pos.getFloorX());
        assertEquals(5, expected.size());

        LevelDBProvider provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
        Method save = LevelDBProvider.class.getDeclaredMethod("saveBlockTickingQueue", Collection.class, long.class);
        save.setAccessible(true);
        NbtMap ticks = (NbtMap) save.invoke(provider, snapshot, 1L);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (NBTOutputStream writer = NbtUtils.createWriterLE(bytes)) {
            writer.writeTag(ticks);
        }
        Method load = LevelDBProvider.class.getDeclaredMethod("loadBlockTickingQueueDeferred",
                byte[].class, boolean.class, List.class);
        load.setAccessible(true);
        List<BaseFullChunk.PendingBlockUpdate> restored = new ArrayList<>();
        load.invoke(provider, bytes.toByteArray(), false, restored);
        assertEquals(5, restored.size());
        Fixture restarted = new Fixture(false, 2);
        for (BaseFullChunk.PendingBlockUpdate entry : restored) {
            restarted.add(entry.getX(), entry.getDelay());
            assertEquals(1, entry.getBlock().getId());
        }
        assertEquals(5, restarted.scheduler.tick(1));
        assertEquals(expected, new HashSet<>(restarted.executed));
        assertEquals(5, restarted.executed.size());
    }

    @Test
    void unloadRequeueLayerAndReplacedBlockChecksKeepExistingContracts() {
        Fixture f = new Fixture(true, 1);
        f.add(0, 1);
        f.add(1, 1);
        f.add(2, 1);
        f.scheduler.tick(1);
        Set<BlockUpdateEntry> remaining = f.scheduler.getPendingBlockUpdates(SAVE_AREA);
        assertEquals(2, remaining.size(), "unload snapshot includes every unexecuted entry");
        when(f.level.isAreaLoaded(any())).thenReturn(false);
        doAnswer(invocation -> {
            Block block = invocation.getArgument(0);
            Vector3 pos = invocation.getArgument(1);
            f.scheduler.add(new BlockUpdateEntry(pos, block, 100, 0));
            return null;
        }).when(f.level).scheduleUpdate(any(Block.class), any(Vector3.class), eq(0));
        assertEquals(1, f.scheduler.tick(2));
        assertEquals(2, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        verify(f.level).scheduleUpdate(any(Block.class), any(Vector3.class), eq(0));
        assertEquals(1, f.scheduler.tick(3));
        assertEquals(2, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        verify(f.level, times(2)).scheduleUpdate(any(Block.class), any(Vector3.class), eq(0));
        when(f.level.isAreaLoaded(any())).thenReturn(true);
        Block replacement = mock(Block.class);
        when(replacement.getId()).thenReturn(2);
        when(f.level.getBlock(any(Vector3.class), anyInt())).thenReturn(replacement);
        assertEquals(1, f.scheduler.tick(100));
        assertEquals(1, f.scheduler.tick(101));
        verify(replacement, never()).onUpdate(anyInt());
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void otherwiseCleanLoadedChunkIsDirtiedForUnloadAndTickListRemoval() {
        Fixture f = new Fixture(true, 1);
        BaseFullChunk owner = mock(BaseFullChunk.class);
        when(f.level.getChunkIfLoaded(0, 0)).thenReturn(owner);
        for (int x = 0; x < 4; x++) f.add(x, 1);
        assertEquals(1, f.scheduler.tick(1));
        assertEquals(3, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        verify(owner, atLeastOnce()).setChanged();
        // Simulate an acknowledged save that made the chunk clean again. A later
        // cancellation/execution must dirty it, otherwise disk replays old ticks.
        clearInvocations(owner);
        BlockUpdateEntry cancelled = f.scheduler.getPendingBlockUpdates(SAVE_AREA).iterator().next();
        assertTrue(f.scheduler.remove(cancelled));
        verify(owner).setChanged();
        clearInvocations(owner);
        assertEquals(1, f.scheduler.tick(2));
        verify(owner).setChanged();
        assertEquals(1, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
    }

    @Test
    void borderTickCancellationAndConsumptionDirtyEveryOverlappingSnapshotOwner() {
        for (int origin : new int[]{0, -16}) {
            Fixture f = new Fixture(true, 1);
            int chunk = origin >> 4;
            List<BaseFullChunk> owners = new ArrayList<>();
            for (int x = chunk; x <= chunk + 1; x++) {
                for (int z = chunk; z <= chunk + 1; z++) {
                    BaseFullChunk owner = mock(BaseFullChunk.class);
                    when(f.level.getChunkIfLoaded(x, z)).thenReturn(owner);
                    owners.add(owner);
                }
            }
            for (int x = origin + 14; x <= origin + 15; x++) {
                for (int z = origin + 14; z <= origin + 15; z++) f.add(x, z, 1);
            }
            assertEquals(1, f.scheduler.tick(1));
            for (BaseFullChunk owner : owners) {
                verify(owner, atLeastOnce()).setChanged();
                clearInvocations(owner);
            }
            AxisAlignedBB area = new SimpleAxisAlignedBB(origin, 0, origin, origin + 16, 319, origin + 16);
            BlockUpdateEntry cancelled = f.scheduler.getPendingBlockUpdates(area).iterator().next();
            assertTrue(f.scheduler.remove(cancelled));
            for (BaseFullChunk owner : owners) verify(owner).setChanged();
            for (BaseFullChunk owner : owners) clearInvocations(owner);
            assertEquals(1, f.scheduler.tick(2));
            for (BaseFullChunk owner : owners) verify(owner).setChanged();
            verify(f.level, never()).getChunk(anyInt(), anyInt(), anyBoolean());
        }
    }

    @Test
    void nonpositiveLimitStillMakesProgressAndHandlerFailureKeepsRemainder() {
        Fixture f = new Fixture(true, 0);
        for (int x = 0; x < 5; x++) f.add(x, 1);
        assertEquals(1, f.scheduler.tick(1));
        assertEquals(4, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        f.callback = x -> { throw new IllegalStateException("handler failure"); };
        assertThrows(IllegalStateException.class, () -> f.scheduler.tick(2));
        assertEquals(3, f.scheduler.getPendingBlockUpdates(SAVE_AREA).size());
        f.callback = x -> {};
        for (int tick = 3; tick <= 5; tick++) assertEquals(1, f.scheduler.tick(tick));
        assertEquals(5, new HashSet<>(f.executed).size());
        assertEquals(0, f.scheduler.getPendingCount());
    }

    @Test
    void highDefaultAndExplicitSwitchSurviveNukkitMotYamlReload() throws Exception {
        PerformanceSettings defaults = new PerformanceSettings();
        assertTrue(defaults.scheduledBlockUpdateBudget());
        assertEquals(10000, defaults.scheduledBlockUpdatesPerTick());
        Path file = directory.resolve("nukkit-mot.yml");
        ServerConfig config = ConfigManager.create(ServerConfig.class, it -> it.configure(opt -> {
            opt.configurer(new YamlSnakeYamlConfigurer());
            opt.bindFile(file.toFile());
        }));
        config.save();
        assertTrue(Files.readString(file).contains("scheduled-block-update-budget: true"));
        assertTrue(Files.readString(file).contains("scheduled-block-updates-per-tick: 10000"));
        Files.writeString(file, "performance-settings:\n  scheduled-block-update-budget: false\n  scheduled-block-updates-per-tick: 12345\n");
        config.load();
        assertFalse(config.performanceSettings().scheduledBlockUpdateBudget());
        assertEquals(12345, config.performanceSettings().scheduledBlockUpdatesPerTick());
        config.save();
        assertTrue(Files.readString(file).contains("scheduled-block-update-budget: false"));
    }

    private static final class Fixture {
        final Server server = mock(Server.class);
        final Level level = mock(Level.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        final List<Integer> executed = new ArrayList<>();
        final Map<Vector3, Block> live = new HashMap<>();
        final Map<Integer, BlockUpdateEntry> entries = new HashMap<>();
        IntConsumer callback = x -> {};
        Fixture(boolean enabled, int limit) {
            server.scheduledBlockUpdateBudget = enabled;
            server.scheduledBlockUpdatesPerTick = limit;
            when(level.getServer()).thenReturn(server);
            when(level.getName()).thenReturn("budget-test");
            when(level.isAreaLoaded(any())).thenReturn(true);
            doAnswer(invocation -> live.get(invocation.getArgument(0)))
                    .when(level).getBlock(any(Vector3.class), anyInt());
        }
        BlockUpdateEntry add(int x, long tick) {
            return add(x, 0, tick);
        }
        BlockUpdateEntry add(int x, int z, long tick) {
            Block block = mock(Block.class);
            when(block.getId()).thenReturn(1);
            when(block.getFullId()).thenReturn(1 << Block.DATA_BITS);
            Vector3 pos = new Vector3(x, 64, z);
            live.put(pos, block);
            doAnswer(invocation -> {
                executed.add(x);
                callback.accept(x);
                return 0;
            }).when(block).onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            BlockUpdateEntry entry = new BlockUpdateEntry(pos, block, tick, 0);
            entries.put(x, entry);
            scheduler.add(entry);
            return entry;
        }
    }
}
