package cn.nukkit.block;

import cn.nukkit.level.Level;
import cn.nukkit.math.BlockFace;
import cn.nukkit.math.Vector3;
import org.junit.jupiter.api.Test;
import org.objenesis.ObjenesisStd;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;

class FrostedIceNeighborTraversalTest {
    private static final List<BlockFace> ORDER = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);
    private static final MethodHandle COUNT;

    static {
        try {
            COUNT = MethodHandles.privateLookupIn(BlockIceFrosted.class, MethodHandles.lookup())
                    .findVirtual(BlockIceFrosted.class, "countNeighbors", MethodType.methodType(int.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static int count(BlockIceFrosted ice) throws Throwable {
        return (int) COUNT.invokeExact(ice);
    }

    @Test
    void everyMaskReadsOnlyThroughTheFourthMatchingNeighbor() throws Throwable {
        for (int mask = 0; mask < 64; mask++) {
            Fixture fixture = new Fixture(mask);
            assertEquals(Math.min(4, Integer.bitCount(mask)), count(fixture.ice), "mask=" + mask);
            assertEquals(expected(mask, 0), fixture.events);
        }
    }

    @Test
    void normalUpdateSchedulesExactlyOnceOnlyBelowTwoNeighborsWithoutMelting() {
        for (int mask = 0; mask < 64; mask++) {
            Fixture fixture = new Fixture(mask);
            fixture.ice.setDamage(2);
            assertEquals(0, fixture.ice.onUpdate(Level.BLOCK_UPDATE_NORMAL));
            assertEquals(expected(mask, 0), fixture.events);
            assertEquals(2, fixture.ice.getDamage());
            assertEquals(Integer.bitCount(mask) < 2 ? 1 : 0, fixture.world.schedules.size());
            for (Schedule schedule : fixture.world.schedules) {
                assertSame(fixture.ice, schedule.block());
                assertTrue(schedule.delay() >= 20 && schedule.delay() < 40);
            }
        }
    }

    @Test
    void callbackFailuresKeepTheirIdentityAndStopBeforeLaterNeighbors() throws Throwable {
        for (String stage : List.of("side", "id")) {
            Fixture fixture = new Fixture(0);
            fixture.failAt = stage + ":NORTH";
            RuntimeException failure = new IllegalStateException("controlled " + stage);
            fixture.failure = failure;
            assertSame(failure, assertThrows(RuntimeException.class, () -> count(fixture.ice)));
            List<String> complete = expected(0, 0);
            int stop = complete.indexOf("0:" + fixture.failAt);
            assertTrue(stop >= 0);
            assertEquals(complete.subList(0, stop + 1), fixture.events);

            fixture.failAt = null;
            fixture.events.clear();
            assertEquals(0, count(fixture.ice));
            assertEquals(complete, fixture.events);
        }
    }

    @Test
    void sameInstanceReentryDoesNotShareTraversalPositionOrCount() throws Throwable {
        Fixture fixture = new Fixture(0);
        fixture.onSide = side -> {
            if (side == BlockFace.DOWN && fixture.depth == 0) {
                fixture.depth = 1;
                fixture.mask = 63;
                try {
                    assertEquals(4, assertDoesNotThrow(() -> count(fixture.ice)));
                } finally {
                    fixture.mask = 0;
                    fixture.depth = 0;
                }
            }
        };
        assertEquals(0, count(fixture.ice));
        List<String> outer = expected(0, 0);
        List<String> trace = new ArrayList<>(outer.subList(0, 1));
        trace.addAll(expected(63, 1));
        trace.addAll(outer.subList(1, outer.size()));
        assertEquals(trace, fixture.events);
    }

    @Test
    void aCallbackCanChangeANeighborWhichHasNotBeenReadYet() throws Throwable {
        Fixture fixture = new Fixture(0);
        fixture.onSide = side -> {
            if (side == BlockFace.NORTH) fixture.mask = 1 << BlockFace.EAST.getIndex();
        };
        assertEquals(1, count(fixture.ice));
        assertEquals(expected(32, 0), fixture.events);
    }

    private static List<String> expected(int mask, int depth) {
        List<BlockFace> matching = ORDER.stream()
                .filter(side -> (mask & (1 << side.getIndex())) != 0).toList();
        int length = matching.size() < 4 ? 6 : matching.get(3).getIndex() + 1;
        List<String> events = new ArrayList<>();
        for (BlockFace side : ORDER.subList(0, length)) {
            events.add(depth + ":side:" + side.name());
            events.add(depth + ":id:" + side.name());
        }
        return events;
    }

    private record Schedule(Block block, int delay) {}

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final Neighbor[] neighbors = new Neighbor[6];
        final World world = new ObjenesisStd().newInstance(World.class);
        final Ice ice = new Ice(this);
        Consumer<BlockFace> onSide = side -> {};
        String failAt;
        RuntimeException failure;
        int mask;
        int depth;

        Fixture(int mask) {
            this.mask = mask;
            world.schedules = new ArrayList<>();
            for (BlockFace side : ORDER) neighbors[side.getIndex()] = new Neighbor(this, side);
            ice.level = world;
        }

        void record(String event) {
            events.add(depth + ":" + event);
            if (event.equals(failAt)) throw failure;
        }
    }

    private static final class Ice extends BlockIceFrosted {
        private final Fixture fixture;

        Ice(Fixture fixture) {
            this.fixture = fixture;
        }

        @Override
        public Block getSide(BlockFace side) {
            fixture.record("side:" + side.name());
            fixture.onSide.accept(side);
            return fixture.neighbors[side.getIndex()];
        }

        @Override
        protected void slightlyMelt(boolean source) {
            throw new AssertionError("NORMAL update must schedule rather than melt");
        }
    }

    private static final class Neighbor extends BlockAir {
        private final Fixture fixture;
        private final BlockFace side;

        Neighbor(Fixture fixture, BlockFace side) {
            this.fixture = fixture;
            this.side = side;
        }

        @Override
        public int getId() {
            fixture.record("id:" + side.name());
            return (fixture.mask & (1 << side.getIndex())) == 0 ? AIR : ICE_FROSTED;
        }
    }

    private static final class World extends Level {
        List<Schedule> schedules;

        private World() {
            super(null, null, null, null);
        }

        @Override
        public void scheduleUpdate(Block block, int delay) {
            schedules.add(new Schedule(block, delay));
        }

        @Override
        public boolean setBlock(Vector3 position, Block block, boolean direct) {
            throw new AssertionError("NORMAL update must not write a block");
        }
    }
}
