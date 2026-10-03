package cn.nukkit.level;

import cn.nukkit.MockServer;
import cn.nukkit.Server;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.generic.BaseFullChunk;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.utils.MainLogger;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Regression from round-3 review: ticketless providers retain legacy asynchronous reads. */
class ReviewTicketlessProviderProbeTest {
    private final Map<Long, BaseFullChunk> loaded = new ConcurrentHashMap<>();
    private final List<Runnable> tasks = new ArrayList<>();
    private Server server;
    private Level level;

    @BeforeEach
    void setUp() throws Exception {
        MockServer.init();
        server = mock(Server.class);
        server.asyncChunkSending = true;
        when(server.getPluginManager()).thenReturn(mock(PluginManager.class));
        when(server.getLogger()).thenReturn(mock(MainLogger.class));
        LevelProvider provider = mock(LevelProvider.class);           // openChunkRead(): default null, like Anvil
        when(provider.isOffThreadChunkReadSupported()).thenReturn(true);
        when(provider.openChunkRead(anyInt(), anyInt())).thenReturn(null);
        when(provider.isChunkLoaded(anyLong())).thenAnswer(c -> loaded.containsKey(c.getArgument(0)));
        ExecutorService executor = new AbstractExecutorService() {
            public void shutdown() { }
            public List<Runnable> shutdownNow() { return List.of(); }
            public boolean isShutdown() { return false; }
            public boolean isTerminated() { return false; }
            public boolean awaitTermination(long t, TimeUnit u) { return true; }
            public void execute(Runnable task) { tasks.add(task); }
        };
        level = mock(Level.class, Mockito.CALLS_REAL_METHODS);
        field("server", server);
        field("provider", provider);
        field("asyncChunkLoadExecutor", executor);
        field("asyncChuckExecutor", executor);
        field("pendingChunkLoads", new ConcurrentHashMap<>());
        field("completedChunkLoads", new ConcurrentLinkedQueue<>());
        field("chunkLoadFailures", Caffeine.newBuilder().maximumSize(1024).build());
    }

    private void field(String name, Object value) throws Exception {
        Field f = Level.class.getDeclaredField(name);
        f.setAccessible(true);
        f.set(level, value);
    }

    @Test
    void withCompletionOnTheLegacyReadIsQueued() {
        server.asyncChunkLoadCompletion = true;
        assertTrue(level.isAsyncChunkLoadEnabled());          // Player.sendNextChunk takes the async branch
        for (int tick = 0; tick < 100; tick++) assertTrue(level.requestChunkLoadAsync(3, 5));
        assertEquals(1, tasks.size(), "ticketless reads are deduplicated and submitted");
    }

    @Test
    void withCompletionOffTheLegacyReadIsQueued() {
        server.asyncChunkLoadCompletion = false;
        assertTrue(level.requestChunkLoadAsync(3, 5));
        assertEquals(1, tasks.size());
    }
}
