package cn.nukkit.nbt.stream;

import cn.nukkit.nbt.tag.*;
import cn.nukkit.utils.VarInt;
import it.unimi.dsi.fastutil.bytes.ByteArrayList;

import java.io.DataInput;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class NBTInputStream implements DataInput, AutoCloseable {

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
        int s = this.stream.readUnsignedShort();
        if (endianness == ByteOrder.LITTLE_ENDIAN) {
            s = Integer.reverseBytes(s) >> 16;
        }
        return s;
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
        int length = (int) (network ? VarInt.readUnsignedVarInt(stream) : this.readUnsignedShort());

        if (this.readSafely && length > 64) {
            ByteArrayList list = new ByteArrayList(64);
            for (int i = 0; i < length; i++) {
                list.add(this.stream.readByte());
            }
            return new String(list.toByteArray(), StandardCharsets.UTF_8);
        } else {
            byte[] bytes = new byte[length];
            this.stream.read(bytes);
            return new String(bytes, StandardCharsets.UTF_8);
        }
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
                    if (this.readSafely && arraySize > 64) {
                        ByteArrayList byteList = new ByteArrayList(64);
                        for (int i = 0; i < arraySize; i++) {
                            byteList.add(this.readByte());
                        }
                        return new ByteArrayTag("", byteList.toByteArray());
                    } else {
                        byte[] bytes = new byte[arraySize];
                        this.readFully(bytes);
                        return new ByteArrayTag("", bytes);
                    }
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
                    List<Tag> list = new ArrayList<>(this.readSafely && listLength > 64 ? 64 : listLength);

                    for (int i = 0; i < listLength; ++i) {
                        list.add(this.deserialize(typeId, maxDepth - 1));
                    }
                    return new ListTag<>(typeId, list);
                case Tag.TAG_Int_Array:
                    arraySize = this.readInt();
                    if (this.readSafely && arraySize > 64) {
                        it.unimi.dsi.fastutil.ints.IntArrayList intList = new it.unimi.dsi.fastutil.ints.IntArrayList(64);
                        for (int i = 0; i < arraySize; i++) {
                            intList.add(this.readInt());
                        }
                        return new IntArrayTag("", intList.toIntArray());
                    } else {
                        int[] ints = new int[arraySize];
                        for (int i = 0; i < arraySize; ++i) {
                            ints[i] = this.readInt();
                        }
                        return new IntArrayTag("", ints);
                    }
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
