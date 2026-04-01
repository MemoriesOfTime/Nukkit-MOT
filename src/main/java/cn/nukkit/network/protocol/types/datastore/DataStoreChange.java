package cn.nukkit.network.protocol.types.datastore;

import lombok.Data;

@Data
public class DataStoreChange implements DataStoreAction {

    private String dataStoreName;
    private String property;
    private Object newValue;
    private int updateCount;

    @Override
    public Type getType() {
        return Type.CHANGE;
    }
}
