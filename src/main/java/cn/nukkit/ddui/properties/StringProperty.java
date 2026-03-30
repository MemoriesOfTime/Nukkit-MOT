package cn.nukkit.ddui.properties;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;

public class StringProperty extends DataDrivenProperty<String, String> {

    @Override
    public DataStorePropertyType getType() {
        return DataStorePropertyType.STRING;
    }

    public StringProperty(String name, String value) {
        this(name, value, null);
    }

    public StringProperty(String name, String value, ObjectProperty parent) {
        super(name, value, parent);
    }
}
