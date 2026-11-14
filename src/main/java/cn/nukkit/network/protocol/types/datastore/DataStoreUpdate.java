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
    /**
     * v2259 起以单个变量名替代 dataStore/property/path；为空时按
     * {@code dataStoreName + ':' + property + ('/' + path)} 生成。
     * <p>
     * Since v2259 a single variable name replaces dataStore/property/path; when unset it is derived as
     * {@code dataStoreName + ':' + property + ('/' + path)}.
     *
     * @since v2259 1.26.70
     */
    private String variableName;

    @Override
    public Type getType() {
        return Type.UPDATE;
    }

    public DataStorePropertyType getPropertyType() {
        return type;
    }

    public String resolveVariableName() {
        if (this.variableName != null) {
            return this.variableName;
        }
        StringBuilder sb = new StringBuilder();
        if (this.dataStoreName != null) {
            sb.append(this.dataStoreName);
        }
        if (this.property != null) {
            sb.append(':').append(this.property);
        }
        if (this.path != null && !this.path.isEmpty()) {
            sb.append('/').append(this.path);
        }
        return sb.toString();
    }
}
