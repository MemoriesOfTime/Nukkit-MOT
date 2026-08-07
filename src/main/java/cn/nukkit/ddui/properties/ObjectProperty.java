package cn.nukkit.ddui.properties;

import cn.nukkit.network.protocol.types.datastore.DataStorePropertyType;

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

    public Map<String, Object> toChangeValue() {
        Map<String, Object> children = new LinkedHashMap<>();
        for (Map.Entry<String, DataDrivenProperty<?, ?>> entry : this.value.entrySet()) {
            children.put(entry.getKey(), convertToRawValue(entry.getValue()));
        }
        return children;
    }

    private static Object convertToRawValue(DataDrivenProperty<?, ?> prop) {
        if (prop instanceof ObjectProperty<?> obj) {
            return obj.toChangeValue();
        } else if (prop instanceof BooleanProperty bp) {
            return bp.getValue();
        } else if (prop instanceof LongProperty lp) {
            return lp.getValue();
        } else if (prop instanceof StringProperty sp) {
            return sp.getValue();
        } else {
            return String.valueOf(prop.getValue());
        }
    }
}
