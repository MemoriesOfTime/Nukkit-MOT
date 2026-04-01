package cn.nukkit.network.protocol.types.datastore;

import lombok.Data;

@Data
public class DataStoreUpdate implements DataStoreAction {

    private String dataStoreName;
    private String property;
    private String path;
    private DataStorePropertyType type;
    private Object data;
    private int propertyUpdateCount;
    private int pathUpdateCount;

    @Override
    public Type getType() {
        return Type.UPDATE;
    }

    public DataStorePropertyType getPropertyType() {
        return type;
    }
}
