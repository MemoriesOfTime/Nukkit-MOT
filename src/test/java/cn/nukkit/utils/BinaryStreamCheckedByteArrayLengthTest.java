package cn.nukkit.utils;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;

class BinaryStreamCheckedByteArrayLengthTest {
    @ParameterizedTest
    @ValueSource(longs = {0x80000000L, 0xffffffffL, 0x100000000L, 0x7ffffffffL})
    void rejectsUnsignedLengthBeforeNarrowing(long length) throws IOException {
        ByteArrayOutputStream prefix = new ByteArrayOutputStream();
        VarInt.writeUnsignedVarInt(prefix, length);
        BinaryStream wire = new BinaryStream();
        wire.put(prefix.toByteArray());
        wire.put(new byte[8]);
        assertThrows(IllegalArgumentException.class,
                () -> new BinaryStream(wire.getBuffer()).getByteArray(256));
    }

    @Test
    void allocatedCapacityDoesNotCountAsReadablePayload() {
        BinaryStream wire = new BinaryStream();
        wire.putUnsignedVarInt(4);
        wire.put(new byte[]{1, 2});
        BinaryStream reader = new BinaryStream(Arrays.copyOf(wire.getBuffer(), 64));
        reader.setCount(wire.getCount());
        assertThrows(IllegalArgumentException.class, () -> reader.getByteArray(256));
    }

    @Test
    void refusesMissingPrefixInAllocatedCapacity() {
        BinaryStream reader = new BinaryStream(new byte[16]);
        reader.setCount(0);
        assertThrows(IllegalArgumentException.class, () -> reader.getByteArray(256));
    }

    @Test
    void refusesInvalidStartingOffset() {
        BinaryStream reader = new BinaryStream(new byte[]{0}, -1);
        assertThrows(IllegalArgumentException.class, () -> reader.getByteArray(256));
    }

    @Test
    void acceptsCompletePayloadAtLimitAndConsumesExactlyIt() {
        BinaryStream wire = new BinaryStream();
        wire.putByte((byte) 99);
        wire.putByteArray(new byte[]{1, 2, 3});
        wire.putByte((byte) 55);
        BinaryStream reader = new BinaryStream(wire.getBuffer(), 1);
        assertArrayEquals(new byte[]{1, 2, 3}, reader.getByteArray(3));
        assertEquals(55, reader.getByte());
    }

    @Test
    void existingExplicitLimitStillRejectsCompleteLargerPayload() {
        BinaryStream wire = new BinaryStream();
        wire.putByteArray(new byte[]{1, 2, 3});
        assertThrows(IllegalArgumentException.class,
                () -> new BinaryStream(wire.getBuffer()).getByteArray(2));
    }

    @Test
    void zeroLimitKeepsAllowingCompletePayloadAndEmptyPayload() {
        BinaryStream wire = new BinaryStream();
        wire.putByteArray(new byte[]{1, 2, 3});
        wire.putByteArray(new byte[0]);
        BinaryStream reader = new BinaryStream(wire.getBuffer());
        assertArrayEquals(new byte[]{1, 2, 3}, reader.getByteArray(0));
        assertArrayEquals(new byte[0], reader.getByteArray(0));
        assertEquals(wire.getCount(), reader.getOffset());
    }
}
