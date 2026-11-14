package cn.nukkit.level.format.leveldb;

import org.iq80.leveldb.impl.WriteBatchImpl;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CapturedWriteBatchTest {
    @Test
    void copiesArraysAndPreservesPutDeletePutOrder() throws Exception {
        CapturedWriteBatch captured = new CapturedWriteBatch();
        byte[] key = {1}, value = {2};
        captured.put(key, value);
        captured.delete(key);
        captured.put(key, new byte[]{3});
        key[0] = 9;
        value[0] = 9;
        List<String> operations = new ArrayList<>();
        try (WriteBatchImpl target = new WriteBatchImpl()) {
            captured.replay(target);
            target.forEach(new WriteBatchImpl.Handler() {
                @Override public void put(org.iq80.leveldb.util.Slice k, org.iq80.leveldb.util.Slice v) { operations.add(k.getByte(0) + "=" + v.getByte(0)); }
                @Override public void delete(org.iq80.leveldb.util.Slice k) { operations.add("delete " + k.getByte(0)); }
            });
        }
        assertEquals(List.of("1=2", "delete 1", "1=3"), operations);
        assertEquals(3, captured.size());
        assertTrue(captured.getApproximateSize() >= 40);
    }

    @Test
    void actorWritesStayOnOriginalPerColumnPath() {
        CapturedWriteBatch captured = new CapturedWriteBatch();
        byte[] actor = LevelDBKey.getKey(LevelDBKey.ACTOR_PREFIX, new byte[8]);
        captured.delete(actor);
        assertTrue(captured.canGroup());
        captured.put(actor, new byte[]{1});
        assertFalse(captured.canGroup(), "a neighbouring cleanup must not erase this actor put");
    }
}
