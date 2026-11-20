package cn.nukkit.block;

import cn.nukkit.Player;
import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.event.block.BlockSpreadEvent;
import cn.nukkit.item.ItemDye;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.FullChunk;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Real activation and collision scan; only world storage, randomness and plugin callbacks are controlled. */
@Isolated("native block registry initialization")
@Timeout(20)
class SeaPickleSpreadEventTest {
    private static final Cell SOURCE = new Cell(8, 64, 8, 0);
    private static final Cell TARGET = new Cell(9, 64, 8, 0);

    @BeforeAll
    static void initialize() {
        if (Block.list == null) Block.init();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void acceptedSpreadDispatchesExactSourceTargetAndStateBeforeBothWrites(boolean creative) {
        try (Fixture f = new Fixture(0)) {
            when(f.player.isCreative()).thenReturn(creative);
            f.onEvent = event -> {
                if (event instanceof BlockSpreadEvent spread) {
                    assertSame(f.source, spread.getSource());
                    assertSame(f.level, spread.getBlock().getLevel());
                    assertEquals(TARGET, Cell.at(spread.getBlock(), spread.getBlock().layer));
                    assertEquals(new State(Block.WATER, 0), State.of(spread.getBlock()));
                    assertEquals(new State(Block.SEA_PICKLE, 2), State.of(spread.getNewState()));
                    assertNull(spread.getNewState().getLevel(), "native proposed state starts unbound");
                    assertEquals(new State(Block.WATER, 0), f.state(TARGET));
                    assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
                    assertEquals(1, f.writes.size(), "only the main Grow has committed before Spread dispatch");
                }
            };

            assertTrue(f.activate());

            assertEquals(2, f.events.size());
            assertEquals(BlockGrowEvent.class, f.events.get(0).getClass());
            assertInstanceOf(BlockSpreadEvent.class, f.events.get(1));
            assertEquals(List.of(
                    new Write(SOURCE, new State(Block.SEA_PICKLE, 3), false, true),
                    new Write(TARGET.layer(1), new State(Block.WATER, 0), true, false),
                    new Write(TARGET, new State(Block.SEA_PICKLE, 2), true, true)), f.writes);
            assertEquals(new State(Block.SEA_PICKLE, 3), f.state(SOURCE));
            assertEquals(new State(Block.SEA_PICKLE, 2), f.state(TARGET));
            assertEquals(new State(Block.WATER, 0), f.state(TARGET.layer(1)));
            assertEquals(creative ? 3 : 2, f.boneMeal.getCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8})
    void cancelledSpreadPreservesBothLayersButKeepsMainGrowthAndItsCost(int waterData) {
        try (Fixture f = new Fixture(waterData)) {
            f.onEvent = event -> {
                if (event instanceof BlockSpreadEvent spread) spread.setCancelled();
            };

            assertTrue(f.activate());

            assertEquals(2, f.events.size());
            assertTrue(((BlockSpreadEvent) f.events.get(1)).isCancelled());
            assertEquals(List.of(new Write(SOURCE, new State(Block.SEA_PICKLE, 3), false, true)), f.writes);
            assertEquals(new State(Block.WATER, waterData), f.state(TARGET));
            assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(new State(Block.SEA_PICKLE, 3), f.state(SOURCE));
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    @Test
    void cancelledMainGrowthDoesNotSpreadWriteOrConsumeBoneMeal() {
        try (Fixture f = new Fixture(0)) {
            f.onEvent = event -> ((BlockGrowEvent) event).setCancelled();

            assertFalse(f.activate());

            assertEquals(1, f.events.size());
            assertEquals(BlockGrowEvent.class, f.events.get(0).getClass());
            assertTrue(f.writes.isEmpty());
            assertEquals(new State(Block.SEA_PICKLE, 0), f.state(SOURCE));
            assertEquals(new State(Block.WATER, 0), f.state(TARGET));
            assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(3, f.boneMeal.getCount());
        }
    }

    @Test
    void cancellingOneCoralDestinationDoesNotCancelAnotherOrCreateExtraChildren() {
        try (Fixture f = new Fixture(0)) {
            Cell accepted = new Cell(10, 64, 8, 0);
            f.put(new Cell(10, 63, 8, 0), Block.CORAL_BLOCK, 0);
            f.put(accepted, Block.WATER, 8);
            f.onEvent = event -> {
                if (event instanceof BlockSpreadEvent spread && spread.getBlock().getFloorX() == TARGET.x()) {
                    spread.setCancelled();
                }
            };

            assertTrue(f.activate());

            assertEquals(3, f.events.size());
            assertEquals(List.of(
                    new Write(SOURCE, new State(Block.SEA_PICKLE, 3), false, true),
                    new Write(accepted.layer(1), new State(Block.WATER, 0), true, false),
                    new Write(accepted, new State(Block.SEA_PICKLE, 2), true, true)), f.writes);
            assertEquals(new State(Block.WATER, 0), f.state(TARGET));
            assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(new State(Block.SEA_PICKLE, 2), f.state(accepted));
            assertEquals(new State(Block.WATER, 0), f.state(accepted.layer(1)));
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    @Test
    void acceptedSpreadWritesTheMetadataChosenByItsListener() {
        try (Fixture f = new Fixture(0)) {
            f.onEvent = event -> {
                if (event instanceof BlockSpreadEvent spread) spread.getNewState().setDamage(1);
            };

            assertTrue(f.activate());

            assertEquals(2, f.events.size());
            assertEquals(new State(Block.SEA_PICKLE, 1), f.state(TARGET));
            assertEquals(new State(Block.WATER, 0), f.state(TARGET.layer(1)));
            assertEquals(3, f.writes.size());
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    @Test
    void missedRandomAttemptKeepsWaterAndOnlyGrowsTheOriginalCluster() {
        try (Fixture f = new Fixture(0)) {
            when(f.random.nextInt(6)).thenReturn(1);

            assertTrue(f.activate());

            assertEquals(1, f.events.size());
            assertEquals(List.of(new Write(SOURCE, new State(Block.SEA_PICKLE, 3), false, true)), f.writes);
            assertEquals(new State(Block.WATER, 0), f.state(TARGET));
            assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 8, 1})
    void onlyNativeWaterStatesCanReceiveANewPickle(int waterData) {
        try (Fixture f = new Fixture(waterData)) {
            assertTrue(f.activate());

            boolean eligible = waterData == 0 || waterData == 8;
            assertEquals(eligible ? 2 : 1, f.events.size());
            assertEquals(eligible ? 3 : 1, f.writes.size());
            assertEquals(new State(eligible ? Block.SEA_PICKLE : Block.WATER,
                    eligible ? 2 : waterData), f.state(TARGET));
            assertEquals(new State(eligible ? Block.WATER : Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    @Test
    void waterWithoutCoralDoesNotSpread() {
        try (Fixture f = new Fixture(0)) {
            f.put(new Cell(9, 63, 8, 0), Block.STONE, 0);

            assertTrue(f.activate());

            assertEquals(1, f.events.size());
            assertEquals(List.of(new Write(SOURCE, new State(Block.SEA_PICKLE, 3), false, true)), f.writes);
            assertEquals(new State(Block.WATER, 0), f.state(TARGET));
            assertEquals(new State(Block.AIR, 0), f.state(TARGET.layer(1)));
            assertEquals(2, f.boneMeal.getCount());
        }
    }

    private record Cell(int x, int y, int z, int layer) {
        static Cell at(Vector3 position, int layer) {
            return new Cell(position.getFloorX(), position.getFloorY(), position.getFloorZ(), layer);
        }

        Cell layer(int value) {
            return new Cell(x, y, z, value);
        }
    }

    private record State(int id, int data) {
        static State of(Block block) {
            return new State(block.getId(), block.getDamage());
        }
    }

    private record Write(Cell cell, State state, boolean direct, boolean update) {}

    private static final class Fixture implements AutoCloseable {
        final Level level = mock(Level.class);
        final Player player = mock(Player.class);
        final ThreadLocalRandom random = mock(ThreadLocalRandom.class);
        final ItemDye boneMeal = new ItemDye(15, 3);
        final Map<Cell, State> cells = new HashMap<>();
        final List<Write> writes = new ArrayList<>();
        final List<Event> events = new ArrayList<>();
        final BlockSeaPickle source;
        final MockedStatic<Server> serverScope;
        final MockedStatic<ThreadLocalRandom> randomScope;
        Consumer<Event> onEvent = event -> {};

        Fixture(int waterData) {
            put(SOURCE, Block.SEA_PICKLE, 0);
            put(SOURCE.layer(1), Block.WATER, 0);
            put(new Cell(8, 63, 8, 0), Block.STONE, 0);
            put(new Cell(9, 63, 8, 0), Block.CORAL_BLOCK, 0);
            put(TARGET, Block.WATER, waterData);
            source = (BlockSeaPickle) block(SOURCE);
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getMaxBlockY()).thenReturn(319);
            BaseFullChunk chunk = mock(BaseFullChunk.class);
            when(level.getChunkIfLoaded(anyInt(), anyInt())).thenAnswer(c ->
                    c.getArgument(0, Integer.class) == 0 && c.getArgument(1, Integer.class) == 0 ? chunk : null);
            when(chunk.getBlockId(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c ->
                    state(new Cell(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3))).id());
            when(level.getBlock(nullable(FullChunk.class), anyInt(), anyInt(), anyInt(), anyInt(), anyBoolean()))
                    .thenAnswer(c -> block(new Cell(c.getArgument(1), c.getArgument(2),
                            c.getArgument(3), c.getArgument(4))));
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(c ->
                    block(new Cell(c.getArgument(0), c.getArgument(1), c.getArgument(2), 0)));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(c ->
                    block(new Cell(c.getArgument(0), c.getArgument(1), c.getArgument(2), c.getArgument(3))));
            when(level.getBlock(any(Vector3.class))).thenAnswer(c -> block(Cell.at(c.getArgument(0), 0)));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(c ->
                    block(Cell.at(c.getArgument(0), c.getArgument(1))));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(c -> write(Cell.at(c.getArgument(0), 0), c.getArgument(1),
                            c.getArgument(2), c.getArgument(3)));
            when(level.setBlock(any(Vector3.class), anyInt(), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(c -> write(Cell.at(c.getArgument(0), c.getArgument(1)), c.getArgument(2),
                            c.getArgument(3), c.getArgument(4)));
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            serverScope = mockStatic(Server.class);
            serverScope.when(Server::getInstance).thenReturn(server);
            doAnswer(c -> {
                Event event = c.getArgument(0);
                events.add(event);
                onEvent.accept(event);
                return null;
            }).when(plugins).callEvent(any(Event.class));
            when(random.nextInt(6)).thenReturn(0);
            when(random.nextInt(3)).thenReturn(2);
            randomScope = mockStatic(ThreadLocalRandom.class);
            randomScope.when(ThreadLocalRandom::current).thenReturn(random);
        }

        boolean activate() {
            return source.onActivate(boneMeal, player);
        }

        void put(Cell cell, int id, int data) {
            cells.put(cell, new State(id, data));
        }

        State state(Cell cell) {
            return cells.getOrDefault(cell, new State(Block.AIR, 0));
        }

        Block block(Cell cell) {
            State value = state(cell);
            Block block = Block.get(value.id(), value.data());
            block.setLevel(level);
            block.setComponents(cell.x(), cell.y(), cell.z());
            block.layer = cell.layer();
            return block;
        }

        boolean write(Cell cell, Block block, boolean direct, boolean update) {
            State value = State.of(block);
            writes.add(new Write(cell, value, direct, update));
            cells.put(cell, value);
            block.setLevel(level);
            block.setComponents(cell.x(), cell.y(), cell.z());
            block.layer = cell.layer();
            return true;
        }

        @Override
        public void close() {
            randomScope.close();
            serverScope.close();
        }
    }
}
