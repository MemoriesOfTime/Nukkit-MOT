package cn.nukkit.level.format.generic;

import cn.nukkit.MockServer;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class BaseChunkConcurrentMaterializationTest {
    @BeforeAll static void setup() {
        MockServer.init();
        // Palette initialization may read all runtime mappings; exclude startup
        // from the bounded two-writer rendezvous below.
        LevelDBChunk warmup = new LevelDBChunk(null, 0, 0);
        warmup.setBlockId(1, 1, 1, BlockID.STONE);
        warmup.setBlockId(2, 1, 1, BlockID.DIRT);
    }

    @Test
    void twoFirstWritersKeepBothBlocksAndMaterializeOnlyOnce() throws Exception {
        GatedChunk chunk = new GatedChunk();
        ExecutorService workers = Executors.newFixedThreadPool(2);
        try {
            Future<?> first = workers.submit(() -> {
                Thread.currentThread().setName("first-materializer");
                try {
                    chunk.setBlockId(1, 1, 1, BlockID.STONE);
                    // Model a completed save before the delayed writer resumes.
                    chunk.setChanged(false);
                } finally {
                    chunk.firstWriteFinished.countDown();
                }
            });
            Future<?> second = workers.submit(() -> {
                Thread.currentThread().setName("second-materializer");
                chunk.setBlockId(2, 1, 1, BlockID.DIRT);
            });
            first.get(10, TimeUnit.SECONDS);
            second.get(10, TimeUnit.SECONDS);
            assertEquals(BlockID.STONE, chunk.getBlockId(1, 1, 1), "a delayed first-write fallback must preserve the other writer's block");
            assertEquals(BlockID.DIRT, chunk.getBlockId(2, 1, 1));
            assertEquals(1, chunk.materializations.get(), "publish only one real section");
            assertTrue(chunk.hasChanged(), "the delayed write must dirty a reused section after save");
        } finally {
            workers.shutdownNow();
        }
    }

    private static final class GatedChunk extends LevelDBChunk {
        final CountDownLatch bothReadEmpty = new CountDownLatch(2);
        final CountDownLatch firstWriteFinished = new CountDownLatch(1);
        final AtomicInteger materializations = new AtomicInteger();

        GatedChunk() { super(null, 0, 0); }

        @Override public ChunkSection getSection(float sectionY) {
            ChunkSection captured = super.getSection(sectionY);
            if (captured instanceof EmptyChunkSection) {
                bothReadEmpty.countDown();
                await(bothReadEmpty);
                if (Thread.currentThread().getName().equals("second-materializer")) {
                    await(firstWriteFinished);
                }
            }
            // Both writers have captured EmptyChunkSection before either materializes it.
            // The second writer enters its catch only after the first has committed a block.
            return captured;
        }

        @Override protected ChunkSection materializeSection(int sectionY) {
            materializations.incrementAndGet();
            return super.materializeSection(sectionY);
        }

        private static void await(CountDownLatch latch) {
            try {
                assertTrue(latch.await(10, TimeUnit.SECONDS), "writer rendezvous timed out");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError(e);
            }
        }
    }
}
