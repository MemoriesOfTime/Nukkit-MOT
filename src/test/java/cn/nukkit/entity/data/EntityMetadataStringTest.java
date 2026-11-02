package cn.nukkit.entity.data;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SuppressWarnings("rawtypes")
class EntityMetadataStringTest {
    @Test
    void nativeStringsKeepPayloadIdentityNullAndIdRepair() {
        EntityMetadata metadata = new EntityMetadata();
        for (int id : new int[]{0, 4, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            for (String payload : new String[]{"", new String("Mob 🐷"), null}) {
                StringEntityData data = new StringEntityData(id, payload);
                metadata.put(data);
                data.setId(id ^ 1234);
                assertSame(payload, metadata.getString(id));
                assertEquals(id, data.getId());
                data.setData(payload == null ? "changed" : null);
                assertSame(data.data, metadata.getString(id));
            }
        }
    }

    @Test
    void missingAndRemovedStringsUseEmptyDefaultWithoutInsertion() {
        EntityMetadata metadata = new EntityMetadata();
        for (int id : new int[]{0, 4, -1, Integer.MIN_VALUE, Integer.MAX_VALUE}) {
            assertEquals("", metadata.getString(id));
            assertFalse(metadata.exists(id));
            metadata.putString(id, "name");
            metadata.remove(id);
            assertEquals("", metadata.getString(id));
            assertFalse(metadata.exists(id));
        }
    }

    @Test
    void wrongPayloadTypeStillThrows() {
        EntityMetadata metadata = new EntityMetadata().putInt(4, 123);
        assertThrows(ClassCastException.class, () -> metadata.getString(4));
    }

    @Test
    void customDataReturningStringKeepsVirtualCallbacks() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, new String("custom"));
        metadata.put(data);
        assertSame(data.payload, metadata.getString(4));
        assertEquals(1, data.sets);
        assertEquals(1, data.reads);
    }

    @Test
    void stringSubclassKeepsHooksAndReentrantReads() {
        EntityMetadata metadata = new EntityMetadata();
        List<String> calls = new ArrayList<>();
        StringEntityData data = new StringEntityData(4, "subclass") {
            @Override
            public EntityData setId(int id) {
                calls.add("set");
                return super.setId(id);
            }

            @Override
            public String getData() {
                calls.add("read");
                assertEquals("", metadata.getString(9));
                return super.getData();
            }
        };
        metadata.put(data);
        assertSame(data.data, metadata.getString(4));
        assertEquals(List.of("set", "read"), calls);
    }

    @Test
    void setIdFailuresKeepOriginalFallbackAndPropagation() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, "custom");
        metadata.put(data);
        data.mode = 1;
        assertEquals("", metadata.getString(4));
        assertEquals(0, data.reads);
        data.mode = 2;
        assertThrows(NullPointerException.class, () -> metadata.getString(4));
        data.mode = 3;
        assertSame(data.error, assertThrows(AssertionError.class, () -> metadata.getString(4)));
        assertEquals(3, data.sets);
        assertEquals(0, data.reads);
    }

    @Test
    void dataReadFailureStillEscapesGetOrDefaultCatch() {
        EntityMetadata metadata = new EntityMetadata();
        CustomData data = new CustomData(4, "custom");
        data.failRead = true;
        metadata.put(data);
        assertSame(data.failure, assertThrows(IllegalStateException.class, () -> metadata.getString(4)));
        assertEquals(1, data.sets);
        assertEquals(1, data.reads);
    }

    @Test
    void metadataSubclassReceivesFreshDefaultsThroughOriginalHook() {
        List<EntityData> defaults = new ArrayList<>();
        EntityMetadata metadata = new EntityMetadata() {
            @Override
            public EntityData get(int id) {
                throw new AssertionError("get must not replace getOrDefault");
            }

            @Override
            public EntityData getOrDefault(int id, EntityData fallback) {
                defaults.add(fallback);
                assertEquals(StringEntityData.class, fallback.getClass());
                assertEquals(id, fallback.getId());
                assertEquals("", fallback.getData());
                return new StringEntityData(id, "hooked");
            }
        };
        metadata.putString(4, "stored");
        assertEquals("hooked", metadata.getString(4));
        assertEquals("hooked", metadata.getString(4));
        assertEquals(2, defaults.size());
        assertNotSame(defaults.get(0), defaults.get(1));
    }

    @Test
    void cloneKeepsIndependentMapAndSharedMutableData() {
        EntityMetadata metadata = new EntityMetadata();
        StringEntityData shared = new StringEntityData(4, "first");
        metadata.put(shared);
        EntityMetadata copy = metadata.clone();
        shared.setData(new String("second"));
        assertSame(shared.data, copy.getString(4));
        copy.remove(4);
        metadata.getMap().clear();
        assertSame(shared.data, metadata.getString(4));
        assertEquals("", copy.getString(4));
    }

    private static final class CustomData extends EntityData<String> {
        private String payload;
        private int sets;
        private int reads;
        private int mode;
        private boolean failRead;
        private final IllegalStateException failure = new IllegalStateException("fixture");
        private final AssertionError error = new AssertionError("fixture");

        private CustomData(int id, String payload) {
            super(id);
            this.payload = payload;
        }

        @Override public int getType() { return 4; }
        @Override public void setData(String value) { payload = value; }
        @Override public String getData() {
            reads++;
            if (failRead) throw failure;
            return payload;
        }
        @Override public EntityData setId(int id) {
            sets++;
            if (mode == 1) throw failure;
            if (mode == 2) return null;
            if (mode == 3) throw error;
            return super.setId(id);
        }
    }
}
