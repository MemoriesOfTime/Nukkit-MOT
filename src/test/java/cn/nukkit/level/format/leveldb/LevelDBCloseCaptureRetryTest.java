package cn.nukkit.level.format.leveldb;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.DimensionEnum;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.level.generator.Flat;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBException;
import org.iq80.leveldb.WriteBatch;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.AbstractAppender;
import org.apache.logging.log4j.core.config.Property;
import org.apache.logging.log4j.core.layout.PatternLayout;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.AdditionalAnswers;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** A failed capture has no pending-write slot for the final close sweep to recover. */
@Timeout(30)
class LevelDBCloseCaptureRetryTest {
    private static final int X = 17;
    private static final int Z = 23;
    private static final int OLD_BLOCK = 1;
    private static final int LATEST_BLOCK = 3;

    @TempDir Path directory;
    private Level level;
    private LevelDBProvider provider;
    private boolean oldNative;
    private boolean oldAsync;
    private int oldCache;
    private int oldPendingLimit;

    @BeforeAll
    static void initializeServer() {
        MockServer.init();
    }

    @BeforeEach
    void openProvider() throws Exception {
        Server server = Server.getInstance();
        oldNative = server.useNativeLevelDB;
        oldAsync = server.asyncChunkSending;
        oldCache = server.levelDbCache;
        oldPendingLimit = server.maxPendingChunkWrites;
        server.useNativeLevelDB = false;
        server.asyncChunkSending = true;
        server.levelDbCache = 8;
        server.maxPendingChunkWrites = 128;

        LevelDBProvider.generate(directory.toString(), "close-capture-retry", 404L, Flat.class);
        level = mock(Level.class);
        when(level.getDimensionData()).thenReturn(DimensionEnum.OVERWORLD.getDimensionData());
        when(level.getDimension()).thenReturn(Level.DIMENSION_OVERWORLD);
        when(level.isSaveOnUnloadEnabled()).thenReturn(true);
        provider = new LevelDBProvider(level, directory.toString());
    }

    @AfterEach
    void closeProviderAndRestoreServer() {
        try {
            if (provider != null) provider.close();
        } finally {
            Server server = Server.getInstance();
            server.useNativeLevelDB = oldNative;
            server.asyncChunkSending = oldAsync;
            server.levelDbCache = oldCache;
            server.maxPendingChunkWrites = oldPendingLimit;
        }
    }

    @Test
    void ordinaryClosePersistsLatestDirtyRevision() throws Exception {
        assertLatestRevisionSurvivesClose(false);
    }

    @Test
    void closeRetriesOneCaptureFailureBeforeDiscardingDirtyChunk() throws Exception {
        assertLatestRevisionSurvivesClose(true);
    }

