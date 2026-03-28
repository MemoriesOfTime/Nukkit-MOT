package cn.nukkit.ddui.properties;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;

public class LongProperty extends DataDrivenProperty<Long, Long> {

    @Override
    public DataStorePropertyType getType() {
        return DataStorePropertyType.INT64;
    }

    public LongProperty(String name, long value) {
        this(name, value, null);
    }

    public LongProperty(String name, long value, ObjectProperty parent) {
        super(name, value, parent);
    }
}
