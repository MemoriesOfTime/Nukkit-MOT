package cn.nukkit.network.protocol.types.attributelayer;

public class ColorAttributeData implements AttributeData {

    public Color255RGBA value;
    public Operation operation;

    public ColorAttributeData(Color255RGBA value, Operation operation) {
        this.value = value;
        this.operation = operation;
    }

    public interface Color255RGBA {
    }

    public static class StringColor implements Color255RGBA {
        public String value;

        public StringColor(String value) {
            this.value = value;
        }
    }

    public static class ArrayColor implements Color255RGBA {
        public int[] value;

        public ArrayColor(int[] value) {
            this.value = value;
        }
    }

    public enum Operation {
        OVERRIDE, ALPHA_BLEND, ADD, SUBTRACT, MULTIPLY
    }
}
