package cn.nukkit.ddui.properties;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;
import cn.nukkit.network.protocol.types.datastore.DataStorePropertyValue;

import java.util.LinkedHashMap;
import java.util.Map;

public class ObjectProperty<T> extends DataDrivenProperty<Map<String, DataDrivenProperty<?, ?>>, T> {

    @Override
    public DataStorePropertyType getType() {
        return DataStorePropertyType.OBJECT;
    }

    public ObjectProperty(String name) {
        this(name, null);
    }

    public ObjectProperty(String name, ObjectProperty parent) {
        super(name, new LinkedHashMap<>(), parent);
    }

    public DataDrivenProperty<?, ?> getProperty(String name) {
        return this.value.get(name);
    }

    public void setProperty(DataDrivenProperty<?, ?> property) {
        this.value.put(property.getName(), property);
    }

    public DataStorePropertyValue toPropertyValue() {
        Map<String, DataStorePropertyValue> children = new LinkedHashMap<>();

        for (Map.Entry<String, DataDrivenProperty<?, ?>> entry : this.value.entrySet()) {
            String key = entry.getKey();
            DataDrivenProperty<?, ?> prop = entry.getValue();

            DataStorePropertyValue child = convertProperty(prop);
            children.put(key, child);
        }

        return DataStorePropertyValue.ofObject(children);
    }

    private static DataStorePropertyValue convertProperty(DataDrivenProperty<?, ?> prop) {
        if (prop instanceof ObjectProperty) {
            return ((ObjectProperty<?>) prop).toPropertyValue();
        } else if (prop instanceof BooleanProperty) {
            return DataStorePropertyValue.ofBoolean(((BooleanProperty) prop).getValue());
        } else if (prop instanceof LongProperty) {
            return DataStorePropertyValue.ofLong(((LongProperty) prop).getValue());
        } else if (prop instanceof StringProperty) {
            return DataStorePropertyValue.ofString(((StringProperty) prop).getValue());
        } else {
            return DataStorePropertyValue.ofString(String.valueOf(prop.getValue()));
        }
    }
}
