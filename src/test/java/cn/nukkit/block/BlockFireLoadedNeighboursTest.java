package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.event.block.BlockBurnEvent;
import cn.nukkit.event.block.BlockIgniteEvent;
import cn.nukkit.level.GameRule;
import cn.nukkit.level.GameRules;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockFireLoadedNeighboursTest {
    @BeforeAll
    static void initializeBlocks() {
        MockServer.init();
        Block.init();
    }

    @Test
    void immortalFireDoesNotReadSkyOrLoadNeighboursEvenInRain() {
        Fixture f = new Fixture(15, 8, new BlockNetherrack());
        when(f.level.isRaining()).thenReturn(true);
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        verify(f.level, never()).canBlockSeeSky(any(Block.class));
        assertFalse(f.removed);
        verify(f.level).scheduleUpdate(eq(f.fire), intThat(delay -> delay >= 30 && delay <= 39));
    }

    @Test
    void dryLoadedFireNeverReadsSky() {
        Fixture f = new Fixture(8, 8, new BlockPlanks());
        f.put(new Fuel(), 9, 64, 8);
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        verify(f.level, never()).canBlockSeeSky(any(Block.class));
        assertFalse(f.removed);
    }

    @Test
    void finiteFireAtEachBorderWaitsWithoutReadingOrChangingItsAge() {
        for (int[] pos : new int[][]{{15, 8}, {0, 8}, {8, 15}, {8, 0}, {-1, -8}, {-16, -8}}) {
            for (boolean rain : new boolean[]{false, true}) {
                Fixture f = new Fixture(pos[0], pos[1], new BlockStone());
                when(f.level.isRaining()).thenReturn(rain);
                f.fire.setDamage(7);
                f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
                assertEquals(7, f.fire.getDamage());
                assertFalse(f.removed);
                verify(f.level).scheduleUpdate(f.fire, f.fire.tickRate());
                verify(f.level, never()).canBlockSeeSky(any(Block.class));
                verify(f.level, never()).setBlock(any(Vector3.class), any(Block.class), anyBoolean());
            }
        }
    }

    @Test
    void deferredFireResumesAndRainExtinguishesAfterNeighbourLoads() {
        Fixture f = new Fixture(15, 8, new BlockStone());
        when(f.level.isRaining()).thenReturn(true);
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertFalse(f.removed);
        f.loaded.add(Level.chunkHash(1, 0));
        f.sky.add("16:64:8");
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        assertTrue(f.removed);
        verify(f.level).canBlockSeeSky(argThat((Block block) -> block.x == 16 && block.z == 8));
    }

    @Test
    void unsupportedNormalAndRandomUpdatesDeferAndRetainOneRetry() {
        for (int type : new int[]{Level.BLOCK_UPDATE_NORMAL, Level.BLOCK_UPDATE_RANDOM}) {
            Fixture f = new Fixture(15, 8, new BlockAir());
            assertEquals(Level.BLOCK_UPDATE_NORMAL, f.fire.onUpdate(type));
            assertFalse(f.removed);
            verify(f.level).scheduleUpdate(f.fire, f.fire.tickRate());
            when(f.level.isUpdateScheduled(f.fire, f.fire)).thenReturn(true);
            f.fire.onUpdate(type);
            verify(f.level, times(1)).scheduleUpdate(f.fire, f.fire.tickRate());
            f.loaded.add(Level.chunkHash(1, 0));
            f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
            assertTrue(f.removed, "without a flammable neighbour unsupported fire still goes out");
        }
    }

    @Test
    void solidSupportNeedsNoNeighbourReadDuringNormalUpdate() {
        Fixture f = new Fixture(15, 8, new BlockStone());
        f.fire.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        verify(f.level, never()).isChunkLoaded(anyInt(), anyInt());
        verify(f.level).scheduleUpdate(f.fire, f.fire.tickRate());
        assertFalse(f.removed);
    }

    @Test
    void disabledFireTickDoesNotCreateDeferredTicks() {
        Fixture f = new Fixture(15, 8, new BlockAir());
        f.level.gameRules.setGameRule(GameRule.DO_FIRE_TICK, false);
        f.fire.onUpdate(Level.BLOCK_UPDATE_NORMAL);
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        verify(f.level, never()).scheduleUpdate(any(Block.class), anyInt());
        assertFalse(f.removed);
    }

    @Test
    void loadedFuelStillReceivesCancellableSpreadOrBurnEvent() {
        Fixture f = new Fixture(8, 8, new BlockPlanks());
        f.put(new Fuel(), 9, 64, 8);
        f.fire.onUpdate(Level.BLOCK_UPDATE_SCHEDULED);
        verify(f.plugins).callEvent(argThat(event -> event instanceof BlockIgniteEvent || event instanceof BlockBurnEvent));
        assertFalse(f.removed);
        assertInstanceOf(Fuel.class, f.at(9, 64, 8, true));
    }

    private static final class Fuel extends BlockPlanks {
        @Override public int getBurnAbility() { return Integer.MAX_VALUE; }
        @Override public int getBurnChance() { return 100; }
    }

    private static final class Fixture {
        final Level level = mock(Level.class);
        final PluginManager plugins = mock(PluginManager.class);
        final BlockFire fire = new BlockFire();
        final Map<String, Block> blocks = new HashMap<>();
        final Set<Long> loaded = new HashSet<>();
        final Set<String> sky = new HashSet<>();
        boolean removed;

        Fixture(int x, int z, Block below) {
            loaded.add(Level.chunkHash(x >> 4, z >> 4));
            put(fire, x, 64, z);
            put(below, x, 63, z);
            level.gameRules = GameRules.getDefault();
            Server server = mock(Server.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            doAnswer(i -> {
                if (i.getArgument(0) instanceof BlockIgniteEvent ignite) ignite.setCancelled();
                if (i.getArgument(0) instanceof BlockBurnEvent burn) burn.setCancelled();
                return null;
            }).when(plugins).callEvent(any());
            when(level.isChunkLoaded(anyInt(), anyInt())).thenAnswer(i ->
                    loaded.contains(Level.chunkHash(i.getArgument(0), i.getArgument(1))));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    at(i.getArgument(0), i.getArgument(1), i.getArgument(2), true));
            when(level.getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenAnswer(i -> at(i.getArgument(1), i.getArgument(2), i.getArgument(3), i.getArgument(5)));
            when(level.canBlockSeeSky(any(Block.class))).thenAnswer(i -> {
                Block block = i.getArgument(0);
                at(block.getFloorX(), block.getFloorY(), block.getFloorZ(), true);
                return sky.contains(key(block));
            });
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean())).thenAnswer(i -> {
                Vector3 pos = i.getArgument(0);
                Block block = i.getArgument(1);
                if (key(pos).equals(key(fire)) && block.getId() == Block.AIR) removed = true;
                put(block, pos.getFloorX(), pos.getFloorY(), pos.getFloorZ());
                return true;
            });
        }

        Block at(int x, int y, int z, boolean load) {
            if (!loaded.contains(Level.chunkHash(x >> 4, z >> 4))) {
                if (load) throw new AssertionError("synchronous cold chunk load at " + x + ", " + z);
                return positioned(new BlockAir(), x, y, z);
            }
            return blocks.getOrDefault(x + ":" + y + ":" + z, positioned(new BlockAir(), x, y, z));
        }

        void put(Block block, int x, int y, int z) {
            blocks.put(x + ":" + y + ":" + z, positioned(block, x, y, z));
        }

        Block positioned(Block block, int x, int y, int z) {
            block.x = x; block.y = y; block.z = z; block.level = level;
            return block;
        }
    }

    private static String key(Vector3 p) {
        return p.getFloorX() + ":" + p.getFloorY() + ":" + p.getFloorZ();
    }
}
