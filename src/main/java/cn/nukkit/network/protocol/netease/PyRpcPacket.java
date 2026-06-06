package cn.nukkit.network.protocol.netease;

import cn.nukkit.api.OnlyNetEase;
import cn.nukkit.network.protocol.DataPacket;
import cn.nukkit.network.protocol.ProtocolInfo;
import lombok.ToString;

import java.io.ByteArrayOutputStream;
import java.lang.reflect.Array;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;

/**
 * NetEase packet used for Python scripting RPC calls.
 */
@OnlyNetEase
@ToString
public class PyRpcPacket extends DataPacket {

    public static final int NETWORK_ID = ProtocolInfo.PY_RPC_PACKET;

    public static final long DEFAULT_MSG_ID = 9753608L;
    public static final String MOD_EVENT_C2S = "ModEventC2S";
    public static final String MOD_EVENT_S2C = "ModEventS2C";
    public static final String STORE_BUY_SUCCESS_EVENT = "StoreBuySuccServerEvent";

    private static final int MAX_SUBPACKET_PAYLOAD_SIZE = 64 * 1024;
    private static final int MAX_MESSAGE_PACK_CONTAINER_SIZE = 1024;
    private static final int MAX_MESSAGE_PACK_DEPTH = 32;
    private static final Object MISSING_VALUE = new Object();

    public byte[] data = new byte[0];
    public long msgId;
    public List<SubPacket> subPackets = Collections.emptyList();

    @Override
    public int packetId() {
        return NETWORK_ID;
    }

    @Override
    @Deprecated
    public byte pid() {
        throw new UnsupportedOperationException("Not supported.");
    }

    @Override
    public void decode() {
        this.data = this.getByteArray();
        this.msgId = this.getLInt() & 0xffffffffL;
        this.subPackets = decodeSubPackets(this.data);
    }

    @Override
    public void encode() {
        this.reset();
        this.putByteArray(this.data != null ? this.data : new byte[0]);
        this.putLInt((int) this.msgId);
    }

    public byte[] getData() {
        return this.data;
    }

    public void setData(byte[] data) {
        this.data = data != null ? data : new byte[0];
    }

    public long getMsgId() {
        return this.msgId;
    }

    public void setMsgId(long msgId) {
        this.msgId = msgId & 0xffffffffL;
    }

    public List<SubPacket> getSubPackets() {
        return this.subPackets;
    }

    public static PyRpcPacket createModEventPacket(String modName, String systemName, String eventName,
                                                   Map<String, ?> eventData) {
        PyRpcPacket packet = new PyRpcPacket();
        packet.setData(encodeModEventS2C(modName, systemName, eventName, eventData));
        packet.setMsgId(DEFAULT_MSG_ID);
        return packet;
    }

    public static PyRpcPacket createEncryptedModEventPacket(String modName, String systemName, String eventName,
                                                            String data, Function<String, String> encMethod) {
        Objects.requireNonNull(encMethod, "encMethod");
        Map<String, Object> eventData = new LinkedHashMap<>();
        eventData.put("data", Objects.requireNonNull(encMethod.apply(data), "encrypted data"));
        return createModEventPacket(modName, systemName, eventName, eventData);
    }

    private static byte[] encodeModEventS2C(String modName, String systemName, String eventName, Map<String, ?> eventData) {
        MessagePackWriter writer = new MessagePackWriter();
        writer.writeArrayHeader(3);
        writer.writeBinaryString(MOD_EVENT_S2C);
        writer.writeArrayHeader(4);
        writer.writeBinaryString(modName);
        writer.writeBinaryString(systemName);
        writer.writeBinaryString(eventName);
        writer.writeObject(eventData != null ? eventData : Collections.emptyMap());
        writer.writeNil();
        return writer.toByteArray();
    }

