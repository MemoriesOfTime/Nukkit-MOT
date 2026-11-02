package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockFromToEvent;
import cn.nukkit.event.block.LiquidFlowEvent;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockLiquidFlowDirectionsTest {
    private static final int Y = 64;
    private static final List<BlockFace> SIDES = List.of(
            BlockFace.WEST, BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH);

    @BeforeAll
    static void initBlocks() {
        cn.nukkit.MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void flatFloorSpreadsToEveryTiedSideInOrder(boolean lava) {
        Fixture f = new Fixture(lava);
        f.tick();
        f.assertFlows(SIDES);
    }

    static Stream<Arguments> downhillChoices() {
        return Stream.of(false, true).flatMap(lava -> IntStream.range(1, 16)
                .mapToObj(mask -> Arguments.of(lava, mask)));
    }

    @ParameterizedTest
    @MethodSource("downhillChoices")
    void immediateDownhillOutletsWinAndKeepAllEqualChoices(boolean lava, int outlets) {
        Fixture f = new Fixture(lava);
        List<BlockFace> expected = new ArrayList<>();
        for (int i = 0; i < SIDES.size(); i++) {
            if ((outlets & (1 << i)) != 0) {
                BlockFace face = SIDES.get(i);
                f.put(face.getXOffset(), Y - 1, face.getZOffset(), 0, new BlockAir());
                expected.add(face);
            }
        }
        f.tick();
        f.assertFlows(expected);
    }

    static Stream<Arguments> shortestOutlets() {
        return Stream.of(false, true).flatMap(lava -> SIDES.stream()
                .map(face -> Arguments.of(lava, face)));
    }

    @ParameterizedTest
    @MethodSource("shortestOutlets")
    void shortestOutletWinsInEachDirection(boolean lava, BlockFace nearest) {
        Fixture f = new Fixture(lava);
        f.put(nearest.getXOffset() * 2, Y - 1, nearest.getZOffset() * 2, 0, new BlockAir());
        BlockFace farther = nearest.getOpposite();
        f.put(farther.getXOffset() * 3, Y - 1, farther.getZOffset() * 3, 0, new BlockAir());
        f.tick();
        f.assertFlows(List.of(nearest));
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void fullyEnclosedSourceDoesNotWriteOrScheduleAnything(boolean lava) {
        Fixture f = new Fixture(lava);
        for (BlockFace face : SIDES) {
            f.put(face.getXOffset(), Y, face.getZOffset(), 0, new BlockStone());
        }
        f.tick();
        f.assertFlows(List.of());
    }

    @Test
    void waterFlowsIntoSecondLayerWithoutBreakingWaterloggableBlock() {
        Fixture f = new Fixture(false);
        f.put(-1, Y, 0, 0, new BlockLever());
        f.tick();
        List<Flow> expected = new ArrayList<>(f.flows(SIDES));
        expected.set(0, new Flow(new Pos(-1, Y, 0, 1), Block.WATER, 1));
        f.assertFlowRecords(expected);
        assertEquals(Block.LEVER, f.get(-1, Y, 0, 0).getId());
        assertEquals(Block.WATER, f.get(-1, Y, 0, 1).getId());
        assertEquals(List.of(), f.breaks);
    }

    @Test
    void existingSecondLayerSourceIsNotOverwritten() {
        Fixture f = new Fixture(false);
        f.put(-1, Y, 0, 0, new BlockLever());
        f.put(-1, Y, 0, 1, new BlockWater());
        f.tick();
        f.assertFlows(List.of(BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH));
        assertEquals(0, f.get(-1, Y, 0, 1).getDamage());
    }

    @Test
    void cancellingOneFlowLeavesOtherDirectionsAndTheirOrderIntact() {
        Fixture f = new Fixture(false);
        f.onEvent = event -> {
            if (event instanceof LiquidFlowEvent flow && flow.getTo().getFloorX() == -1) {
                flow.setCancelled(true);
            }
        };
        f.tick();
        assertEquals(f.flows(SIDES), f.events);
        List<Flow> remaining = f.flows(List.of(BlockFace.EAST, BlockFace.NORTH, BlockFace.SOUTH));
        assertEquals(remaining, f.writes);
        assertEquals(remaining.stream().map(Flow::pos).toList(), f.scheduled);
        assertEquals(Block.AIR, f.get(-1, Y, 0, 0).getId());
        List<String> expectedOrder = new ArrayList<>();
        expectedOrder.add("event:" + f.flows(List.of(BlockFace.WEST)).get(0));
        expectedOrder.addAll(Fixture.traceFor(remaining));
        assertEquals(expectedOrder, f.trace);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void earlierFlowCallbackCanOpenOrCloseALaterNeighbour(boolean initiallyBlocked) {
        Fixture f = new Fixture(false);
        if (initiallyBlocked) {
            f.put(1, Y, 0, 0, new BlockStone());
        }
        f.onEvent = event -> {
            if (event instanceof LiquidFlowEvent flow && flow.getTo().getFloorX() == -1) {
                f.put(1, Y, 0, 0, initiallyBlocked ? new BlockAir() : new BlockStone());
            }
        };
        f.tick();
        // With no reachable drop, even an initially blocked side shares the
        // no-outlet tie. Its block must be read again after the west callback.
        f.assertFlows(initiallyBlocked ? SIDES : List.of(BlockFace.WEST, BlockFace.NORTH, BlockFace.SOUTH));
        assertEquals(initiallyBlocked ? Block.WATER : Block.STONE, f.get(1, Y, 0, 0).getId());
    }

    @Test
    void cancelledSourceDecayStillUsesTheOriginalSideFlowSequence() {
        Fixture f = new Fixture(false, 1);
        f.put(0, Y + 1, 0, 0, new BlockWater());
        f.onEvent = event -> {
            if (event instanceof BlockFromToEvent change) {
                change.setCancelled(true);
            }
        };
        f.tick();
        assertEquals(1, f.get(0, Y, 0, 0).getDamage());
        assertEquals(List.of(new Flow(new Pos(0, Y, 0, 0), Block.WATER, 8)), f.sourceChanges);
        assertEquals(f.flows(SIDES), f.events);
        assertEquals(f.flows(SIDES), f.writes);
        List<String> expected = new ArrayList<>();
        expected.add("source:" + f.sourceChanges.get(0));
        expected.addAll(Fixture.traceFor(f.flows(SIDES)));
        assertEquals(expected, f.trace);
    }

    private record Pos(int x, int y, int z, int layer) {
        static Pos at(Vector3 vector, int layer) {
            return new Pos(vector.getFloorX(), vector.getFloorY(), vector.getFloorZ(), layer);
        }
    }

    private record Flow(Pos pos, int id, int depth) {}

    private static final class Fixture {
        final Level level = mock(Level.class);
        final Map<Pos, Block> blocks = new HashMap<>();
        final List<Flow> events = new ArrayList<>();
        final List<Flow> sourceChanges = new ArrayList<>();
        final List<Flow> writes = new ArrayList<>();
        final List<Pos> scheduled = new ArrayList<>();
        final List<Pos> breaks = new ArrayList<>();
        final List<String> trace = new ArrayList<>();
        final BlockLiquid source;
        Consumer<Event> onEvent = event -> {};

        Fixture(boolean lava) {
            this(lava, 0);
        }

        Fixture(boolean lava, int depth) {
            source = lava ? new BlockLava(depth) : new BlockWater(depth);
            source.level = level;
            source.y = Y;
            put(0, Y, 0, 0, source);
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(level.getServer()).thenReturn(server);
            when(server.getPluginManager()).thenReturn(plugins);
            when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getMinBlockY()).thenReturn(-64);
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    get(i.getArgument(0), i.getArgument(1), i.getArgument(2), 0));
            when(level.getBlock(anyInt(), anyInt(), anyInt(), anyInt())).thenAnswer(i ->
                    get(i.getArgument(0), i.getArgument(1), i.getArgument(2), i.getArgument(3)));
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(i -> {
                Vector3 p = i.getArgument(0);
                return get(p.getFloorX(), p.getFloorY(), p.getFloorZ(), i.getArgument(1));
            });
            when(level.setBlock(any(Vector3.class), anyInt(), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(i -> {
                        Pos pos = Pos.at(i.getArgument(0), i.getArgument(1));
                        Block block = i.getArgument(2);
                        Flow flow = new Flow(pos, block.getId(), block.getDamage());
                        writes.add(flow);
                        trace.add("write:" + flow);
                        blocks.put(pos, block.clone());
                        assertTrue((boolean) i.getArgument(3), "liquid writes retain direct=true");
                        assertTrue((boolean) i.getArgument(4), "liquid writes retain update=true");
                        return true;
                    });
            doAnswer(i -> {
                Block block = i.getArgument(0);
                Pos pos = Pos.at(block, block.layer);
                scheduled.add(pos);
                trace.add("schedule:" + pos);
                assertEquals(source.tickRate(), (int) i.getArgument(1));
                return null;
            }).when(level).scheduleUpdate(any(Block.class), anyInt());
            when(level.useBreakOn(any(Vector3.class), any())).thenAnswer(i -> {
                Vector3 block = i.getArgument(0);
                breaks.add(Pos.at(block, 0));
                return null;
            });
            doAnswer(i -> {
                Event event = i.getArgument(0);
                if (event instanceof LiquidFlowEvent flow) {
                    Flow record = new Flow(Pos.at(flow.getTo(), flow.getTo().layer),
                            flow.getSource().getId(), flow.getNewFlowDecay());
                    events.add(record);
                    trace.add("event:" + record);
                } else if (event instanceof BlockFromToEvent change) {
                    Flow record = new Flow(Pos.at(change.getFrom(), change.getFrom().layer),
                            change.getTo().getId(), change.getTo().getDamage());
                    sourceChanges.add(record);
                    trace.add("source:" + record);
                }
                onEvent.accept(event);
                return null;
            }).when(plugins).callEvent(any(Event.class));
        }

        void tick() {
            assertEquals(0, source.onUpdate(Level.BLOCK_UPDATE_SCHEDULED));
        }

        void put(int x, int y, int z, int layer, Block block) {
            blocks.put(new Pos(x, y, z, layer), block);
        }

        Block get(int x, int y, int z, int layer) {
            Block prototype = blocks.get(new Pos(x, y, z, layer));
            Block block = prototype != null ? prototype.clone()
                    : layer == 0 && y < Y ? new BlockStone() : new BlockAir();
            block.level = level;
            block.x = x;
            block.y = y;
            block.z = z;
            block.layer = layer;
            return block;
        }

        List<Flow> flows(List<BlockFace> faces) {
            return faces.stream().map(face -> new Flow(
                    new Pos(face.getXOffset(), Y, face.getZOffset(), 0),
                    source.getId(), source.getFlowDecayPerBlock())).toList();
        }

        void assertFlows(List<BlockFace> faces) {
            assertFlowRecords(flows(faces));
        }

        void assertFlowRecords(List<Flow> expected) {
            assertEquals(expected, events);
            assertEquals(expected, writes);
            assertEquals(expected.stream().map(Flow::pos).toList(), scheduled);
            assertEquals(traceFor(expected), trace);
        }

        static List<String> traceFor(List<Flow> flows) {
            return flows.stream().flatMap(flow -> Stream.of(
                    "event:" + flow, "write:" + flow, "schedule:" + flow.pos())).toList();
        }
    }
}
