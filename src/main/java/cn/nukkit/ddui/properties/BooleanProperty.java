package cn.nukkit.ddui.properties;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;

public class BooleanProperty extends DataDrivenProperty<Boolean, Boolean> {

    @Override
    public DataStorePropertyType getType() {
        return DataStorePropertyType.BOOLEAN;
    }

    public BooleanProperty(String name, boolean value) {
        this(name, value, null);
    }

    public BooleanProperty(String name, boolean value, ObjectProperty parent) {
        super(name, value, parent);
    }
}
