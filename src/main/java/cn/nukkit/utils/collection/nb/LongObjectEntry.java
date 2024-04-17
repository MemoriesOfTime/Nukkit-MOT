package cn.nukkit.utils.collection.nb;

import java.util.Map;

public interface LongObjectEntry<V> extends Map.Entry<Long, V> {
    @Override
    @Deprecated
    default Long getKey() {
        return getLongKey();
    }

    long getLongKey();
}
