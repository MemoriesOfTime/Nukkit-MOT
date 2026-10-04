package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DBException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Regression from round-3 review: synchronous corruption mounts unsavable quarantine. */
class ReviewStrictSyncReadProbeTest {
    private static final int X = 9, Z = -4;
    @TempDir Path directory;
    private LevelDBProvider provider;
    private boolean previousAsync, previousCompletion;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        Server server = Server.getInstance();
        previousAsync = server.asyncChunkSending;
        previousCompletion = server.asyncChunkLoadCompletion;
        server.asyncChunkSending = true;
        server.levelDbCache = 8;
        server.useNativeLevelDB = false;
        server.maxPendingChunkWrites = 128;
        LevelDBProvider.generate(directory.toString(), "strict-sync-probe", 404L, Flat.class);
        Level level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        provider = new LevelDBProvider(level, directory.toString());
        provider.getDatabase().put(LevelDBKey.VERSION.getKey(X, Z, 0), new byte[]{1, 2}); // malformed header
    }

    @AfterEach
    void tearDown() {
        if (provider != null) provider.close();
        Server.getInstance().asyncChunkSending = previousAsync;
        Server.getInstance().asyncChunkLoadCompletion = previousCompletion;
    }

    @Test
    void strictSyncCreateMountsQuarantineWithoutThrowing() {
        Server.getInstance().asyncChunkLoadCompletion = true;
        BaseFullChunk chunk = assertDoesNotThrow(() -> provider.getChunk(X, Z, true));
        assertNotNull(chunk);
        assertNotNull(chunk.getChunkLoadFailure());
        assertFalse(chunk.isGenerated());
        assertSame(chunk, provider.getLoadedChunk(Level.chunkHash(X, Z)));
        assertSame(chunk, provider.getChunk(X, Z, true));
        provider.saveChunk(X, Z, chunk);
        assertArrayEquals(new byte[]{1, 2}, provider.getDatabase().get(LevelDBKey.VERSION.getKey(X, Z, 0)));
    }

    @Test
    void quarantineCannotBeSavedEvenIfCallerMarksItGenerated() {
        Server.getInstance().asyncChunkLoadCompletion = true;
        BaseFullChunk chunk = provider.getChunk(X, Z, true);
        chunk.setGenerated(true);
        chunk.setChanged();
        assertThrows(java.util.concurrent.CompletionException.class, () -> provider.saveChunkFuture(X, Z, chunk).join());
        assertThrows(DBException.class, () -> provider.saveChunkSync(X, Z, chunk));
        provider.saveChunks();
        assertEquals(0, provider.getPendingWriteCount());
        assertArrayEquals(new byte[]{1, 2}, provider.getDatabase().get(LevelDBKey.VERSION.getKey(X, Z, 0)));
        assertTrue(provider.unloadChunk(X, Z, false));
        assertEquals(0, provider.getPendingWriteCount());
        assertArrayEquals(new byte[]{1, 2}, provider.getDatabase().get(LevelDBKey.VERSION.getKey(X, Z, 0)));
    }

    @Test
    void nonCreatingReadQuarantinesAndUnloadAllowsRecovery() {
        Server.getInstance().asyncChunkLoadCompletion = true;
        BaseFullChunk failed = provider.getChunk(X, Z, false);
        assertNotNull(failed.getChunkLoadFailure());
        assertTrue(provider.unloadChunk(X, Z, false));
        provider.getDatabase().delete(LevelDBKey.VERSION.getKey(X, Z, 0));
        BaseFullChunk recovered = provider.getChunk(X, Z, true);
        assertNotSame(failed, recovered);
        assertNull(recovered.getChunkLoadFailure());
        assertFalse(recovered.isGenerated());
    }

    @Test
    void pendingCommitFailureAlsoQuarantinesInsteadOfEscapingTheTick() throws Exception {
        Server.getInstance().asyncChunkLoadCompletion = true;
        var real = provider.getDatabase();
        var failed = mock(org.iq80.leveldb.DB.class, org.mockito.AdditionalAnswers.delegatesTo(real));
        doThrow(new DBException("injected write failure")).when(failed).write(any(org.iq80.leveldb.WriteBatch.class));
        var dbField = LevelDBProvider.class.getDeclaredField("db");
        dbField.setAccessible(true);
        dbField.set(provider, failed);
        try {
            var pending = provider.getEmptyChunk(X, Z);
            pending.setGenerated(true);
            assertThrows(java.util.concurrent.CompletionException.class,
                    () -> provider.saveChunkFuture(X, Z, pending).join());
            BaseFullChunk quarantined = assertDoesNotThrow(() -> provider.getChunk(X, Z, true));
            assertNotNull(quarantined.getChunkLoadFailure());
            assertTrue(quarantined.getChunkLoadFailure().getMessage().contains("Failed to commit chunk"));
            assertFalse(quarantined.isGenerated());
        } finally {
            dbField.set(provider, real);
        }
    }

    @Test
    void legacySyncCreateReturnedAnUngeneratedChunk() {
        Server.getInstance().asyncChunkLoadCompletion = false;
        BaseFullChunk chunk = provider.getChunk(X, Z, true);
        assertNotNull(chunk);
        assertFalse(chunk.isGenerated());
    }
}
