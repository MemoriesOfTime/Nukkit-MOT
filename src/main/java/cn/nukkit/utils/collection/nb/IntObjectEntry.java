package cn.nukkit.utils.collection.nb;

import java.util.Map;

public interface IntObjectEntry<V> extends Map.Entry<Integer, V> {
    @Deprecated
    @Override
    default Integer getKey() {
        return getIntKey();
    }

    int getIntKey();
}
