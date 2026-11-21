package cn.nukkit.entity.data;

import org.junit.jupiter.api.Test;

import java.lang.management.ManagementFactory;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EntityMetadataPrimitiveGetterTest {
    @Test
    void nativeNumericGettersDoNotAllocateFallbackObjects() {
        EntityMetadata metadata = new EntityMetadata()
                .putByte(100, 200).putInt(101, 0x12345678).putLong(102, 0x123456789abcdefL).putFloat(103, 1.25f);
        long expected = 200 + 0x12345678L + 0x123456789abcdefL + 1;
        for (int i = 0; i < 50_000; i++) {
            assertEquals(expected, read(metadata));
        }

        com.sun.management.ThreadMXBean threads = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        long thread = Thread.currentThread().getId();
        long before = threads.getThreadAllocatedBytes(thread);
        long sum = 0;
        for (int i = 0; i < 10_000; i++) {
            sum += read(metadata);
        }
        long allocated = threads.getThreadAllocatedBytes(thread) - before;

        assertEquals(expected * 10_000, sum);
        assertTrue(allocated < 64 * 1024, "numeric reads allocated " + allocated + " bytes for 40,000 lookups");
    }

    private static long read(EntityMetadata metadata) {
        return metadata.getByte(100) + metadata.getInt(101) + metadata.getLong(102) + (long) metadata.getFloat(103);
    }

    @Test
    void wrongNativePayloadTypesKeepCastFailures() {
        EntityMetadata metadata = new EntityMetadata().putString(100, "wrong");
        assertThrows(ClassCastException.class, () -> metadata.getByte(100));
        assertThrows(ClassCastException.class, () -> metadata.getInt(100));
        assertThrows(ClassCastException.class, () -> metadata.getLong(100));
        assertThrows(ClassCastException.class, () -> metadata.getFloat(100));
    }

    @Test
    void missingNumericValuesKeepZeroDefaultsWithoutInsertion() {
        EntityMetadata metadata = new EntityMetadata();

        assertEquals(0, metadata.getByte(100));
        assertEquals(0, metadata.getInt(101));
        assertEquals(0L, metadata.getLong(102));
        assertEquals(0.0f, metadata.getFloat(103));
        assertEquals(0, metadata.getMap().size());
    }

    @Test
    void nativeValuesStillRepairMutatedEntryIds() {
        EntityMetadata metadata = new EntityMetadata();
        ByteEntityData byteData = new ByteEntityData(100, 200);
        IntEntityData intData = new IntEntityData(101, 42);
        LongEntityData longData = new LongEntityData(102, 1_000_000_000_000L);
        FloatEntityData floatData = new FloatEntityData(103, 1.25f);
        metadata.put(byteData).put(intData).put(longData).put(floatData);

        byteData.setId(-1);
        intData.setId(-1);
        longData.setId(-1);
        floatData.setId(-1);

        assertEquals(200, metadata.getByte(100));
        assertEquals(42, metadata.getInt(101));
        assertEquals(1_000_000_000_000L, metadata.getLong(102));
        assertEquals(1.25f, metadata.getFloat(103));
        assertEquals(100, byteData.getId());
        assertEquals(101, intData.getId());
        assertEquals(102, longData.getId());
        assertEquals(103, floatData.getId());
    }

    @Test
    void metadataSubclassKeepsVirtualDefaultLookup() {
        CustomMetadata metadata = new CustomMetadata();
        metadata.putInt(100, 42);

        assertEquals(42, metadata.getInt(100));
        assertEquals(1, metadata.defaultLookups);
    }

    private static final class CustomMetadata extends EntityMetadata {
        int defaultLookups;

        @Override
        public EntityData getOrDefault(int id, EntityData defaultValue) {
            defaultLookups++;
            return super.getOrDefault(id, defaultValue);
        }
    }
}
