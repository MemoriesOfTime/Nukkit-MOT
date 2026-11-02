package cn.nukkit.block;

import cn.nukkit.Server;
import cn.nukkit.block.custom.properties.BlockProperty;
import cn.nukkit.event.Event;
import cn.nukkit.event.block.BlockGrowEvent;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import cn.nukkit.plugin.PluginManager;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class BlockCropsGrowthReadTest {
    @BeforeAll
    static void initialize() {
        Block.init();
    }

    static Stream<Arguments> growthCases() {
        return IntStream.range(0, 3).boxed().flatMap(kind -> Stream.of(0, 6)
                .flatMap(stage -> Stream.of("accept", "cancel", "change")
                        .map(mode -> Arguments.of(kind, stage, mode))));
    }

    @ParameterizedTest
    @MethodSource("growthCases")
    void nativeCropReusesGrowthAndPreservesMutationEventAndWriteOrder(int kind, int stage, String mode) {
        try (Fixture f = new Fixture(crop(kind, stage), mode)) {
            int result = f.growthAttempt();
            assertEquals(stage + 1, f.source.getDamage(), "source still mutates before the event, including cancellation");
            assertEquals(List.of(stage + 1), f.eventSourceStages);
            assertEquals(List.of(stage + 1), f.eventNewStages);
            assertEquals(mode.equals("cancel") ? Level.BLOCK_UPDATE_RANDOM : 0, result);
            assertEquals(mode.equals("cancel") ? List.of("event") : List.of("event", "write"), f.trace);
            assertEquals(mode.equals("cancel") ? List.of() : List.of(mode.equals("change") ? 2 : stage + 1), f.writes);
            assertEquals(1, growthReads(f.source), "native growth lookup is not repeated before mutation");
        }
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 2})
    void matureCropRetainsItsOneReadAndNoWrite(int kind) {
        try (Fixture f = new Fixture(crop(kind, 7), "accept")) {
            assertEquals(0, f.growthAttempt());
            assertEquals(7, f.source.getDamage());
            assertEquals(List.of(), f.trace);
            assertEquals(1, growthReads(f.source));
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"accept", "cancel"})
    void unknownSubclassKeepsItsSecondVirtualGrowthRead(String mode) {
        try (Fixture f = new Fixture(new ChangingWheat(), mode)) {
            assertEquals(mode.equals("cancel") ? Level.BLOCK_UPDATE_RANDOM : 0, f.growthAttempt());
            assertEquals(5, f.source.getDamage(), "the extension's second result still drives mutation");
            assertEquals(List.of(5), f.eventSourceStages);
            assertEquals(List.of(5), f.eventNewStages);
            assertEquals(mode.equals("cancel") ? List.of() : List.of(5), f.writes);
            assertEquals(2, growthReads(f.source));
        }
    }

    private static BlockCrops crop(int kind, int stage) {
        return switch (kind) {
            case 0 -> new BlockWheat(stage);
            case 1 -> new BlockCarrot(stage);
            case 2 -> new BlockPotato(stage);
            default -> throw new IllegalArgumentException("Unknown fixture crop");
        };
    }

    private static long growthReads(BlockCrops crop) {
        return mockingDetails(crop).getInvocations().stream()
                .filter(invocation -> invocation.getMethod().getName().equals("getPropertyValue")
                        && invocation.getArguments().length == 1
                        && invocation.getArguments()[0] == BlockCrops.GROWTH)
                .count();
    }

    private static final class ChangingWheat extends BlockWheat {
        private int growthReads;

        @Override
        @SuppressWarnings("unchecked")
        public <V extends Serializable> V getPropertyValue(BlockProperty<V> property) {
            if (property == GROWTH) {
                return (V) Integer.valueOf(growthReads++ == 0 ? 0 : 4);
            }
            return super.getPropertyValue(property);
        }
    }

    private static final class Fixture implements AutoCloseable {
        final Level level = mock(Level.class);
        final BlockCrops source;
        final List<String> trace = new ArrayList<>();
        final List<Integer> eventSourceStages = new ArrayList<>();
        final List<Integer> eventNewStages = new ArrayList<>();
        final List<Integer> writes = new ArrayList<>();
        final MockedStatic<Server> serverScope;

        Fixture(BlockCrops original, String mode) {
            source = spy(original);
            assertEquals(original.getClass(), source.getClass(), "inline spy retains the exact native class");
            source.level = level;
            source.x = -17;
            source.y = 64;
            source.z = -16;
            Server server = mock(Server.class);
            PluginManager plugins = mock(PluginManager.class);
            when(server.getPluginManager()).thenReturn(plugins);
            serverScope = mockStatic(Server.class);
            serverScope.when(Server::getInstance).thenReturn(server);
            doAnswer(invocation -> {
                BlockGrowEvent event = (BlockGrowEvent) invocation.getArgument(0);
                assertSame(source, event.getBlock());
                assertNotSame(source, event.getNewState());
                trace.add("event");
                eventSourceStages.add(event.getBlock().getDamage());
                eventNewStages.add(event.getNewState().getDamage());
                if (mode.equals("cancel")) {
                    event.setCancelled(true);
                } else if (mode.equals("change")) {
                    event.getNewState().setDamage(2);
                }
                return null;
            }).when(plugins).callEvent(any(Event.class));
            when(level.setBlock(any(Vector3.class), any(Block.class), anyBoolean(), anyBoolean()))
                    .thenAnswer(invocation -> {
                        assertSame(source, invocation.getArgument(0));
                        assertFalse((boolean) invocation.getArgument(2), "direct=false stays unchanged");
                        assertTrue((boolean) invocation.getArgument(3), "update=true stays unchanged");
                        trace.add("write");
                        writes.add(((Block) invocation.getArgument(1)).getDamage());
                        return true;
                    });
        }

        int growthAttempt() {
            // The engine's final SplittableRandom is not replaced or reseeded.
            // Rejected coin flips are checked; retries cannot write or fire an event.
            for (int attempt = 0; attempt < 128; attempt++) {
                clearInvocations(source);
                int result = source.onUpdate(Level.BLOCK_UPDATE_RANDOM);
                if (growthReads(source) != 0) {
                    return result;
                }
                assertEquals(Level.BLOCK_UPDATE_RANDOM, result);
                assertEquals(List.of(), trace);
            }
            throw new AssertionError("No growth branch in 128 random-tick attempts");
        }

        @Override
        public void close() {
            serverScope.close();
        }
    }
}
