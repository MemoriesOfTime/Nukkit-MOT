package cn.nukkit.nbt.tag;

import java.util.Objects;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public sealed abstract class NumberTag<T extends Number> extends Tag permits ByteTag, ShortTag, IntTag, LongTag, FloatTag, DoubleTag {

    protected NumberTag(String name) {
        super(name);
    }

    public abstract T getData();

    public abstract void setData(T data);

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), getData());
    }
}
