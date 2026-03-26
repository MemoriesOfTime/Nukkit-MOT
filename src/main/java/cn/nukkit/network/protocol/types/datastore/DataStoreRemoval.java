package cn.nukkit.network.protocol.types.datastore;

import lombok.Data;

@Data
public class DataStoreRemoval implements DataStoreAction {

    private String dataStoreName;

    @Override
    public Type getType() {
        return Type.REMOVAL;
    }
}
