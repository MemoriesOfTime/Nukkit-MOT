package cn.nukkit.level.format.leveldb;

import cn.nukkit.level.Level;
import cn.nukkit.level.format.generic.BaseFullChunk;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMaps;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import jdk.jfr.Recording;
import jdk.jfr.consumer.RecordingFile;
import java.nio.file.Path;

import java.lang.reflect.Field;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoadedChunkSnapshotTest {
    @Test
    void collidingCoordinateKeysRemainDetachedAndReadOnly(@TempDir Path directory) throws Exception {
        LevelDBProvider provider = mock(LevelDBProvider.class, CALLS_REAL_METHODS);
        Long2ObjectMap<BaseFullChunk> live = Long2ObjectMaps.synchronize(new Long2ObjectOpenHashMap<>());
        Field field = LevelDBProvider.class.getDeclaredField("chunks");
        field.setAccessible(true);
        field.set(provider, live);
        BaseFullChunk chunk = mock(BaseFullChunk.class);
        for (int i = -128; i < 128; i++) live.put(Level.chunkHash(i, i), chunk);
        Map<Long, BaseFullChunk> snapshot;
        Path recordingPath = directory.resolve("snapshot.jfr");
        try (Recording recording = new Recording()) {
            recording.enable("jdk.JavaExceptionThrow").withStackTrace();
            recording.start();
            snapshot = provider.getLoadedChunks();
            recording.stop();
            recording.dump(recordingPath);
        }
        assertTrue(RecordingFile.readAllEvents(recordingPath).stream()
                .filter(event -> event.getEventType().getName().equals("jdk.JavaExceptionThrow"))
                .noneMatch(event -> event.getClass("thrownClass").getName()
                        .equals("com.google.common.collect.RegularImmutableMap$BucketOverflowException")),
                "diagonal chunk coordinates must not cause a Guava overflow-and-rebuild exception");
        assertEquals(256, snapshot.size());
        for (int i = -128; i < 128; i++) assertSame(chunk, snapshot.get(Level.chunkHash(i, i)));
        live.clear();
        assertEquals(256, snapshot.size(), "snapshot must not become PNX's live view");
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
        assertThrows(UnsupportedOperationException.class, () -> snapshot.put(1L, chunk));
        assertThrows(UnsupportedOperationException.class, () -> snapshot.entrySet().iterator().next().setValue(chunk));
        live.put(1L, (BaseFullChunk) null);
        assertThrows(NullPointerException.class, provider::getLoadedChunks,
                "preserve the former ImmutableMap null rejection contract");
    }
}
