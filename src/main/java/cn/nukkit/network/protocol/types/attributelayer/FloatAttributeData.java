package cn.nukkit.network.protocol.types.attributelayer;

public class FloatAttributeData implements AttributeData {

    public float value;
    public Operation operation;
    public Float constraintMin;
    public Float constraintMax;

    public FloatAttributeData(float value, Operation operation, Float constraintMin, Float constraintMax) {
        this.value = value;
        this.operation = operation;
        this.constraintMin = constraintMin;
        this.constraintMax = constraintMax;
    }

    public enum Operation {
        OVERRIDE, ALPHA_BLEND, ADD, SUBTRACT, MULTIPLY, MINIMUM, MAXIMUM
    }
}
