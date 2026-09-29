package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.event.Cancellable;
import cn.nukkit.event.block.BlockFromToEvent;
import cn.nukkit.event.block.LiquidFlowEvent;
import cn.nukkit.item.Item;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.AxisAlignedBB;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.BlockUpdateScheduler;
import cn.nukkit.utils.BlockUpdateEntry;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockLiquidLoadedBoundaryTest {
    @BeforeAll
    static void initializeBlocks() {
        MockServer.init();
        Block.init();
    }

    @Test
    void waterWaitsBeforeAnyReadOrEffectAtAllBordersIncludingLastSearchCell() {
        for (int[] pos : new int[][]{{11, 8}, {4, 8}, {8, 11}, {8, 4}, {-12, -8}, {-5, -8}, {14, 14}}) {
            Fixture f = new Fixture(new BlockWater(2), pos[0], pos[1], Level.DIMENSION_OVERWORLD, true);
            f.liquid.adjacentSources = 17;
            assertEquals(0, f.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
            f.assertOnlyRetry(5);
            assertEquals(2, f.liquid.getDamage());
            assertEquals(17, f.liquid.adjacentSources, "preflight must precede decay bookkeeping too");
        }
    }

    @Test
    void lavaUsesThreeBlockReachInOverworldAndFiveInNether() {
        Fixture overworld = new Fixture(new BlockLava(), 13, 8, Level.DIMENSION_OVERWORLD, true);
        overworld.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        overworld.assertOnlyRetry(30);
        Fixture nether = new Fixture(new BlockLava(), 11, 8, Level.DIMENSION_NETHER, true);
        nether.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        nether.assertOnlyRetry(10);

        Fixture outsideWaterReach = new Fixture(new BlockWater(), 10, 8, Level.DIMENSION_OVERWORLD, true);
        outsideWaterReach.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(outsideWaterReach.reads > 0);
        Fixture outsideLavaReach = new Fixture(new BlockLava(), 12, 8, Level.DIMENSION_OVERWORLD, true);
        outsideLavaReach.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(outsideLavaReach.reads > 0);
    }

    @Test
    void diamondRequiresReachableDiagonalButDoesNotWaitForUnrelatedDiagonal() {
        Fixture reachable = new Fixture(new BlockWater(), 14, 14, Level.DIMENSION_OVERWORLD, true);
        reachable.loaded.add(Level.chunkHash(1, 0));
        reachable.loaded.add(Level.chunkHash(0, 1));
        reachable.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        reachable.assertOnlyRetry(5);

        Fixture unrelated = new Fixture(new BlockWater(), 11, 11, Level.DIMENSION_OVERWORLD, true);
        unrelated.loaded.add(Level.chunkHash(1, 0));
        unrelated.loaded.add(Level.chunkHash(0, 1));
        unrelated.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(unrelated.writes > 0);
        verify(unrelated.level, never()).getChunkIfLoaded(1, 1);
    }

    @Test
    void coldLateralSearchDefersBeforeDownwardFlowBreakingOrLavaMixing() {
        for (Block target : new Block[]{new BlockAir(), new BlockTripWire(), new BlockTripWireHook(),
                new BlockRedstoneWire(), new BlockWater(), new BlockFence()}) {
            for (boolean lava : new boolean[]{false, true}) {
                Fixture f = new Fixture(lava ? new BlockLava() : new BlockWater(),
                        15, 8, Level.DIMENSION_OVERWORLD, true);
                f.put(target, 15, 63, 8, 0);
                // Lava hardening must also wait before inspecting water alongside the source.
                f.put(new BlockWater(), 15, 64, 9, 1);
                f.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
                f.assertOnlyRetry(lava ? 30 : 5);
                assertSame(target, f.blocks.get(key(15, 63, 8, 0)));
            }
        }
    }

    @Test
    void retryHasBackoffIsDeduplicatedAndResumesOnceWithCurrentBlock() {
        Fixture f = new Fixture(new BlockWater(), 11, 8, Level.DIMENSION_OVERWORLD, true);
        f.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        f.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertEquals(1, f.scheduler.getPendingCount());
        f.tick(4);
        assertEquals(0, f.reads);
        f.tick(5);
        assertEquals(0, f.writes);
        assertEquals(1, f.scheduler.getPendingCount(), "cold retry remains pending with backoff");
        int readsAfterDispatch = f.reads;
        f.tick(9);
        assertEquals(readsAfterDispatch, f.reads);
        f.loaded.add(Level.chunkHash(1, 0));
        f.tick(10);
        assertTrue(f.writes > 0);
        int effects = f.writes;
        f.tick(10);
        assertEquals(effects, f.writes, "same scheduled tick must not replay effects");

        Fixture replaced = new Fixture(new BlockWater(), 11, 8, Level.DIMENSION_OVERWORLD, true);
        replaced.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        replaced.put(new BlockStone(), 11, 64, 8, 0);
        replaced.loaded.add(Level.chunkHash(1, 0));
        replaced.tick(5);
        assertEquals(0, replaced.writes, "scheduler must not resurrect replaced liquid");
        assertEquals(0, replaced.scheduler.getPendingCount());
    }

    @Test
    void fullyLoadedReadEventWriteAndScheduleTraceMatchesDisabledMode() {
        for (int scenario = 0; scenario < 7; ++scenario) {
            Fixture enabled = loadedScenario(scenario, true);
            Fixture disabled = loadedScenario(scenario, false);
            enabled.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            disabled.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            assertFalse(enabled.trace.isEmpty());
            assertEquals(disabled.trace, enabled.trace, "scenario " + scenario);
            assertEquals(disabled.state(), enabled.state(), "final blocks for scenario " + scenario);
            if (scenario == 2) assertEquals(0, enabled.writes, "cancelled events cannot write");
            if (scenario == 3) assertEquals(Block.WATER, enabled.blocks.get(key(14, 64, 15, 1)).getId());
            if (scenario == 4) assertEquals(Block.STONE, enabled.blocks.get(key(15, 63, 15, 0)).getId());
            if (scenario == 5) assertEquals(Block.OBSIDIAN, enabled.blocks.get(key(15, 64, 15, 0)).getId());
            if (scenario == 6) assertTrue(enabled.breaks > 0, "loaded redstone still reaches existing break callback");
        }
    }

    @Test
    void disabledModeRetainsOriginalColdReadInsteadOfSilentlyDeferring() {
        Fixture f = new Fixture(new BlockWater(), 11, 8, Level.DIMENSION_OVERWORLD, false);
        AssertionError error = assertThrows(AssertionError.class,
                () -> f.liquid.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        assertTrue(error.getMessage().startsWith("synchronous cold chunk read"));
        verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
    }

    @Test
    void normalWaterloggedNormalizationKeepsOriginalContract() {
        Fixture enabled = new Fixture(new BlockWater(1), 15, 8, Level.DIMENSION_OVERWORLD, true);
        Fixture disabled = new Fixture(new BlockWater(1), 15, 8, Level.DIMENSION_OVERWORLD, false);
        for (Fixture f : new Fixture[]{enabled, disabled}) {
            f.put(new BlockAir(), 15, 64, 8, 0);
            f.put(f.liquid, 15, 64, 8, 1);
            f.liquid.onUpdate(Level.BLOCK_UPDATE_NORMAL);
            assertEquals(2, f.writes);
            verify(f.level, never()).getChunkIfLoaded(anyInt(), anyInt());
        }
        assertEquals(disabled.trace, enabled.trace);
    }

    private static Fixture loadedScenario(int scenario, boolean enabled) {
        BlockLiquid liquid = scenario >= 4 ? new BlockLava()
                : new BlockWater(scenario == 1 ? 3 : 0);
        Fixture f = new Fixture(liquid, 15, 15, Level.DIMENSION_OVERWORLD, enabled);
        f.loadSurroundings();
        if (scenario == 0) f.put(new BlockAir(), 15, 63, 15, 0);
        if (scenario == 1) {
            f.put(new BlockWater(), 14, 64, 15, 0);
            f.put(new BlockWater(), 15, 64, 14, 0);
        }
        if (scenario == 2) f.cancelEvents = true;
        if (scenario == 3) f.put(new BlockTripWireHook(), 14, 64, 15, 0);
        if (scenario == 4) f.put(new BlockWater(), 15, 63, 15, 0);
        if (scenario == 5) f.put(new BlockWater(), 14, 64, 15, 1);
        if (scenario == 6) f.put(new BlockRedstoneWire(), 15, 63, 15, 0);
        return f;
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Server server = mock(Server.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockUpdateScheduler scheduler = new BlockUpdateScheduler(level, 0);
        final BlockLiquid liquid;
        final Map<String, Block> blocks = new HashMap<>();
        final Set<Long> loaded = new HashSet<>();
        final List<String> trace = new ArrayList<>();
        int reads;
        int writes;
        int events;
        int breaks;
        long currentTick;
        boolean cancelEvents;

        Fixture(BlockLiquid liquid, int x, int z, int dimension, boolean enabled) {
            this.liquid = liquid;
            server.liquidLoadedBoundary = enabled;
            loaded.add(Level.chunkHash(x >> 4, z >> 4));
            put(liquid, x, 64, z, 0);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getDimension()).thenReturn(dimension);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getMinBlockY()).thenReturn(-64);
            BaseFullChunk healthyChunk = mock(BaseFullChunk.class);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(i -> {
                long hash = Level.chunkHash(i.getArgument(0), i.getArgument(1));
                return loaded.contains(hash) ? healthyChunk : null;
            });
            when(level.isAreaLoaded(any(AxisAlignedBB.class))).thenAnswer(i -> {
                AxisAlignedBB box = i.getArgument(0);
                return loaded.contains(Level.chunkHash(((int) box.getMinX()) >> 4, ((int) box.getMinZ()) >> 4));
            });
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(i.getArgument(0), i.getArgument(1), i.getArgument(2), 0));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3)));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(i -> {
                Vector3 pos = i.getArgument(0);
                return at(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), i.getArgument(1));
            });
            when(level.getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenAnswer(i -> at(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(4)));
            doAnswer(i -> {
                Object event = i.getArgument(0);
                events++;
                if (event instanceof LiquidFlowEvent flow) {
                    trace.add("flow:" + key(flow.getTo()) + ":" + flow.getNewFlowDecay());
                } else if (event instanceof BlockFromToEvent change) {
                    trace.add("change:" + key(change.getFrom()) + ":" + BlockLiquidLoadedBoundaryTest.state(change.getTo()));
                } else {
                    fail("unexpected event " + event);
                }
                if (cancelEvents && event instanceof Cancellable cancellable) cancellable.setCancelled();
                return null;
            }).when(plugins).callEvent(any());
            when(level.setBlock(any(Vector3.class), anyInt(), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> write(i.getArgument(0), i.getArgument(1), i.getArgument(2),
                            i.getArgument(3), i.getArgument(4)));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> write(i.getArgument(0), 0, i.getArgument(1),
                            i.getArgument(2), i.getArgument(3)));
            when(level.useBreakOn(any(Vector3.class), nullable(Item.class))).thenAnswer(i -> {
                breaks++;
                trace.add("break:" + key((Vector3) i.getArgument(0), 0));
                return null;
            });
            doAnswer(i -> {
                trace.add("sound:" + key((Vector3) i.getArgument(0), 0) + ":" + i.getArgument(1));
                return null;
            }).when(level).addLevelSoundEvent(any(Vector3.class), anyInt());
            doAnswer(i -> {
                Block block = i.getArgument(0);
                int delay = i.getArgument(1);
                trace.add("schedule:" + key(block) + ":" + delay);
                BlockUpdateEntry entry = new BlockUpdateEntry(block.floor(), block, currentTick + delay, 0);
                if (!scheduler.contains(entry)) scheduler.add(entry);
                return null;
            }).when(level).scheduleUpdate(any(Block.class), anyInt());
        }

        void assertOnlyRetry(int delay) {
            assertEquals(0, reads);
            assertEquals(0, events);
            assertEquals(0, writes);
            assertEquals(0, breaks);
            assertEquals(List.of("schedule:" + key(liquid) + ":" + delay), trace);
            assertEquals(1, scheduler.getPendingCount());
        }

        void tick(long tick) {
            currentTick = tick;
            scheduler.tick(tick);
        }

        void loadSurroundings() {
            int cx = liquid.getFloorX() >> 4;
            int cz = liquid.getFloorZ() >> 4;
            for (int x = cx - 1; x <= cx + 1; ++x) {
                for (int z = cz - 1; z <= cz + 1; ++z) loaded.add(Level.chunkHash(x, z));
            }
        }

        Block at(int x, int y, int z, int layer) {
            reads++;
            trace.add("read:" + key(x, y, z, layer));
            if (!loaded.contains(Level.chunkHash(x >> 4, z >> 4))) {
                throw new AssertionError("synchronous cold chunk read at " + x + ", " + z);
            }
            Block block = blocks.get(key(x, y, z, layer));
            if (block == null) block = layer == 0 && y < 64 ? new BlockStone() : new BlockAir();
            return position(block, x, y, z, layer);
        }

        boolean write(Vector3 pos, int layer, Block block, boolean direct, boolean update) {
            writes++;
            trace.add("write:" + key(pos, layer) + ":" + BlockLiquidLoadedBoundaryTest.state(block) + ":" + direct + ":" + update);
            put(block, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer);
            return true;
        }

        void put(Block block, int x, int y, int z, int layer) {
            blocks.put(key(x, y, z, layer), position(block, x, y, z, layer));
        }

        Block position(Block block, int x, int y, int z, int layer) {
            block.x = x; block.y = y; block.z = z; block.layer = layer; block.level = level;
            return block;
        }

        Map<String, String> state() {
            Map<String, String> result = new HashMap<>();
            blocks.forEach((key, block) -> result.put(key, BlockLiquidLoadedBoundaryTest.state(block)));
            return result;
        }
    }

    private static String state(Block block) {
        return block.getId() + ":" + block.getDamage();
    }

    private static String key(Block block) {
        return key(block, block.layer);
    }

    private static String key(Vector3 pos, int layer) {
        return key(pos.getFloorX(), pos.getFloorY(), pos.getFloorZ(), layer);
    }

    private static String key(int x, int y, int z, int layer) {
        return x + ":" + y + ":" + z + ":" + layer;
    }
}
