package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockCactusObstructionTest {
    @BeforeAll
    static void initialize() {
        Block.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {Block.STONE, Block.GLASS, Block.SAND, Block.WATER})
    void growthDoesNotJumpAcrossAnotherBlock(int obstacle) {
        try (Fixture f = new Fixture(1, false)) {
            f.put(65, obstacle);
            f.tick();
            assertTrue(f.events.isEmpty(), "an obstacle must not create a growth event above itself");
            assertTrue(f.children.isEmpty(), "no disconnected cactus may be written above the obstacle");
            assertEquals(obstacle, f.at(65).getId());
            assertEquals(Block.AIR, f.at(66).getId());
            assertEquals(0, f.source.getDamage(), "blocked mature growth still resets the original age");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void contiguousColumnStillGrowsExactlyOneBlock(int height) {
        try (Fixture f = new Fixture(height, false)) {
            f.tick();
            assertEquals(List.of(64 + height), f.children);
            assertEquals(1, f.events.size());
            assertEquals(64 + height, f.events.get(0).getBlock().getFloorY());
            assertEquals(Block.CACTUS, f.at(64 + height).getId());
            assertEquals(Block.AIR, f.at(65 + height).getId());
            assertEquals(0, f.source.getDamage());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void cancelledGrowthKeepsTheColumnAndTarget(int height) {
        try (Fixture f = new Fixture(height, true)) {
            f.tick();
            assertEquals(1, f.events.size());
            assertTrue(f.events.get(0).isCancelled());
            assertTrue(f.children.isEmpty());
            assertEquals(Block.AIR, f.at(64 + height).getId());
            for (int y = 64; y < 64 + height; y++) assertEquals(Block.CACTUS, f.at(y).getId());
            assertEquals(0, f.source.getDamage());
        }
    }

    @Test
    void threeBlockColumnDoesNotGrowHigher() {
        try (Fixture f = new Fixture(3, false)) {
            f.tick();
            assertTrue(f.events.isEmpty());
            assertTrue(f.children.isEmpty());
            assertEquals(Block.AIR, f.at(67).getId());
            assertEquals(0, f.source.getDamage());
        }
    }

    @Test
    void immatureBaseOnlyAdvancesItsAge() {
        try (Fixture f = new Fixture(1, false)) {
            f.source.setDamage(14);
            f.tick();
            assertEquals(15, f.source.getDamage());
            assertTrue(f.events.isEmpty());
            assertTrue(f.children.isEmpty());
            assertEquals(Block.AIR, f.at(65).getId());
        }
    }

    @Test
    void upperSegmentDoesNotRunTheBaseGrowthCycle() {
        try (Fixture f = new Fixture(1, false)) {
            doReturn(Block.get(Block.CACTUS)).when(f.source).down();
            f.tick();
            assertEquals(15, f.source.getDamage());
            assertTrue(f.events.isEmpty());
            assertTrue(f.children.isEmpty());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Level level = mock(Level.class);
        final BlockCactus source = spy(new BlockCactus(15));
        final Map<Integer, Block> cells = new HashMap<>();
        final List<Integer> children = new ArrayList<>();
        final List<BlockGrowEvent> events = new ArrayList<>();
        final MockedStatic<Server> serverScope;

        Fixture(int height, boolean cancelled) {
            source.level = level;
            source.x = -17;
            source.y = 64;
            source.z = -16;
            doReturn(Block.get(Block.SAND)).when(source).down();
            cells.put(64, source);
            for (int y = 65; y < 64 + height; y++) put(y, Block.CACTUS);
            when(level.getBlock(any(Vector3.class))).thenAnswer(invocation -> {
                Vector3 pos = invocation.getArgument(0);
                assertEquals(-17, pos.getFloorX());
                assertEquals(-16, pos.getFloorZ());
                return at(pos.getFloorY());
            });
            when(level.setBlock(any(Vector3.class), any(Block.class), eq(true), eq(true)))
                    .thenAnswer(invocation -> {
                        Vector3 pos = invocation.getArgument(0);
                        Block block = invocation.getArgument(1);
                        children.add(pos.getFloorY());
                        put(pos.getFloorY(), block.getId());
                        return true;
                    });
            when(level.setBlock(any(Vector3.class), any(Block.class))).thenReturn(true);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            serverScope = mockStatic(Server.class);
            serverScope.when(Server::getInstance).thenReturn(server);
            doAnswer(invocation -> {
                BlockGrowEvent event = (BlockGrowEvent) invocation.getArgument(0);
                events.add(event);
                if (cancelled) event.setCancelled(true);
                return null;
            }).when(plugins).callEvent(any(Event.class));
        }

        void tick() {
            source.onUpdate(Level.BLOCK_UPDATE_RANDOM);
        }

        Block at(int y) {
            return cells.computeIfAbsent(y, ignored -> positioned(y, Block.AIR));
        }

        void put(int y, int id) {
            cells.put(y, positioned(y, id));
        }

        Block positioned(int y, int id) {
            Block block = Block.get(id);
            block.level = level;
            block.x = -17;
            block.y = y;
            block.z = -16;
            return block;
        }

        @Override
        public void close() {
            serverScope.close();
        }
    }
}
