package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.utils.ChunkException;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordedEvent;
import jdk.jfr.consumer.RecordingFile;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BaseChunkLightWriteAdmissionTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
        LevelDBChunk warm = new LevelDBChunk(null, 0, 0);
        warm.setBlockLight(1, 1, 1, 8);
        warm.setBlockSkyLight(1, 1, 1, 7);
    }

    @Test
    void nativeEmptyBlockAndSkyWritesMaterializeWithoutExpectedExceptions() throws Exception {
        CountingChunk block = new CountingChunk();
        CountingChunk sky = new CountingChunk();
        Path file = Files.createTempFile("empty-light-admission", ".jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.JavaExceptionThrow").withStackTrace();
            recording.start();
            block.setBlockLight(1, 1, 1, 11);
            sky.setBlockSkyLight(2, 1, 1, 6);
            recording.stop();
            recording.dump(file);
            List<RecordedEvent> events = RecordingFile.readAllEvents(file);
            long exceptions = events.stream().filter(BaseChunkLightWriteAdmissionTest::isEmptyLightException).count();
            assertEquals(0, exceptions, "ordinary non-default native light writes must not allocate ChunkException");
            assertEquals(11, block.getBlockLight(1, 1, 1));
            assertEquals(6, sky.getBlockSkyLight(2, 1, 1));
            assertEquals(1, block.materializations.get());
            assertEquals(1, sky.materializations.get());
            assertTrue(block.hasChanged());
            assertTrue(sky.hasChanged());
        } finally {
            Files.deleteIfExists(file);
        }
    }

    private static boolean isEmptyLightException(RecordedEvent event) {
        if (!event.getEventType().getName().equals("jdk.JavaExceptionThrow")
                || !event.getClass("thrownClass").getName().equals(ChunkException.class.getName())
                || event.getStackTrace() == null) return false;
        return event.getStackTrace().getFrames().stream().anyMatch(frame ->
                frame.getMethod().getType().getName().equals(EmptyChunkSection.class.getName())
                        && (frame.getMethod().getName().equals("setBlockLight")
                        || frame.getMethod().getName().equals("setBlockSkyLight")));
    }

    @Test
    void defaultWritesKeepSharedEmptySectionAndChangedContract() {
        CountingChunk chunk = new CountingChunk();
        ChunkSection empty = chunk.getSection(0);
        chunk.setChanged(false);
        chunk.setBlockLight(1, 1, 1, 0);
        assertSame(empty, chunk.getSection(0));
        assertTrue(chunk.hasChanged());
        chunk.setChanged(false);
        chunk.setBlockSkyLight(1, 1, 1, 15);
        assertSame(empty, chunk.getSection(0));
        assertTrue(chunk.hasChanged());
        assertEquals(0, chunk.materializations.get());
    }

    @Test
    void existingNativeSectionIsNotReplacedByLaterLightWrites() {
        CountingChunk chunk = new CountingChunk();
        chunk.setBlockLight(1, 1, 1, 7);
        ChunkSection section = chunk.getSection(0);
        chunk.setBlockLight(2, 1, 1, 12);
        chunk.setBlockSkyLight(3, 1, 1, 4);
        assertSame(section, chunk.getSection(0));
        assertEquals(7, chunk.getBlockLight(1, 1, 1));
        assertEquals(12, chunk.getBlockLight(2, 1, 1));
        assertEquals(4, chunk.getBlockSkyLight(3, 1, 1));
        assertEquals(1, chunk.materializations.get());
    }

    @Test
    void customEmptySetterOverrideReceivesNonDefaultWrites() {
        CountingChunk chunk = new CountingChunk();
        AtomicInteger blockCalls = new AtomicInteger();
        AtomicInteger skyCalls = new AtomicInteger();
        EmptyChunkSection custom = new EmptyChunkSection(0) {
            @Override public void setBlockLight(int x, int y, int z, int light) {
                assertEquals(11, light);
                blockCalls.incrementAndGet();
            }
            @Override public void setBlockSkyLight(int x, int y, int z, int light) {
                assertEquals(6, light);
                skyCalls.incrementAndGet();
            }
        };
        chunk.getSections()[0] = custom;
        chunk.setBlockLight(1, 1, 1, 11);
        chunk.setBlockSkyLight(1, 1, 1, 6);
        assertSame(custom, chunk.getSection(0));
        assertEquals(1, blockCalls.get());
        assertEquals(1, skyCalls.get());
        assertEquals(0, chunk.materializations.get());
    }

    @Test
    void customEmptyFailureKeepsOriginalSetterThenGuardedRetry() {
        CountingChunk chunk = new CountingChunk();
        AtomicInteger calls = new AtomicInteger();
        chunk.getSections()[0] = new EmptyChunkSection(0) {
            @Override public void setBlockLight(int x, int y, int z, int light) {
                calls.incrementAndGet();
                throw new ChunkException("custom empty setter");
            }
        };
        chunk.setBlockLight(1, 1, 1, 9);
        assertEquals(1, calls.get(), "an EmptyChunkSection subclass must keep virtual setter dispatch");
        assertEquals(1, chunk.materializations.get());
        assertEquals(9, chunk.getBlockLight(1, 1, 1));
    }

    @Test
    void failedFactoryKeepsEscapingChunkExceptionAndEmptySection() {
        CountingChunk chunk = new CountingChunk() {
            @Override protected ChunkSection materializeSection(int y) {
                materializations.incrementAndGet();
                return null;
            }
        };
        ChunkSection empty = chunk.getSection(0);
        ChunkException failure = assertThrows(ChunkException.class, () -> chunk.setBlockLight(1, 1, 1, 9));
        assertEquals("Tried to modify an empty Chunk", failure.getMessage());
        assertSame(empty, chunk.getSection(0));
        assertEquals(1, chunk.materializations.get());
    }

    @Test
    void initialVirtualGetterFailureKeepsOriginalMaterializeRetry() {
        AtomicInteger reads = new AtomicInteger();
        CountingChunk chunk = new CountingChunk() {
            @Override public ChunkSection getSection(float y) {
                if (reads.incrementAndGet() == 1) throw new ChunkException("first section getter");
                return super.getSection(y);
            }
        };
        chunk.setBlockLight(1, 1, 1, 8);
        assertEquals(3, reads.get(), "initial getter failure, guarded recheck and setter lookup");
        assertEquals(1, chunk.materializations.get());
        assertEquals(8, chunk.getBlockLight(1, 1, 1));
    }

    @Test
    void factoryChunkExceptionEscapesOnceWithoutAnotherFallback() {
        ChunkException sentinel = new ChunkException("factory sentinel");
        CountingChunk chunk = new CountingChunk() {
            @Override protected ChunkSection materializeSection(int y) {
                materializations.incrementAndGet();
                throw sentinel;
            }
        };
        assertSame(sentinel, assertThrows(ChunkException.class, () -> chunk.setBlockLight(1, 1, 1, 8)));
        assertEquals(1, chunk.materializations.get());
    }

    @Test
    void delayedFirstWriterPreservesTheOtherLightWriteAndSaveDirtyState() throws Exception {
        GatedChunk chunk = new GatedChunk();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> {
                Thread.currentThread().setName("first-light-materializer");
                try {
                    chunk.setBlockLight(1, 1, 1, 7);
                    chunk.setChanged(false);
                } finally {
                    chunk.firstFinished.countDown();
                }
            });
            Future<?> second = workers.submit(() -> {
                Thread.currentThread().setName("second-light-materializer");
                chunk.setBlockLight(2, 1, 1, 12);
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(7, chunk.getBlockLight(1, 1, 1));
            assertEquals(12, chunk.getBlockLight(2, 1, 1));
            assertEquals(1, chunk.materializations.get());
            assertTrue(chunk.hasChanged());
        } finally {
            workers.shutdownNow();
            assertTrue(workers.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    private static class CountingChunk extends LevelDBChunk {
        final AtomicInteger materializations = new AtomicInteger();
        CountingChunk() { super(null, 0, 0); }
        @Override protected ChunkSection materializeSection(int y) {
            materializations.incrementAndGet();
            return super.materializeSection(y);
        }
    }

    private static final class GatedChunk extends CountingChunk {
        final CountDownLatch bothCapturedEmpty = new CountDownLatch(2);
        final CountDownLatch firstFinished = new CountDownLatch(1);
        @Override public ChunkSection getSection(float y) {
            ChunkSection captured = super.getSection(y);
            if (captured instanceof EmptyChunkSection) {
                bothCapturedEmpty.countDown();
                await(bothCapturedEmpty);
                if (Thread.currentThread().getName().equals("second-light-materializer")) await(firstFinished);
            }
            return captured;
        }
        private static void await(CountDownLatch latch) {
            try {
                assertTrue(latch.await(10, TimeUnit.SECONDS), "light writer rendezvous timed out");
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError(failure);
            }
        }
    }
}
