package cn.nukkit.network.protocol.netease.pyrpc.io;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bounded MessagePack reader for NetEase PyRpc payloads.
 */
public final class PyRpcReader {

    private static final int MAX_MESSAGE_PACK_CONTAINER_SIZE = 1024;
    private static final int MAX_MESSAGE_PACK_DEPTH = 32;

    private final byte[] data;
    private int offset;

    public PyRpcReader(byte[] data) {
        this.data = data;
    }

    public Object readValue() {
        return readValue(0);
    }

    private Object readValue(int depth) {
        if (depth > MAX_MESSAGE_PACK_DEPTH) {
            throw new IllegalArgumentException("MessagePack nesting is too deep");
        }

        int code = readUnsignedByte();
        if (code <= 0x7f) {
            return code;
        }
        if (code >= 0x80 && code <= 0x8f) {
            return readMap(code & 0x0f, depth + 1);
        }
        if (code >= 0x90 && code <= 0x9f) {
            return readArray(code & 0x0f, depth + 1);
        }
        if (code >= 0xa0 && code <= 0xbf) {
            return readString(code & 0x1f);
        }
        if (code >= 0xe0) {
            return (byte) code;
        }

        return switch (code) {
            case 0xc0 -> null;
            case 0xc2 -> false;
            case 0xc3 -> true;
            case 0xc4 -> readBytes(readUnsignedByte());
            case 0xc5 -> readBytes(readUnsignedShort());
            case 0xc6 -> readBytes(readLength32());
            case 0xca -> Float.intBitsToFloat((int) readUnsignedInt());
            case 0xcb -> Double.longBitsToDouble(readLong());
            case 0xcc -> readUnsignedByte();
            case 0xcd -> readUnsignedShort();
            case 0xce -> readUnsignedInt();
            case 0xcf -> readLong();
            case 0xd0 -> (byte) readUnsignedByte();
            case 0xd1 -> (short) readUnsignedShort();
            case 0xd2 -> (int) readUnsignedInt();
            case 0xd3 -> readLong();
            case 0xd9 -> readString(readUnsignedByte());
            case 0xda -> readString(readUnsignedShort());
            case 0xdb -> readString(readLength32());
            case 0xdc -> readArray(readUnsignedShort(), depth + 1);
            case 0xdd -> readArray(readLength32(), depth + 1);
            case 0xde -> readMap(readUnsignedShort(), depth + 1);
            case 0xdf -> readMap(readLength32(), depth + 1);
            default -> throw new IllegalArgumentException("Unsupported MessagePack code: " + code);
        };
    }

    private List<Object> readArray(int size, int depth) {
        checkContainer(size, depth);
        List<Object> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(readValue(depth));
        }
        return values;
    }

    private Map<Object, Object> readMap(int size, int depth) {
        checkContainer(size, depth);
        Map<Object, Object> values = new LinkedHashMap<>();
        for (int i = 0; i < size; i++) {
            Object key = readValue(depth);
            values.put(key, readValue(depth));
        }
        return values;
    }

    private void checkContainer(int size, int depth) {
        if (depth > MAX_MESSAGE_PACK_DEPTH) {
            throw new IllegalArgumentException("MessagePack nesting is too deep");
        }
        if (size < 0 || size > MAX_MESSAGE_PACK_CONTAINER_SIZE) {
            throw new IllegalArgumentException("MessagePack container is too large: " + size);
        }
    }

    private String readString(int length) {
        return new String(readBytes(length), StandardCharsets.UTF_8);
    }

    private byte[] readBytes(int length) {
        ensure(length);
        byte[] bytes = new byte[length];
        System.arraycopy(this.data, this.offset, bytes, 0, length);
        this.offset += length;
        return bytes;
    }

    private int readUnsignedByte() {
        ensure(1);
        return this.data[this.offset++] & 0xff;
    }

    private int readUnsignedShort() {
        ensure(2);
        return (readUnsignedByte() << 8) | readUnsignedByte();
    }

    private long readUnsignedInt() {
        ensure(4);
        return ((long) readUnsignedByte() << 24)
                | ((long) readUnsignedByte() << 16)
                | ((long) readUnsignedByte() << 8)
                | readUnsignedByte();
    }

    private long readLong() {
        ensure(8);
        return (readUnsignedInt() << 32) | readUnsignedInt();
    }

    private int readLength32() {
        long length = readUnsignedInt();
        if (length > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("MessagePack value is too large: " + length);
        }
        return (int) length;
    }

    private void ensure(int length) {
        if (length < 0 || length > this.data.length - this.offset) {
            throw new IllegalArgumentException("Unexpected end of MessagePack payload");
        }
    }
}