    @Test
    void persistentCaptureFailureIsBoundedReportedAndDoesNotBlockHealthyChunk() throws Exception {
        LevelDBChunk bad = spy(provider.getEmptyChunk(X, Z));
        LevelDBChunk healthy = provider.getEmptyChunk(X + 1, Z);
        for (LevelDBChunk chunk : List.of(bad, healthy)) {
            chunk.setGenerated(true);
            provider.setChunk(chunk.getX(), chunk.getZ(), chunk);
            chunk.setBlock(0, 64, 0, OLD_BLOCK);
            provider.saveChunkSync(chunk.getX(), chunk.getZ(), chunk);
            chunk.setBlock(0, 64, 0, LATEST_BLOCK);
        }
        clearInvocations(bad);
        doThrow(new DBException("persistent snapshot failure")).when(bad).prepareStorageSave();
        Field field = LevelDBProvider.class.getDeclaredField("db");
        field.setAccessible(true);
        DB database = (DB) field.get(provider);
        DB observed = mock(DB.class, AdditionalAnswers.delegatesTo(database));
        List<WriteBatch> batches = new ArrayList<>();
        doAnswer(call -> {
            WriteBatch batch = spy(database.createWriteBatch());
            batches.add(batch);
            return batch;
        }).when(observed).createWriteBatch();
        field.set(provider, observed);
        var logger = (org.apache.logging.log4j.core.Logger) LogManager.getLogger(LevelDBProvider.class);
        var errors = new CopyOnWriteArrayList<String>();
        var appender = new AbstractAppender("close-capture-test", null,
                PatternLayout.createDefaultLayout(), false, Property.EMPTY_ARRAY) {
            @Override public void append(LogEvent event) {
                if (event.getLevel().isMoreSpecificThan(org.apache.logging.log4j.Level.ERROR))
                    errors.add(event.getMessage().getFormattedMessage());
            }
        };
        appender.start();
        logger.addAppender(appender);
        try {
            provider.close();
            provider = null;
        } finally {
            logger.removeAppender(appender);
            appender.stop();
        }
        verify(bad, times(2)).prepareStorageSave();
        assertTrue(bad.hasChanged(), "failed capture must never acknowledge the dirty revision");
        assertEquals(1, batches.size(), "only the healthy capture allocates a batch; preparation fails before allocation");
        verify(observed, times(1)).createWriteBatch();
        for (WriteBatch batch : batches) verify(batch).close();
        assertTrue(errors.stream().anyMatch(s -> s.contains("Unsaved chunk 17, 23 after 2")));
        assertTrue(errors.stream().anyMatch(s -> s.contains("could not capture 1 dirty chunks")));
        provider = new LevelDBProvider(level, directory.toString());
        assertEquals(OLD_BLOCK, provider.readChunk(X, Z).getBlockId(0, 64, 0),
                "persistent failure cannot promise a saved revision; retain the last acknowledged data");
        assertEquals(LATEST_BLOCK, provider.readChunk(X + 1, Z).getBlockId(0, 64, 0));
    }

    private void assertLatestRevisionSurvivesClose(boolean failFirstCapture) throws Exception {
        LevelDBChunk chunk = provider.getEmptyChunk(X, Z);
        chunk.setGenerated(true);
        provider.setChunk(X, Z, chunk);
        chunk.setBlock(0, 64, 0, OLD_BLOCK);
        provider.saveChunkSync(X, Z, chunk);
        assertFalse(chunk.hasChanged(), "the old on-disk revision must be acknowledged first");
        assertEquals(0, provider.getPendingWriteCount(), "no old staged batch may rescue this test");
        chunk.setBlock(0, 64, 0, LATEST_BLOCK);
        assertTrue(chunk.hasChanged());

        Field dbField = LevelDBProvider.class.getDeclaredField("db");
        dbField.setAccessible(true);
        DB realDatabase = (DB) dbField.get(provider);
        DB observed = mock(DB.class, AdditionalAnswers.delegatesTo(realDatabase));
        AtomicInteger captures = new AtomicInteger();
        AtomicInteger injectedFailures = new AtomicInteger();
        doAnswer(call -> {
            int attempt = captures.incrementAndGet();
            if (failFirstCapture && attempt == 1) {
                injectedFailures.incrementAndGet();
                throw new DBException("injected one-shot close snapshot capture failure");
            }
            return realDatabase.createWriteBatch();
        }).when(observed).createWriteBatch();
        dbField.set(provider, observed);

        // Only capture fails: subsequent capture and all physical writes remain healthy.
        provider.close();
        provider = null;
        assertTrue(captures.get() >= 1, "close must attempt to capture the dirty revision");
        assertEquals(failFirstCapture ? 1 : 0, injectedFailures.get());

        provider = new LevelDBProvider(level, directory.toString());
        LevelDBChunk restored = provider.readChunk(X, Z);
        assertNotNull(restored, "the previously saved chunk must remain readable");
        assertEquals(LATEST_BLOCK, restored.getBlockId(0, 64, 0),
                "close must not discard the latest dirty revision after a recoverable capture failure");
        if (failFirstCapture) {
            assertTrue(captures.get() >= 2, "a failure before registration needs another snapshot capture");
        }
    }
}
