package cn.nukkit.block;

import cn.nukkit.MockServer;
import cn.nukkit.math.SimpleAxisAlignedBB;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

@Isolated("Temporarily replaces the wire prototype slot and restores every cache reference")
class BlockPrototypeFastCacheTest {
    @BeforeAll
    static void init() {
        MockServer.init();
    }

    @Test
    void warmStandardStateReadFinishesWhileAnotherThreadHoldsItsMonitor() throws Exception {
        var original = new BlockRedstoneWirePrototypeTest.Snapshot();
        try (var scope = new BlockRedstoneWirePrototypeTest.RegistryScope(original)) {
            scope.constructors[Block.REDSTONE_WIRE] = PublishedPrototype.class.getConstructor(int.class);
            Block warm = Block.get(Block.REDSTONE_WIRE, 7);
            assertInstanceOf(PublishedPrototype.class, warm);
            Block returned = readWhileHoldingMonitor(scope, 7);
            assertInstanceOf(PublishedPrototype.class, returned);
            assertNotSame(warm, returned);
            assertEquals(7, returned.getDamage());
        }
    }

    @Test
    void rejectedStandardStateIsConstructedOnceAndCachedWithoutMonitor() throws Exception {
        var original = new BlockRedstoneWirePrototypeTest.Snapshot();
        RejectedPrototype.constructors.set(0);
        try (var scope = new BlockRedstoneWirePrototypeTest.RegistryScope(original)) {
            scope.constructors[Block.REDSTONE_WIRE] = RejectedPrototype.class.getConstructor(int.class);
            Block first = Block.get(Block.REDSTONE_WIRE, 7);
            assertInstanceOf(BlockUnknown.class, first);
            Block second = readWhileHoldingMonitor(scope, 7);
            assertInstanceOf(BlockUnknown.class, second);
            assertNotSame(first, second);
            assertEquals(Block.REDSTONE_WIRE, second.getId());
            assertEquals(7, second.getDamage());
            assertEquals(1, RejectedPrototype.constructors.get());
            assertFalse(scope.cache.containsKey(7), "Unsupported sentinels must not become public prototypes");
        }
    }

    @Test
    void concurrentColdStateHasOneConstructorAndPublishesInitializedDetachedCopies() throws Exception {
        var original = new BlockRedstoneWirePrototypeTest.Snapshot();
        PublishedPrototype.constructors.set(0);
        try (var scope = new BlockRedstoneWirePrototypeTest.RegistryScope(original)) {
            scope.constructors[Block.REDSTONE_WIRE] = PublishedPrototype.class.getConstructor(int.class);
            ExecutorService executor = Executors.newFixedThreadPool(8);
            CountDownLatch ready = new CountDownLatch(8);
            CountDownLatch start = new CountDownLatch(1);
            List<Future<Block>> futures = new ArrayList<>();
            try {
                for (int i = 0; i < 8; i++) {
                    futures.add(executor.submit(() -> {
                        ready.countDown();
                        assertTrue(start.await(5, TimeUnit.SECONDS));
                        Block previous = null;
                        for (int repeat = 0; repeat < 2_000; repeat++) {
                            PublishedPrototype result = assertInstanceOf(PublishedPrototype.class,
                                    Block.get(Block.REDSTONE_WIRE, 7));
                            assertEquals(0x12345678, result.initialized);
                            assertArrayEquals(new int[]{7, 11, 13}, result.initializedArray);
                            assertEquals(7, result.getDamage());
                            assertEquals(0, result.x);
                            assertEquals(0, result.y);
                            assertEquals(0, result.z);
                            assertEquals(0, result.layer);
                            assertNull(result.getLevel());
                            assertNull(result.boundingBox);
                            assertNotSame(previous, result);
                            previous = result;
                        }
                        return previous;
                    }));
                }
                assertTrue(ready.await(5, TimeUnit.SECONDS));
                start.countDown();
                List<Block> results = new ArrayList<>();
                for (Future<Block> future : futures) {
                    Block result = future.get(10, TimeUnit.SECONDS);
                    for (Block previous : results) {
                        assertNotSame(previous, result);
                    }
                    results.add(result);
                }
                assertEquals(1, PublishedPrototype.constructors.get());
                assertEquals(1, scope.cache.size());
            } finally {
                start.countDown();
                executor.shutdownNow();
                assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
            }
        }
    }

    @Test
    void negativeAndOversizedMetadataKeepDefaultCloneFallbackAndDoNotAllocateFastRow() throws Exception {
        var original = new BlockRedstoneWirePrototypeTest.Snapshot();
        try (var scope = new BlockRedstoneWirePrototypeTest.RegistryScope(original)) {
            for (int meta : new int[]{-1, Block.DATA_SIZE, Integer.MAX_VALUE}) {
                Block block = Block.get(Block.REDSTONE_WIRE, meta);
                assertInstanceOf(BlockRedstoneWire.class, block);
                assertEquals(meta, block.getDamage());
            }
            assertNull(original.fastCaches[Block.REDSTONE_WIRE]);
            assertTrue(scope.cache.isEmpty());
        }
    }

    private static Block readWhileHoldingMonitor(BlockRedstoneWirePrototypeTest.RegistryScope scope, int meta)
            throws Exception {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            synchronized (scope.original.locks[Block.REDSTONE_WIRE]) {
                Future<Block> future = executor.submit(() -> Block.get(Block.REDSTONE_WIRE, meta));
                return future.get(5, TimeUnit.SECONDS);
            }
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }

    public static class PublishedPrototype extends BlockRedstoneWire {
        static final AtomicInteger constructors = new AtomicInteger();
        int initialized;
        int[] initializedArray;

        public PublishedPrototype(int meta) {
            super(meta);
            constructors.incrementAndGet();
            x = 17;
            y = 19;
            z = 23;
            layer = 1;
            boundingBox = new SimpleAxisAlignedBB(0, 0, 0, 1, 1, 1);
            initializedArray = new int[]{7, 11, 13};
            initialized = 0x12345678;
        }
    }

    public static class RejectedPrototype extends BlockRedstoneWire {
        static final AtomicInteger constructors = new AtomicInteger();

        public RejectedPrototype(int meta) {
            super(meta + 1);
            constructors.incrementAndGet();
        }
    }
}
