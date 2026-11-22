package cn.nukkit.nbt.tag;

import cn.nukkit.nbt.stream.NBTInputStream;
import cn.nukkit.nbt.stream.NBTOutputStream;

import java.io.IOException;
import java.util.Arrays;
import java.util.Objects;

public class LongArrayTag extends Tag {

    public long[] data;

    public LongArrayTag(String name) {
        super(name);
    }

    public LongArrayTag(long[] data) {
        this("", data);
    }

    public LongArrayTag(String name, long[] data) {
        super(name);
        this.data = data;
    }

    @Override
    void write(NBTOutputStream dos) throws IOException {
        dos.writeLongArray(data);
    }

    @Override
    void load(NBTInputStream dis, int nested) throws IOException {
        data = dis.readLongArray(dis.readInt());
    }

    public long[] getData() {
        return data;
    }

    @Override
    public long[] parseValue() {
        return this.data;
    }

    @Override
    public byte getId() {
        return TAG_Long_Array;
    }

    @Override
    public String toString() {
        return "LongArrayTag " + this.getName() + " [" + data.length + " longs]";
    }

    @Override
    public String toSNBT() {
        return toSNBT(false);
    }

    @Override
    public String toSNBT(int space) {
        return toSNBT(true);
    }

    private String toSNBT(boolean padded) {
        StringBuilder builder = new StringBuilder(padded ? "[L; " : "[L;");
        for (int i = 0; i < this.data.length; i++) {
            if (i > 0) {
                builder.append(padded ? ", " : ",");
            }
            builder.append(data[i]).append('L');
        }
        return builder.append(']').toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (super.equals(obj)) {
            LongArrayTag longArrayTag = (LongArrayTag) obj;
            return ((data == null && longArrayTag.data == null) || (data != null && Arrays.equals(data, longArrayTag.data)));
        }
        return false;
    }

    @Override
    public int hashCode() {
        // Tag.hashCode() 对数组 parseValue 取身份哈希，这里用内容哈希保持 equals 契约。
        // Tag.hashCode() identity-hashes parseValue(), which is wrong for arrays.
        return Objects.hash(getId(), getName(), Arrays.hashCode(data));
    }

    @Override
    public Tag copy() {
        long[] cp = new long[data.length];
        System.arraycopy(data, 0, cp, 0, data.length);
        return new LongArrayTag(getName(), cp);
    }
}
