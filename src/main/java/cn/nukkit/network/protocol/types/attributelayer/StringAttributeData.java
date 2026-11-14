package cn.nukkit.network.protocol.types.attributelayer;

/**
 * 字符串属性值，随环境属性层的联合类型下发。
 * <p>
 * String attribute value carried by the environment attribute layer union.
 *
 * @since v2259 1.26.70
 */
public class StringAttributeData implements AttributeData {

    public String value;
    public Operation operation;

    public StringAttributeData(String value, Operation operation) {
        this.value = value;
        this.operation = operation;
    }

    public enum Operation {
        OVERRIDE
    }
}
