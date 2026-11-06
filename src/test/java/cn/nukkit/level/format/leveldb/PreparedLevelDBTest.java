package cn.nukkit.level.format.leveldb;

import org.iq80.leveldb.DB;
import cn.nukkit.MockServer;
import cn.nukkit.level.Level;
import cn.nukkit.nbt.tag.CompoundTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PreparedLevelDBTest {
    @TempDir Path directory;

    @Test void nativeOpenIsWorkerOnlyAndActivationConsumesOneHandle() throws Exception {
        DB db = Mockito.mock(DB.class);
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db, new CompoundTag()));
        assertThrows(IllegalStateException.class, token::open);
        assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(directory.toString()));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(directory.toString()));
        LevelDBProvider provider = mock(LevelDBProvider.class);
        assertTrue(token.activate(() -> {
            try { assertSame(token, PreparedLevelDB.forConstructor(directory.toString())); }
            catch (IOException e) { throw new RuntimeException(e); }
            assertSame(db, token.borrow());
            token.constructed(provider);
            return true;
        }));
        token.commit();
        assertNull(PreparedLevelDB.forConstructor(directory.toString()));
        token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
        verify(db, never()).close();
    }

    @Test void cancellationDuringSlowOpenClosesBeforePathCanBeReleased() throws Exception {
        DB db = mock(DB.class);
        CountDownLatch entered = new CountDownLatch(1), finishOpen = new CountDownLatch(1);
        CountDownLatch closing = new CountDownLatch(1), finishClose = new CountDownLatch(1);
        doAnswer(invocation -> { closing.countDown(); assertTrue(finishClose.await(5, TimeUnit.SECONDS)); return null; }).when(db).close();
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> {
            entered.countDown();
            try { assertTrue(finishOpen.await(5, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new IOException(e); }
            return new PreparedLevelDB.OpenedDatabase(db, new CompoundTag());
        });
        CompletableFuture<Void> opening = CompletableFuture.runAsync(() -> open(token));
        assertTrue(entered.await(5, TimeUnit.SECONDS));
        var cancelled = token.cancel().toCompletableFuture();
        assertFalse(cancelled.isDone());
        finishOpen.countDown();
        assertTrue(closing.await(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, token::release);
        assertThrows(IllegalStateException.class, () -> PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db, new CompoundTag())));
        finishClose.countDown();
        cancelled.get(5, TimeUnit.SECONDS);
        opening.get(5, TimeUnit.SECONDS);
        token.release();
        assertNull(PreparedLevelDB.forConstructor(directory.toString()));
        verify(db, times(1)).close();
    }

    @Test void failedNativeCloseNeverUnlocksDirectory() throws Exception {
        DB db = mock(DB.class);
        doThrow(new IOException("close failed")).when(db).close();
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db, new CompoundTag()));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, token::release);
        assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(directory.toString()));
    }

    @Test void constructorFailureBeforeAdoptionClosesThePreopenedHandle() throws Exception {
        DB db = mock(DB.class);
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db, new CompoundTag()));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> token.activate(() -> { throw new IllegalStateException("init failed"); }));
        token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
        token.release();
        verify(db).close();
    }

    @Test void providerLifecycleStaysOnMainAndReleaseWaitsForProviderNativeClose() throws Exception {
        DB db = mock(DB.class);
        LevelDBProvider provider = mock(LevelDBProvider.class);
        CompletableFuture<Void> nativeClosed = new CompletableFuture<>();
        when(provider.databaseClosed()).thenReturn(nativeClosed);
        Thread main = Thread.currentThread();
        doAnswer(invocation -> { assertSame(main, Thread.currentThread()); return null; }).when(provider).close();
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db, new CompoundTag()));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        token.activate(() -> { token.borrow(); token.constructed(provider); return null; });
        var cancelled = token.cancel().toCompletableFuture();
        verify(provider).close();
        assertFalse(cancelled.isDone());
        nativeClosed.complete(null);
        cancelled.get(5, TimeUnit.SECONDS);
        token.release();
        verify(db, never()).close();
    }

    @Test void canonicalAliasesShareTheSameReservation() throws Exception {
        Path alias = directory.resolveSibling(directory.getFileName() + "-alias");
        Files.createSymbolicLink(alias, directory);
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(mock(DB.class), new CompoundTag()));
        try {
            assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(alias.toString()));
            assertThrows(IllegalStateException.class, () -> PreparedLevelDB.reserve(alias, path -> new PreparedLevelDB.OpenedDatabase(mock(DB.class), new CompoundTag())));
        } finally {
            token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
            token.release();
            Files.delete(alias);
        }
    }

    @Test void nativeOpenFailureKeepsActivationUnavailableUntilCancellation() throws Exception {
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> {
            throw new IOException("open failed");
        });
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, () -> token.activate(() -> null));
        assertThrows(IllegalStateException.class, token::release);
        token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
        token.release();
        assertNull(PreparedLevelDB.forConstructor(directory.toString()));
    }

    @Test void cancellationWaitsForActualProviderCloseAndCallerCannotForgeAcknowledgement() throws Exception {
        MockServer.init();
        DB db = mock(DB.class);
        CountDownLatch closing = new CountDownLatch(1), finishClose = new CountDownLatch(1);
        doAnswer(invocation -> {
            closing.countDown();
            assertTrue(finishClose.await(5, TimeUnit.SECONDS));
            return null;
        }).when(db).close();
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db,
                new CompoundTag().putString("LevelName", "prepared-close")
                        .putInt("StorageVersion", LevelDBConstants.CURRENT_STORAGE_VERSION)));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        LevelDBProvider provider = token.activate(() -> new LevelDBProvider(mock(Level.class), directory.toString()));
        try {
            var cancellation = token.cancel().toCompletableFuture();
            assertTrue(closing.await(5, TimeUnit.SECONDS));
            assertFalse(cancellation.isDone());
            cancellation.complete(null);
            provider.databaseClosed().toCompletableFuture().complete(null);
            assertFalse(token.cancel().toCompletableFuture().isDone());
            assertFalse(provider.databaseClosed().toCompletableFuture().isDone());
            assertThrows(IllegalStateException.class, token::release);
            assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(directory.toString()));
        } finally {
            finishClose.countDown();
            token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS);
            token.release();
        }
        verify(db, times(1)).close();
    }

    @Test void failedActualProviderCloseNeverAcknowledgesOrReleasesThePath() throws Exception {
        MockServer.init();
        DB db = mock(DB.class);
        doThrow(new IOException("close failed")).when(db).close();
        PreparedLevelDB token = PreparedLevelDB.reserve(directory, path -> new PreparedLevelDB.OpenedDatabase(db,
                new CompoundTag().putString("LevelName", "failed-close")
                        .putInt("StorageVersion", LevelDBConstants.CURRENT_STORAGE_VERSION)));
        CompletableFuture.runAsync(() -> open(token)).get(5, TimeUnit.SECONDS);
        LevelDBProvider provider = token.activate(() -> new LevelDBProvider(mock(Level.class), directory.toString()));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> token.cancel().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertThrows(java.util.concurrent.ExecutionException.class,
                () -> provider.databaseClosed().toCompletableFuture().get(5, TimeUnit.SECONDS));
        assertThrows(IllegalStateException.class, token::release);
        assertThrows(IllegalStateException.class, () -> PreparedLevelDB.forConstructor(directory.toString()));
        verify(db, times(1)).close();
    }

    private static void open(PreparedLevelDB token) {
        try { token.open(); } catch (IOException e) { throw new RuntimeException(e); }
    }
}
