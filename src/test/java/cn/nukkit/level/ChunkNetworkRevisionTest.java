package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.level.format.ChunkSection;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseChunk;
import cn.nukkit.level.format.generic.serializer.ChunkRequestSnapshot;
import cn.nukkit.level.format.leveldb.LevelDBProvider;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.lang.reflect.Field;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@org.junit.jupiter.api.parallel.Isolated("MockServer")
@org.junit.jupiter.api.Timeout(30)
class ChunkNetworkRevisionTest {
    @BeforeAll static void initialize() { MockServer.init(); }

    @ParameterizedTest
    @CsvSource({"leveldb,setBlock", "leveldb,setFullBlockId", "leveldb,getAndSetBlock",
            "anvil,setBlock", "anvil,setFullBlockId", "anvil,getAndSetBlock"})
    void completedWriteMustInvalidateAnOldCloneCapturedDuringLockWait(String type, String mutation) throws Exception {
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(new DimensionData(0, 0, 15));
        LevelProvider provider = type.equals("leveldb") ? mock(LevelDBProvider.class)
                : mock(cn.nukkit.level.format.anvil.Anvil.class);
        when(provider.getLevel()).thenReturn(level);
        when(provider.getMinBlockY()).thenReturn(0);
        when(provider.getMaxBlockY()).thenReturn(15);
        BaseChunk original = type.equals("leveldb") ? LevelDBChunk.getEmptyChunk(3, 5, provider)
                : cn.nukkit.level.format.anvil.Chunk.getEmptyChunk(3, 5, provider);
        original.setBlock(1, 1, 1, BlockID.STONE);
        ChunkSection section = original.getSection(0);
        Lock readLock;
        if (type.equals("leveldb")) {
            Field field = section.getClass().getDeclaredField("readLock");
            field.setAccessible(true);
            readLock = (Lock) field.get(section);
        } else {
            Field field = section.getClass().getDeclaredField("sectionLock");
            field.setAccessible(true);
            readLock = ((ReadWriteLock) field.get(section)).readLock();
        }
        var executor = Executors.newSingleThreadExecutor();
        java.util.concurrent.Future<?> writer = null;
        ChunkRequestSnapshot token;
        BaseChunk snapshot;
        long revision = original.getNetworkRevision();
        try {
            readLock.lock();
            try {
                writer = executor.submit(() -> {
                    switch (mutation) {
                        case "setBlock" -> original.setBlock(1, 1, 1, BlockID.DIAMOND_BLOCK);
                        case "setFullBlockId" -> original.setFullBlockId(1, 1, 1, BlockID.DIAMOND_BLOCK << Block.DATA_BITS);
                        case "getAndSetBlock" -> original.getAndSetBlock(1, 1, 1, Block.get(BlockID.DIAMOND_BLOCK));
                        default -> throw new AssertionError(mutation);
                    }
                });
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (original.getNetworkRevision() == revision && System.nanoTime() < deadline) Thread.onSpinWait();
                assertTrue(original.getNetworkRevision() > revision, "Writer entered real pre-write invalidation");
                token = new ChunkRequestSnapshot(original);
                snapshot = original.cloneForChunkSending();
                assertEquals(BlockID.STONE, snapshot.getBlockId(1, 1, 1));
            } finally {
                readLock.unlock();
            }
            writer.get(5, TimeUnit.SECONDS);
            assertEquals(BlockID.DIAMOND_BLOCK, original.getBlockId(1, 1, 1));
            assertFalse(token.matches(original), "Completed write left older STONE snapshot cacheable: " + type + " / " + mutation);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        }
    }
    @ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"id", "data"})
    void finalWriteAfterEmptySectionMaterializationInvalidatesItsReceipt(String mutation) {
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(new DimensionData(0, 0, 15));
        LevelProvider provider = mock(cn.nukkit.level.format.anvil.Anvil.class);
        when(provider.getLevel()).thenReturn(level);
        CapturingChunk chunk = new CapturingChunk(provider);
        if (mutation.equals("id")) chunk.setBlockId(1, 1, 1, BlockID.STONE);
        else chunk.setBlockData(1, 1, 1, 3);
        assertNotNull(chunk.materialized, "The native EmptyChunkSection catch path was exercised");
        assertFalse(chunk.materialized.matches(chunk), "The final section write must retire an empty snapshot");
        if (mutation.equals("id")) assertEquals(BlockID.STONE, chunk.getBlockId(1, 1, 1));
        else assertEquals(3, chunk.getBlockData(1, 1, 1));
    }

    private static final class CapturingChunk extends cn.nukkit.level.format.anvil.Chunk {
        ChunkRequestSnapshot materialized;

        CapturingChunk(LevelProvider provider) { super(provider); }

        @Override
        protected void setInternalSection(float y, ChunkSection section) {
            super.setInternalSection(y, section);
            this.materialized = new ChunkRequestSnapshot(this);
        }
    }

}
