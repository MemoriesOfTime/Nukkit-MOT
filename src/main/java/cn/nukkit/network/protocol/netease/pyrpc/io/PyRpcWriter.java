package cn.nukkit.network.protocol.netease.pyrpc.io;

import cn.nukkit.api.OnlyNetEase;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * MessagePack writer for NetEase PyRpc payloads.
 */
@OnlyNetEase
public final class PyRpcWriter {

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();

    public byte[] toByteArray() {
        return this.out.toByteArray();
    }

    public void writeMessage(String method, List<?> arguments) {
        writeArrayHeader(3);
        writeBinaryString(method);
        writeObject(arguments != null ? arguments : List.of());
        writeNil();
    }

    public void writeObject(Object value) {
        if (value == null) {
            writeNil();
        } else if (value instanceof CharSequence sequence) {
            writeBinaryString(sequence.toString());
        } else if (value instanceof byte[] bytes) {
            writeBinary(bytes);
        } else if (value instanceof Boolean bool) {
            writeByte(bool ? 0xc3 : 0xc2);
        } else if (value instanceof Float || value instanceof Double) {
            writeDouble(((Number) value).doubleValue());
        } else if (value instanceof BigInteger bigInteger) {
            if (bigInteger.bitLength() < Long.SIZE) {
                writeLong(bigInteger.longValue());
            } else {
                writeBinaryString(bigInteger.toString());
            }
        } else if (value instanceof Number number) {
            writeLong(number.longValue());
        } else if (value instanceof Map<?, ?> map) {
            writeMap(map);
        } else if (value instanceof Iterable<?> iterable) {
            writeIterable(iterable);
        } else if (value.getClass().isArray()) {
            writeArray(value);
        } else {
            writeBinaryString(value.toString());
        }
    }

    public void writeNil() {
        writeByte(0xc0);
    }

    public void writeBinaryString(String value) {
        writeBinary((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
    }

    public void writeBinary(byte[] bytes) {
        int length = bytes.length;
        if (length <= 0xff) {
            writeByte(0xc4);
            writeByte(length);
        } else if (length <= 0xffff) {
            writeByte(0xc5);
            writeShort(length);
        } else {
            writeByte(0xc6);
            writeInt(length);
        }
        writeBytes(bytes);
    }

    public void writeArrayHeader(int size) {
        if (size < 16) {
            writeByte(0x90 | size);
        } else if (size <= 0xffff) {
            writeByte(0xdc);
            writeShort(size);
        } else {
            writeByte(0xdd);
            writeInt(size);
        }
    }

    public void writeMapHeader(int size) {
        if (size < 16) {
            writeByte(0x80 | size);
        } else if (size <= 0xffff) {
            writeByte(0xde);
            writeShort(size);
        } else {
            writeByte(0xdf);
            writeInt(size);
        }
    }

    private void writeLong(long value) {
        if (value >= 0 && value <= 0x7f) {
            writeByte((int) value);
        } else if (value >= -32 && value < 0) {
            writeByte((int) value);
        } else if (value >= 0 && value <= 0xff) {
            writeByte(0xcc);
            writeByte((int) value);
        } else if (value >= 0 && value <= 0xffff) {
            writeByte(0xcd);
            writeShort((int) value);
        } else if (value >= 0 && value <= 0xffffffffL) {
            writeByte(0xce);
            writeInt((int) value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            writeByte(0xd0);
            writeByte((int) value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            writeByte(0xd1);
            writeShort((int) value);
        } else if (value >= Integer.MIN_VALUE && value <= Integer.MAX_VALUE) {
            writeByte(0xd2);
            writeInt((int) value);
        } else {
            writeByte(0xd3);
            writeLongBytes(value);
        }
    }

    private void writeDouble(double value) {
        writeByte(0xcb);
        writeLongBytes(Double.doubleToLongBits(value));
    }

    private void writeMap(Map<?, ?> map) {
        writeMapHeader(map.size());
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            writeBinaryString(String.valueOf(entry.getKey()));
            writeObject(entry.getValue());
        }
    }

    private void writeIterable(Iterable<?> iterable) {
        if (iterable instanceof Collection<?> collection) {
            writeArrayHeader(collection.size());
            for (Object value : collection) {
                writeObject(value);
            }
            return;
        }

        List<Object> values = new ArrayList<>();
        for (Object value : iterable) {
            values.add(value);
        }
        writeArrayHeader(values.size());
        for (Object value : values) {
            writeObject(value);
        }
    }

    private void writeArray(Object array) {
        int length = Array.getLength(array);
        writeArrayHeader(length);
        for (int i = 0; i < length; i++) {
            writeObject(Array.get(array, i));
        }
    }

    private void writeByte(int value) {
        this.out.write(value & 0xff);
    }

    private void writeShort(int value) {
        writeByte(value >>> 8);
        writeByte(value);
    }

    private void writeInt(int value) {
        writeByte(value >>> 24);
        writeByte(value >>> 16);
        writeByte(value >>> 8);
        writeByte(value);
    }

    private void writeLongBytes(long value) {
        writeInt((int) (value >>> 32));
        writeInt((int) value);
    }

    private void writeBytes(byte[] bytes) {
        this.out.writeBytes(bytes);
    }
}
