package cn.nukkit.network.protocol.types.attributelayer;

public class BoolAttributeData implements AttributeData {

    public boolean value;
    public Operation operation;

    public BoolAttributeData(boolean value, Operation operation) {
        this.value = value;
        this.operation = operation;
    }

    public enum Operation {
        OVERRIDE, ALPHA_BLEND, AND, NAND, OR, NOR, XOR, XNOR
    }
}
