package cn.nukkit;

import cn.nukkit.event.server.PlayerDataSerializeEvent;
import cn.nukkit.nbt.NBTIO;
import cn.nukkit.nbt.tag.CompoundTag;
import cn.nukkit.plugin.Plugin;
import cn.nukkit.plugin.PluginManager;
import cn.nukkit.scheduler.*;
import cn.nukkit.utils.PlayerDataSerializer;
import java.io.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Timeout(20)
class PlayerQueuedProfileSaveTest {
    @Test void synchronousCheckpointFinishesOlderQueuedSaveBeforeItsOwnWrite() throws Exception {
        var f = new Fixture();
        f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 1), true);
        f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 2), false);
        assertEquals(List.of(1, 2), f.writes);
        f.scheduled.get(0).onRun(1);
        assertEquals(List.of(1, 2), f.writes);
        assertTrue(f.pending.isEmpty());
        assertEquals(2, f.read().getInt("value"));
    }
    @Test void asyncSnapshotIncludesSerializeListenerChangesButDetachesBeforeReturning() throws Exception {
        var f = new Fixture();
        var live = new CompoundTag().putCompound("nested", new CompoundTag().putInt("value", 1));
        doAnswer(call -> { live.getCompound("nested").putInt("value", 7); return null; })
                .when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        f.server.saveOfflinePlayerData("player", live, true);
        live.getCompound("nested").putInt("value", 99);
        f.scheduled.get(0).onRun(1);
        assertEquals(7, f.read().getCompound("nested").getInt("value"));
        assertTrue(f.pending.isEmpty());
    }
    @Test void throwingSerializeListenerQueuesNothingAndHealthyRetryRunsOnlyOnce() throws Exception {
        var f = new Fixture(); var failure = new AssertionError("serialization listener");
        doThrow(failure).when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        assertSame(failure, assertThrows(AssertionError.class, () ->
                f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 1), true)));
        assertTrue(f.pending.isEmpty()); assertTrue(f.scheduled.isEmpty()); assertTrue(f.writes.isEmpty());
        doNothing().when(f.plugins).callEvent(any(PlayerDataSerializeEvent.class));
        f.server.saveOfflinePlayerData("player", new CompoundTag().putInt("value", 2), true);
        f.scheduled.get(0).onRun(1); f.scheduled.get(0).onCancel();
        assertEquals(List.of(2), f.writes); assertTrue(f.pending.isEmpty());
    }

    static final class Fixture {
        final Server server = mock(Server.class, CALLS_REAL_METHODS);
        final PluginManager plugins = mock(PluginManager.class);
        final ConcurrentHashMap<String, ArrayDeque<Task>> pending = new ConcurrentHashMap<>();
        final List<Task> scheduled = new ArrayList<>();
        final List<Integer> writes = new ArrayList<>();
        byte[] bytes;
        Fixture() throws Exception {
            inject("playerDataLocks", Server.createPlayerDataLocks());
            inject("pendingPlayerDataSaves", pending); inject("pluginManager", plugins);
            inject("playerDataSerializer", new PlayerDataSerializer() {
                public Optional<InputStream> read(String name, UUID id) { return Optional.ofNullable(bytes).map(ByteArrayInputStream::new); }
                public OutputStream write(String name, UUID id) {
                    return new ByteArrayOutputStream() {
                        boolean closed;
                        public void close() throws IOException {
                            if (closed) return; closed = true;
                            super.close(); bytes = toByteArray(); writes.add(Fixture.this.read().getInt("value"));
                        }
                    };
                }
            });
            doReturn(true).when(server).shouldSavePlayerData(); server.savePlayerDataByUuid = false;
            var scheduler = mock(ServerScheduler.class); doReturn(scheduler).when(server).getScheduler();
            when(scheduler.scheduleTask(any(Plugin.class), any(Runnable.class), eq(true))).thenAnswer(call -> {
                Task task = call.getArgument(1); var handler = new TaskHandler(call.getArgument(0), task, scheduled.size()+1, true);
                task.setHandler(handler); scheduled.add(task); return handler;
            });
        }
        CompoundTag read() throws IOException { return NBTIO.readCompressed(new ByteArrayInputStream(bytes)); }
        void inject(String name, Object value) throws Exception {
            Field f = Server.class.getDeclaredField(name); f.setAccessible(true); f.set(server, value);
        }
    }
}
