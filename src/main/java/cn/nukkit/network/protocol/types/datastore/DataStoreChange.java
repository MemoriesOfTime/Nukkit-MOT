package cn.nukkit.network.protocol.types.datastore;

import lombok.Data;

@Data
public class DataStoreChange implements DataStoreAction {

    private String dataStoreName;
    private String property;
    private Object newValue;
    private int updateCount;
    /**
     * v2259 起以单个变量名替代 dataStore/property；为空时按
     * {@code dataStoreName + ':' + property} 生成。
     * <p>
     * Since v2259 a single variable name replaces dataStore/property; when unset it is derived as
     * {@code dataStoreName + ':' + property}.
     *
     * @since v2259 1.26.70
     */
    private String variableName;

    @Override
    public Type getType() {
        return Type.CHANGE;
    }

    public String resolveVariableName() {
        if (this.variableName != null) {
            return this.variableName;
        }
        if (this.dataStoreName == null) {
            return "";
        }
        return this.property == null ? this.dataStoreName : this.dataStoreName + ':' + this.property;
    }
}