    public static List<SubPacket> decodeSubPackets(byte[] data) {
        if (data == null || data.length == 0) {
            return Collections.emptyList();
        }
        if (data.length > MAX_SUBPACKET_PAYLOAD_SIZE) {
            return Collections.emptyList();
        }

        try {
            Object root = new MessagePackReader(data).readValue();
            List<?> values = root instanceof Map<?, ?> map ? asList(getMapValue(map, "value")) : asList(root);
            if (values == null || values.isEmpty()) {
                return Collections.emptyList();
            }

            String type = asString(values.get(0));
            if (STORE_BUY_SUCCESS_EVENT.equals(type)) {
                return Collections.singletonList(new StoreBuySuccessSubPacket());
            }

            if (!MOD_EVENT_C2S.equals(type) || values.size() < 2) {
                return Collections.emptyList();
            }

            List<?> args = asList(values.get(1));
            if (args == null || args.size() < 4) {
                return Collections.emptyList();
            }

            String modName = asString(args.get(0));
            String systemName = asString(args.get(1));
            String eventName = asString(args.get(2));
            if (modName == null || systemName == null || eventName == null) {
                return Collections.emptyList();
            }

            return Collections.singletonList(new ModEventSubPacket(
                    modName,
                    systemName,
                    eventName,
                    asStringMap(args.get(3))));
        } catch (RuntimeException ignored) {
            return Collections.emptyList();
        }
    }

    private static List<?> asList(Object value) {
        Object unwrapped = unwrapValue(value);
        return unwrapped instanceof List<?> list ? list : null;
    }

    private static Object unwrapValue(Object value) {
        if (value instanceof Map<?, ?> map && map.containsKey("value")) {
            return map.get("value");
        } else if (value instanceof Map<?, ?> map) {
            Object wrapped = getMapValue(map, "value");
            if (wrapped != MISSING_VALUE) {
                return wrapped;
            }
        }
        return value;
    }

    private static Object getMapValue(Map<?, ?> map, String key) {
        if (map.containsKey(key)) {
            return map.get(key);
        }
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            if (key.equals(asString(entry.getKey()))) {
                return entry.getValue();
            }
        }
        return MISSING_VALUE;
    }

    private static String asString(Object value) {
        if (value instanceof String string) {
            return string;
        }
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        return null;
    }

    private static Map<String, Object> asStringMap(Object value) {
        if (!(value instanceof Map<?, ?> map)) {
            return Collections.emptyMap();
        }

        Map<String, Object> result = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = asString(entry.getKey());
            if (key != null) {
                result.put(key, normalizeValue(entry.getValue()));
            }
        }
        return result;
    }

    private static Object normalizeValue(Object value) {
        if (value instanceof byte[] bytes) {
            return new String(bytes, StandardCharsets.UTF_8);
        }
        if (value instanceof List<?> list) {
            List<Object> result = new ArrayList<>(list.size());
            for (Object element : list) {
                result.add(normalizeValue(element));
            }
            return result;
        }
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> result = new LinkedHashMap<>();
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                String key = asString(entry.getKey());
                if (key != null) {
                    result.put(key, normalizeValue(entry.getValue()));
                }
            }
            return result;
        }
        return value;
    }

    public interface SubPacket {
    }

    public static final class StoreBuySuccessSubPacket implements SubPacket {
    }

    public static final class ModEventSubPacket implements SubPacket {
        private final String modName;
        private final String systemName;
        private final String eventName;
        private final Map<String, Object> eventData;

        public ModEventSubPacket(String modName, String systemName, String eventName, Map<String, Object> eventData) {
            this.modName = modName;
            this.systemName = systemName;
            this.eventName = eventName;
            this.eventData = eventData != null ? Collections.unmodifiableMap(new LinkedHashMap<>(eventData)) : Collections.emptyMap();
        }

        public String getModName() {
            return modName;
        }

        public String getSystemName() {
            return systemName;
        }

        public String getEventName() {
            return eventName;
        }

        public Map<String, Object> getEventData() {
            return eventData;
        }
    }

    private static final class MessagePackWriter {
        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        private byte[] toByteArray() {
            return this.out.toByteArray();
        }

        private void writeObject(Object value) {
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

        private void writeNil() {
            writeByte(0xc0);
        }

        private void writeBinaryString(String value) {
            writeBinary((value != null ? value : "").getBytes(StandardCharsets.UTF_8));
        }

        private void writeBinary(byte[] bytes) {
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

        private void writeArrayHeader(int size) {
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

        private void writeMapHeader(int size) {
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

    private static final class MessagePackReader {
        private final byte[] data;
        private int offset;

        private MessagePackReader(byte[] data) {
            this.data = data;
        }

        private Object readValue() {
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
}
