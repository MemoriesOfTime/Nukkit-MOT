package cn.nukkit.network.protocol.types.datastore;

import lombok.Data;

@Data
public class DataStoreRemoval implements DataStoreAction {

    private String dataStoreName;
    /**
     * v2259 起以单个变量名替代 dataStore 名称；为空时回退到 {@link #dataStoreName}。
     * <p>
     * Since v2259 a single variable name replaces the data store name; falls back to
     * {@link #dataStoreName} when unset.
     *
     * @since v2259 1.26.70
     */
    private String variableName;

    @Override
    public Type getType() {
        return Type.REMOVAL;
    }

    public String resolveVariableName() {
        return this.variableName != null ? this.variableName : (this.dataStoreName != null ? this.dataStoreName : "");
    }
}
