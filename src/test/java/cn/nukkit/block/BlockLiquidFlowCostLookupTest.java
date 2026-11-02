package cn.nukkit.block;

import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.math.Vector3;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class BlockLiquidFlowCostLookupTest {
    private static Method calculateFlowCost;

    @BeforeAll
    static void initialize() throws Exception {
        Block.init();
        calculateFlowCost = BlockLiquid.class.getDeclaredMethod("calculateFlowCost",
                int.class, int.class, int.class, int.class, int.class, int.class, int.class);
        calculateFlowCost.setAccessible(true);
    }

    static Stream<Arguments> cases() {
        return Stream.of(false, true).flatMap(lava -> Stream.of(false, true)
                .flatMap(zeroHash -> Stream.of(BlockLiquid.BLOCKED, BlockLiquid.CAN_FLOW,
                                BlockLiquid.CAN_FLOW_DOWN)
                        .map(status -> Arguments.of(lava, zeroHash, status))));
    }

    @ParameterizedTest
    @MethodSource("cases")
    void cachedStatusUsesOneLookupAndNeverReadsTheWorld(boolean lava, boolean zeroHash, byte status)
            throws Exception {
        Fixture f = new Fixture(lava, zeroHash, status);
        f.visited.put(f.westHash, status);
        f.visited.resetCounts();

        assertEquals(status == BlockLiquid.CAN_FLOW_DOWN ? 1 : 1000, f.calculate());
        assertEquals(List.of(), f.reads);
        assertEquals(status, f.visited.peek(f.westHash));
        assertEquals(2, f.visited.defaultReturnValue(), "the map's default is not changed");
        assertEquals(0, f.visited.putCalls);
        assertEquals(status == BlockLiquid.CAN_FLOW_DOWN ? 1 : 2, f.visited.lookupCalls());
    }

    @ParameterizedTest
    @MethodSource("cases")
    void uncachedStatusIsStoredWithoutAnImmediateReread(boolean lava, boolean zeroHash, byte status)
            throws Exception {
        Fixture f = new Fixture(lava, zeroHash, status);
        f.visited.resetCounts();

        assertEquals(status == BlockLiquid.CAN_FLOW_DOWN ? 1 : 1000, f.calculate());
        List<String> expectedReads = new ArrayList<>();
        expectedReads.add(f.westX + ":" + f.y + ":" + f.z);
        if (status != BlockLiquid.BLOCKED) {
            if (!lava) {
                expectedReads.add(f.westX + ":" + f.y + ":" + f.z + ":1");
            }
            expectedReads.add(f.westX + ":" + (f.y - 1) + ":" + f.z);
        }
        assertEquals(expectedReads, f.reads, "side is read before below; blocked sides omit below");
        assertEquals(status, f.visited.peek(f.westHash));
        assertEquals(2, f.visited.defaultReturnValue(), "the absent sentinel does not use the map default");
        assertEquals(1, f.visited.putCalls);
        assertEquals(status == BlockLiquid.CAN_FLOW_DOWN ? 2 : 3, f.visited.lookupCalls());
    }

    static Stream<Arguments> sentinelCases() {
        return Stream.of(false, true).flatMap(lava -> Stream.of(false, true)
                .map(zeroHash -> Arguments.of(lava, zeroHash)));
    }

    @ParameterizedTest
    @MethodSource("sentinelCases")
    void externallyStoredSentinelKeepsItsOriginalFlowableMeaning(boolean lava, boolean zeroHash)
            throws Exception {
        Fixture f = new Fixture(lava, zeroHash, BlockLiquid.BLOCKED);
        f.visited.put(f.westHash, (byte) 2);
        f.visited.resetCounts();

        assertEquals(1000, f.calculate());
        assertEquals(List.of(), f.reads, "a present extension value must not be treated as absent");
        assertEquals((byte) 2, f.visited.peek(f.westHash));
        assertEquals(0, f.visited.putCalls);
    }

    private static final class Fixture {
        final CountingMap visited = new CountingMap();
        final List<String> reads = new ArrayList<>();
        final BlockLiquid liquid;
        final int x;
        final int y;
        final int z;
        final int westX;
        final long westHash;

        Fixture(boolean lava, boolean zeroHash, byte status) {
            x = zeroHash ? 1 : -17;
            y = zeroHash ? DimensionEnum.OVERWORLD.getDimensionData().getMinHeight() : 64;
            z = zeroHash ? 0 : -16;
            westX = x - 1;
            Level level = mock(Level.class);
            when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
            when(level.getBlock(anyInt(), anyInt(), anyInt())).thenAnswer(invocation -> {
                int bx = invocation.getArgument(0);
                int by = invocation.getArgument(1);
                int bz = invocation.getArgument(2);
                reads.add(bx + ":" + by + ":" + bz);
                Block block = by == y && status == BlockLiquid.BLOCKED
                        || by == y - 1 && status == BlockLiquid.CAN_FLOW
                        ? new BlockStone() : new BlockAir();
                block.level = level;
                block.x = bx;
                block.y = by;
                block.z = bz;
                return block;
            });
            when(level.getBlock(any(Vector3.class), anyInt())).thenAnswer(invocation -> {
                Vector3 position = invocation.getArgument(0);
                reads.add(position.getFloorX() + ":" + position.getFloorY() + ":"
                        + position.getFloorZ() + ":" + invocation.getArgument(1));
                return new BlockAir();
            });
            liquid = lava ? new BlockLava() : new BlockWater();
            liquid.level = level;
            liquid.flowCostVisited = visited;
            visited.defaultReturnValue((byte) 2);
            westHash = Level.blockHash(westX, y, z, level.getDimensionData());
            if (zeroHash) {
                assertEquals(0L, westHash, "exercise fastutil's special zero-key lane");
            }
            visited.put(Level.blockHash(x, y, z + 1, level.getDimensionData()), BlockLiquid.BLOCKED);
        }

        int calculate() throws Exception {
            // East and north are excluded; west and south retain their original order.
            // At max cost there is no recursive search beyond these two cells.
            return (int) calculateFlowCost.invoke(liquid, x, y, z, 1, 1, 1, 2);
        }
    }

    private static final class CountingMap extends Long2ByteOpenHashMap {
        int containsCalls;
        int getCalls;
        int getOrDefaultCalls;
        int putCalls;

        @Override
        public boolean containsKey(long key) {
            containsCalls++;
            return super.containsKey(key);
        }

        @Override
        public byte get(long key) {
            getCalls++;
            return super.get(key);
        }

        @Override
        public byte getOrDefault(long key, byte fallback) {
            getOrDefaultCalls++;
            return super.getOrDefault(key, fallback);
        }

        @Override
        public byte put(long key, byte value) {
            putCalls++;
            return super.put(key, value);
        }

        byte peek(long key) {
            return super.get(key);
        }

        int lookupCalls() {
            return containsCalls + getCalls + getOrDefaultCalls;
        }

        void resetCounts() {
            containsCalls = getCalls = getOrDefaultCalls = putCalls = 0;
        }
    }
}
