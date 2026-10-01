package cn.nukkit.nbt.stream;

import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.VarInt;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class NBTInputStream implements DataInput, AutoCloseable {

    /**
     * Largest allocation made up front for a length read from the stream. Longer payloads grow
     * with the bytes that actually arrive, so a forged length costs at most this much before
     * the stream runs dry.
     */
    static final int SAFE_INITIAL_BYTES = 64 * 1024;

    private final DataInputStream stream;
    private final ByteOrder endianness;
    private final boolean network;
    private final AtomicBoolean closed = new AtomicBoolean(false);
    // When true, large allocations (length/size > 64) use growing backed lists
    // instead of pre-allocating a single big array, mitigating malicious oversized NBT.
    private boolean readSafely = true;

    public NBTInputStream(InputStream stream) {
        this(stream, ByteOrder.BIG_ENDIAN);
    }

    public NBTInputStream(InputStream stream, ByteOrder endianness) {
        this(stream, endianness, false);
    }

    public NBTInputStream(InputStream stream, ByteOrder endianness, boolean network) {
        this.stream = stream instanceof DataInputStream ? (DataInputStream) stream : new DataInputStream(stream);
        this.endianness = endianness;
        this.network = network;
    }

    public ByteOrder getEndianness() {
        return endianness;
    }

    public boolean isNetwork() {
        return network;
    }

    public boolean isReadSafely() {
        return readSafely;
    }

    /**
     * Enable safe reading with allocation limits. When enabled, tags with a
     * declared length/size greater than 64 use growing backed lists instead of
     * pre-allocating a single large array, mitigating malicious oversized NBT
     * payloads. Safe mode is on by default.
     */
    public NBTInputStream readSafely() {
        this.readSafely = true;
        return this;
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        this.stream.readFully(b);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        this.stream.readFully(b, off, len);
    }

    @Override
    public int skipBytes(int n) throws IOException {
        return this.stream.skipBytes(n);
    }

    @Override
    public boolean readBoolean() throws IOException {
        return this.stream.readBoolean();
    }

    @Override
    public byte readByte() throws IOException {
        return this.stream.readByte();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return this.stream.readUnsignedByte();
    }

    @Override
    public short readShort() throws IOException {
        short s = this.stream.readShort();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            s = Short.reverseBytes(s);
        }
        return s;
    }

    @Override
    public int readUnsignedShort() throws IOException {
        // The former little-endian branch used a signed shift: 32768..65535 came back
        // negative, so a long string in a chunk or block entity could not be read at all.
        return this.readShort() & 0xFFFF;
    }

    @Override
    public char readChar() throws IOException {
        char c = this.stream.readChar();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            c = Character.reverseBytes(c);
        }
        return c;
    }

    @Override
    public int readInt() throws IOException {
        if (network) {
            return VarInt.readVarInt(this.stream);
        }
        int i = this.stream.readInt();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            i = Integer.reverseBytes(i);
        }
        return i;
    }

    @Override
    public long readLong() throws IOException {
        if (network) {
            return VarInt.readVarLong(this.stream);
        }
        long l = this.stream.readLong();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            l = Long.reverseBytes(l);
        }
        return l;
    }

    @Override
    public float readFloat() throws IOException {
        int i = this.stream.readInt();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            i = Integer.reverseBytes(i);
        }
        return Float.intBitsToFloat(i);
    }

    @Override
    public double readDouble() throws IOException {
        long l = this.stream.readLong();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            l = Long.reverseBytes(l);
        }
        return Double.longBitsToDouble(l);
    }

    @Override
    @SuppressWarnings("deprecation")
    public String readLine() throws IOException {
        return this.stream.readLine();
    }

    @Override
    public String readUTF() throws IOException {
        long length = network ? VarInt.readUnsignedVarInt(stream) : this.readUnsignedShort();
        if (length > Integer.MAX_VALUE) {
            throw new IOException("NBT string length out of range: " + length);
        }
        return new String(this.readByteArray((int) length), StandardCharsets.UTF_8);
    }

    /**
     * Reads exactly {@code length} bytes in bulk.
     * <p>
     * In safe mode the declared length is not trusted for the allocation: up to
     * {@link #SAFE_INITIAL_BYTES} are reserved at once, then the buffer doubles only after it has
     * been filled from the stream, so a forged length fails with an {@link java.io.EOFException}
     * after an allocation bounded by twice the bytes that really arrived. This keeps the
     * protection of the former byte-by-byte list while copying the payload with
     * {@link DataInputStream#readFully(byte[], int, int)}.
     */
    public byte[] readByteArray(int length) throws IOException {
        if (length < 0) {
            throw new IOException("Negative NBT array length: " + length);
        }
        if (!this.readSafely || length <= SAFE_INITIAL_BYTES) {
            byte[] bytes = new byte[length];
            this.stream.readFully(bytes);
            return bytes;
        }
        byte[] bytes = new byte[SAFE_INITIAL_BYTES];
        int filled = 0;
        while (true) {
            this.stream.readFully(bytes, filled, bytes.length - filled);
            filled = bytes.length;
            if (filled == length) {
                return bytes;
            }
            bytes = Arrays.copyOf(bytes, (int) Math.min(length, (long) filled << 1));
        }
    }

    /**
     * Reads {@code length} ints. Fixed-width encodings are copied in bulk through
     * {@link #readByteArray(int)} (same allocation bound); the network VarInt encoding has no
     * fixed width and is decoded element by element into a buffer that grows with the input.
     */
    public int[] readIntArray(int length) throws IOException {
        if (length < 0) {
            throw new IOException("Negative NBT array length: " + length);
        }
        if (this.network) {
            int[] ints = new int[this.readSafely ? Math.min(length, SAFE_INITIAL_BYTES / Integer.BYTES) : length];
            for (int i = 0; i < length; i++) {
                if (i == ints.length) {
                    ints = Arrays.copyOf(ints, (int) Math.min(length, (long) i << 1));
                }
                ints[i] = VarInt.readVarInt(this.stream);
            }
            return ints;
        }
        if (length > Integer.MAX_VALUE / Integer.BYTES) {
            throw new IOException("NBT int array length out of range: " + length);
        }
        // The payload first: readByteArray bounds its allocation by the bytes that arrive, so a
        // forged length (item NBT from a client is read in this format) fails before an int[] of
        // that length - up to 2 GiB - is reserved.
        byte[] payload = this.readByteArray(length * Integer.BYTES);
        int[] ints = new int[length];
        ByteBuffer.wrap(payload).order(this.endianness).asIntBuffer().get(ints);
        return ints;
    }

    public Object readTag() throws IOException {
        return this.readTag(16);
    }

    public Object readTag(int maxDepth) throws IOException {
        if (this.closed.get()) {
            throw new IllegalStateException("Trying to read from a closed reader!");
        } else {
            int typeId = this.readUnsignedByte();
            this.readUTF();
            return this.deserialize(typeId, maxDepth);
        }
    }

    public <T extends Tag> T readValue(int type) throws IOException {
        return this.readValue(type, 16);
    }

    public <T extends Tag> T readValue(int type, int maxDepth) throws IOException {
        if (this.closed.get()) {
            throw new IllegalStateException("Trying to read from a closed reader!");
        } else {
            return (T) this.deserialize(type, maxDepth);
        }
    }

    private Tag deserialize(int type, int maxDepth) throws IOException {
        if (maxDepth < 0) {
            throw new IllegalArgumentException("NBT compound is too deeply nested");
        } else {
            int arraySize;
            switch (type) {
                case Tag.TAG_End:
                    return null;
                case Tag.TAG_Byte:
                    return new ByteTag("", readByte());
                case Tag.TAG_Short:
                    return new ShortTag("", readShort());
                case Tag.TAG_Int:
                    return new IntTag("", readInt());
                case Tag.TAG_Long:
                    return new LongTag("", readLong());
                case Tag.TAG_Float:
                    return new FloatTag("", readFloat());
                case Tag.TAG_Double:
                    return new DoubleTag("", readDouble());
                case Tag.TAG_Byte_Array:
                    arraySize = this.readInt();
                    return new ByteArrayTag("", this.readByteArray(arraySize));
                case Tag.TAG_String:
                    return new StringTag("", this.readUTF());
                case Tag.TAG_Compound:
                    LinkedHashMap<String, Tag> map = new LinkedHashMap<>();
                    int nbtType;
                    while ((nbtType = this.readUnsignedByte()) != Tag.TAG_End) {
                        String name = this.readUTF();
                        map.put(name, deserialize(nbtType, maxDepth - 1));
                    }
                    return new CompoundTag(map);
                case Tag.TAG_List:
                    int typeId = this.readUnsignedByte();
                    int listLength = this.readInt();
                    if (typeId == Tag.TAG_End) {
                        return new ListTag<>(typeId, new ArrayList<Tag>());
                    }
                    List<Tag> list = new ArrayList<>(this.readSafely && listLength > 64 ? 64 : listLength);

                    for (int i = 0; i < listLength; ++i) {
                        list.add(this.deserialize(typeId, maxDepth - 1));
                    }
                    return new ListTag<>(typeId, list);
                case Tag.TAG_Int_Array:
                    arraySize = this.readInt();
                    return new IntArrayTag("", this.readIntArray(arraySize));
                default:
                    throw new IllegalArgumentException("Unknown type " + type);
            }
        }
    }

    public int available() throws IOException {
        return this.stream.available();
    }

    @Override
    public void close() throws IOException {
        this.closed.set(true);
        this.stream.close();
    }
}
