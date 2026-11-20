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

class PistonPowerTraversalTest {
    private static final List<BlockFace> ORDER = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.WEST, BlockFace.EAST);
    private static final List<BlockFace> METADATA_FACING = List.of(BlockFace.DOWN, BlockFace.UP,
            BlockFace.SOUTH, BlockFace.NORTH, BlockFace.EAST, BlockFace.WEST);
    private static final MethodHandle POWER;

    static {
        try {
            POWER = MethodHandles.privateLookupIn(BlockPistonBase.class, MethodHandles.lookup())
                    .findVirtual(BlockPistonBase.class, "isPowered", MethodType.methodType(boolean.class));
        } catch (ReflectiveOperationException failure) {
            throw new ExceptionInInitializerError(failure);
        }
    }

    private static boolean power(BlockPistonBase block) throws Throwable {
        return (boolean) POWER.invokeExact(block);
    }

    @Test
    void everyFacingAndWireMaskRetainsTheExactFirstSignalPrefix() throws Throwable {
        verifyMasks(true);
    }

    @Test
    void everyFacingAndLevelPowerMaskRetainsTheExactFirstSignalPrefix() throws Throwable {
        verifyMasks(false);
    }

    private static void verifyMasks(boolean wire) throws Throwable {
        for (int metadata = 0; metadata < 6; metadata++) {
            BlockFace facing = METADATA_FACING.get(metadata);
            for (int mask = 0; mask < 64; mask++) {
                Fixture fixture = new Fixture(metadata);
                if (wire) fixture.wires(mask, 15);
                else fixture.world.powerMask = mask;
                int effective = mask & ~(1 << facing.getIndex());
                assertEquals(effective != 0, power(fixture.piston), "metadata=" + metadata + " mask=" + mask);
                assertEquals(expected(facing, wire ? mask : 0, wire ? mask : 0,
                        wire ? 0 : mask, 0), fixture.events);
            }
        }
    }

    @Test
    void zeroDamageWireStillCallsLevelPowerAndPositiveWireSkipsIt() throws Throwable {
        Fixture fixture = new Fixture(0);
        fixture.wires(63, 0);
        fixture.world.powerMask = 1 << BlockFace.EAST.getIndex();
        assertTrue(power(fixture.piston));
        assertEquals(expected(BlockFace.DOWN, 63, 0, fixture.world.powerMask, 0), fixture.events);

        fixture.events.clear();
        fixture.neighbors[BlockFace.NORTH.getIndex()].damage = 15;
        assertTrue(power(fixture.piston));
        assertEquals(expected(BlockFace.DOWN, 63, 1 << BlockFace.NORTH.getIndex(),
                fixture.world.powerMask, 0), fixture.events);
    }

    @Test
    void facingIsCapturedOnceBeforeCallbacksCanChangeMetadata() throws Throwable {
        Fixture fixture = new Fixture(4); // EAST is excluded for the complete call.
        fixture.onSide = side -> {
            if (side == BlockFace.DOWN) fixture.piston.setDamage(0);
        };
        assertFalse(power(fixture.piston));
        assertEquals(expected(BlockFace.EAST, 0, 0, 0, 0), fixture.events);
        assertEquals(0, fixture.piston.getDamage());
    }

    @Test
    void callbackFailuresKeepTheirIdentityAndStopBeforeLaterNeighbors() throws Throwable {
        for (String stage : List.of("side", "id", "damage", "power")) {
            Fixture fixture = new Fixture(0);
            fixture.wires(63, 0);
            fixture.failAt = stage + ":NORTH";
            RuntimeException failure = new IllegalStateException("controlled " + stage);
            fixture.failure = failure;
            assertSame(failure, assertThrows(RuntimeException.class, () -> power(fixture.piston)));
            List<String> complete = expected(BlockFace.DOWN, 63, 0, 0, 0);
            int stop = complete.indexOf("0:" + fixture.failAt);
            assertTrue(stop >= 0);
            assertEquals(complete.subList(0, stop + 1), fixture.events);

            fixture.failAt = null;
            fixture.events.clear();
            assertFalse(power(fixture.piston));
            assertEquals(complete, fixture.events);
        }
    }

    @Test
    void sameInstanceReentryDoesNotShareTraversalPositionOrResult() throws Throwable {
        Fixture fixture = new Fixture(0);
        fixture.onSide = side -> {
            if (side == BlockFace.UP && fixture.depth == 0) {
                fixture.depth = 1;
                fixture.wires(1 << BlockFace.EAST.getIndex(), 15);
                try {
                    assertTrue(assertDoesNotThrow(() -> power(fixture.piston)));
                } finally {
                    fixture.wires(0, 0);
                    fixture.depth = 0;
                }
            }
        };
        assertFalse(power(fixture.piston));
        List<String> outer = expected(BlockFace.DOWN, 0, 0, 0, 0);
        List<String> trace = new ArrayList<>(outer.subList(0, 2));
        trace.addAll(expected(BlockFace.DOWN, 32, 32, 0, 1));
        trace.addAll(outer.subList(2, outer.size()));
        assertEquals(trace, fixture.events);
    }

    @Test
    void aCallbackCanChangeANeighborWhichHasNotBeenReadYet() throws Throwable {
        Fixture fixture = new Fixture(0);
        fixture.onSide = side -> {
            if (side == BlockFace.NORTH) {
                fixture.neighbors[BlockFace.EAST.getIndex()].wire = true;
                fixture.neighbors[BlockFace.EAST.getIndex()].damage = 15;
            }
        };
        assertTrue(power(fixture.piston));
        assertEquals(expected(BlockFace.DOWN, 32, 32, 0, 0), fixture.events);
    }

    private static List<String> expected(BlockFace facing, int wireMask, int positiveWireMask,
            int levelMask, int depth) {
        int effective = (positiveWireMask | levelMask) & ~(1 << facing.getIndex());
        int last = effective == 0 ? 5 : Integer.numberOfTrailingZeros(effective);
        List<String> events = new ArrayList<>();
        events.add(depth + ":face");
        for (BlockFace side : ORDER.subList(0, last + 1)) {
            if (side == facing) continue;
            events.add(depth + ":side:" + side.name());
            events.add(depth + ":id:" + side.name());
            int bit = 1 << side.getIndex();
            if ((wireMask & bit) != 0) events.add(depth + ":damage:" + side.name());
            if ((positiveWireMask & bit) == 0) events.add(depth + ":power:" + side.name());
        }
        return events;
    }

    private static final class Fixture {
        final List<String> events = new ArrayList<>();
        final Neighbor[] neighbors = new Neighbor[6];
        final World world = new ObjenesisStd().newInstance(World.class);
        final Piston piston;
        Consumer<BlockFace> onSide = side -> {};
        String failAt;
        RuntimeException failure;
        int depth;

        Fixture(int metadata) {
            world.fixture = this;
            for (BlockFace side : ORDER) neighbors[side.getIndex()] = new Neighbor(this, side);
            piston = new Piston(this, metadata);
            piston.level = world;
        }

        void wires(int mask, int damage) {
            for (BlockFace side : ORDER) {
                Neighbor neighbor = neighbors[side.getIndex()];
                neighbor.wire = (mask & (1 << side.getIndex())) != 0;
                neighbor.damage = damage;
            }
        }

        void record(String event) {
            events.add(depth + ":" + event);
            if (event.equals(failAt)) throw failure;
        }
    }

    private static final class Piston extends BlockPiston {
        private final Fixture fixture;

        Piston(Fixture fixture, int metadata) {
            super(metadata);
            this.fixture = fixture;
        }

        @Override
        public BlockFace getBlockFace() {
            fixture.record("face");
            return super.getBlockFace();
        }

        @Override
        public Block getSide(BlockFace side) {
            fixture.record("side:" + side.name());
            fixture.onSide.accept(side);
            return fixture.neighbors[side.getIndex()];
        }
    }

    private static final class Neighbor extends BlockAir {
        private final Fixture fixture;
        private final BlockFace side;
        boolean wire;
        int damage;

        Neighbor(Fixture fixture, BlockFace side) {
            this.fixture = fixture;
            this.side = side;
        }

        @Override
        public int getId() {
            fixture.record("id:" + side.name());
            return wire ? REDSTONE_WIRE : AIR;
        }

        @Override
        public int getDamage() {
            fixture.record("damage:" + side.name());
            return damage;
        }
    }

    private static final class World extends Level {
        Fixture fixture;
        int powerMask;

        private World() {
            super(null, null, null, null);
        }

        @Override
        public boolean isSidePowered(Vector3 block, BlockFace side) {
            assertSame(fixture.neighbors[side.getIndex()], block);
            fixture.record("power:" + side.name());
            return (powerMask & (1 << side.getIndex())) != 0;
        }
    }
}
