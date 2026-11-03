package cn.nukkit.level.format.leveldb.serializer;

import cn.nukkit.MockServer;
import cn.nukkit.entity.Entity;
import cn.nukkit.level.Level;
import cn.nukkit.level.format.LevelProvider;
import cn.nukkit.level.format.leveldb.LevelDBKey;
import cn.nukkit.level.format.leveldb.structure.LevelDBChunk;
import cn.nukkit.nbt.tag.CompoundTag;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.WriteBatch;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class ActorRetentionSnapshotTest {
    @BeforeAll
    static void initialize() {
        MockServer.init();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 16, 64, 256})
    void retainedPrimitiveIdsKeepDigestOrderAndSurviveSourceMutation(int count) {
        long[] ids = new long[count];
        List<LevelDBChunk.PreservedEntityActor> actors = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            ids[i] = switch (i) {
                case 0 -> 0;
                case 1 -> Long.MIN_VALUE;
                case 2 -> Long.MAX_VALUE;
                case 3 -> -1;
                default -> 0x7654321000000000L + i;
            };
            actors.add(actor(ids[i], (byte) i));
            actors.add(actor(ids[i], (byte) 99)); // First preserved record wins.
        }
        LevelDBChunk chunk = chunk(Map.of(), actors);
        List<byte[]> digestWrites = new ArrayList<>();
        List<byte[]> actorValues = new ArrayList<>();
        WriteBatch batch = mock(WriteBatch.class);
        when(batch.put(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            byte[] key = call.getArgument(0), value = call.getArgument(1);
            if (key[0] == 'd') digestWrites.add(value.clone());
            else actorValues.add(value.clone());
            return batch;
        });
        EntitySerializer.Cleanup cleanup = EntityStorageV1_18_30.INSTANCE.snapshotEntities(batch, chunk);
        if (count == 0) {
            verify(batch).delete(digp());
            assertTrue(digestWrites.isEmpty());
        } else {
            assertEquals(1, digestWrites.size());
            assertArrayEquals(digest(ids), digestWrites.get(0));
        }
        assertEquals(count, actorValues.size());
        for (int i = 0; i < count; i++) assertArrayEquals(new byte[]{(byte) i}, actorValues.get(i));

        // Neither replacement collections nor mutable preserved byte[] may change captured IDs.
        for (LevelDBChunk.PreservedEntityActor actor : actors) Arrays.fill(actor.getStorageKey(), (byte) 7);
        actors.clear();
        when(chunk.getPreservedEntityActors()).thenReturn(List.of(actor(123, (byte) 3)));
        clearInvocations(chunk, batch);
        long[] oldIds = Arrays.copyOf(ids, ids.length + 2);
        oldIds[ids.length] = 42;
        oldIds[ids.length + 1] = 43;
        DB database = mock(DB.class);
        when(database.get(any(byte[].class))).thenReturn(digest(oldIds));
        cleanup.apply(database, batch);
        verifyNoInteractions(chunk);
        verify(batch).delete(actorKey(42));
        verify(batch).delete(actorKey(43));
        for (long id : ids) verify(batch, never()).delete(actorKey(id));
        verifyNoMoreInteractions(batch);
    }

    @Test
    void duplicateLiveIdsStillWriteDuplicateDigestEntriesAndOverridePreserved() {
        Entity first = entity(777);
        Entity second = entity(777);
        Entity unsavable = entity(888);
        when(unsavable.canBeSavedWithChunk()).thenReturn(false);
        Map<Long, Entity> live = new LinkedHashMap<>();
        live.put(1L, first);
        live.put(2L, second);
        live.put(3L, unsavable);
        byte[] key = EntityStorageV1_18_30.generateStorageKeyForEntity(777);
        LevelDBChunk chunk = chunk(live, List.of(new LevelDBChunk.PreservedEntityActor(key, new byte[]{99}), actor(44, (byte) 4)));
        List<byte[]> writtenKeys = new ArrayList<>();
        List<byte[]> digestWrites = new ArrayList<>();
        WriteBatch batch = mock(WriteBatch.class);
        when(batch.put(any(byte[].class), any(byte[].class))).thenAnswer(call -> {
            byte[] writeKey = call.getArgument(0);
            if (writeKey[0] == 'd') digestWrites.add(call.<byte[]>getArgument(1).clone());
            else writtenKeys.add(writeKey.clone());
            return batch;
        });
        EntitySerializer.Cleanup cleanup = EntityStorageV1_18_30.INSTANCE.snapshotEntities(batch, chunk);
        assertArrayEquals(ByteBuffer.allocate(24).put(key).put(key).putLong(44).array(), digestWrites.get(0));
        assertEquals(3, writtenKeys.size());
        assertArrayEquals(LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, key), writtenKeys.get(0));
        assertArrayEquals(writtenKeys.get(0), writtenKeys.get(1));
        verify(first).saveNBT();
        verify(second).saveNBT();
        verify(unsavable, never()).saveNBT();
        live.clear();
        first.namedTag.putLong("UniqueID", 1);
        second.namedTag.putLong("UniqueID", 2);
        clearInvocations(chunk, batch);
        DB database = mock(DB.class);
        when(database.get(any(byte[].class))).thenReturn(ByteBuffer.allocate(24).put(key).putLong(44).putLong(45).array());
        cleanup.apply(database, batch);
        verifyNoInteractions(chunk);
        verify(batch).delete(actorKey(45));
        verifyNoMoreInteractions(batch);
    }

    @Test
    void nullAndAbsentDatabaseDigestNeedNoDeletion() {
        WriteBatch batch = mock(WriteBatch.class);
        EntitySerializer.Cleanup cleanup = EntityStorageV1_18_30.INSTANCE.snapshotEntities(batch, chunk(Map.of(), List.of(actor(0, (byte) 1))));
        clearInvocations(batch);
        cleanup.apply(null, batch);
        verifyNoInteractions(batch);
        DB database = mock(DB.class);
        cleanup.apply(database, batch);
        verify(database).get(digp());
        verifyNoInteractions(batch);
    }

    @Test
    void emptySnapshotAndDeleteKeepDuplicateStaleDeleteOrder() {
        LevelDBChunk chunk = chunk(Map.of(), List.of());
        WriteBatch batch = mock(WriteBatch.class);
        List<byte[]> deletes = new ArrayList<>();
        when(batch.delete(any(byte[].class))).thenAnswer(call -> {
            deletes.add(call.<byte[]>getArgument(0).clone());
            return batch;
        });
        EntitySerializer.Cleanup cleanup = EntityStorageV1_18_30.INSTANCE.snapshotEntities(batch, chunk);
        assertEquals(1, deletes.size());
        assertArrayEquals(digp(), deletes.get(0));
        deletes.clear();
        DB database = mock(DB.class);
        when(database.get(any(byte[].class))).thenReturn(digest(0, Long.MIN_VALUE, 0, -1));
        cleanup.apply(database, batch);
        assertDeleteOrder(deletes, 0, Long.MIN_VALUE, 0, -1);
        deletes.clear();
        EntityStorageV1_18_30.INSTANCE.deleteEntities(database, batch, chunk);
        assertEquals(5, deletes.size());
        assertDeleteOrder(deletes.subList(0, 4), 0, Long.MIN_VALUE, 0, -1);
        assertArrayEquals(digp(), deletes.get(4));
    }

    private static void assertDeleteOrder(List<byte[]> deleted, long... ids) {
        assertEquals(ids.length, deleted.size());
        for (int i = 0; i < ids.length; i++) assertArrayEquals(actorKey(ids[i]), deleted.get(i));
    }

    private static Entity entity(long id) {
        Entity entity = mock(Entity.class);
        entity.namedTag = new CompoundTag().putLong("UniqueID", id);
        when(entity.canBeSavedWithChunk()).thenReturn(true);
        return entity;
    }

    private static LevelDBChunk chunk(Map<Long, Entity> live, List<LevelDBChunk.PreservedEntityActor> actors) {
        Level level = mock(Level.class);
        when(level.getDimension()).thenReturn(0);
        LevelProvider provider = mock(LevelProvider.class);
        when(provider.getLevel()).thenReturn(level);
        LevelDBChunk chunk = mock(LevelDBChunk.class);
        when(chunk.getProvider()).thenReturn(provider);
        when(chunk.getEntities()).thenReturn(live);
        when(chunk.getPreservedEntityActors()).thenReturn(actors);
        return chunk;
    }

    private static LevelDBChunk.PreservedEntityActor actor(long id, byte value) {
        return new LevelDBChunk.PreservedEntityActor(digest(id), new byte[]{value});
    }

    private static byte[] digest(long... ids) {
        ByteBuffer buffer = ByteBuffer.allocate(ids.length * 8);
        for (long id : ids) buffer.putLong(id);
        return buffer.array();
    }

    private static byte[] actorKey(long id) { return LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, digest(id)); }
    private static byte[] digp() { return LevelDBKey.getKey(LevelDBKey.DIGP_PREFIX, 0, 0, 0); }
}
