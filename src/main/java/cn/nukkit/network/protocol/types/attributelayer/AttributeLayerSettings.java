package cn.nukkit.network.protocol.types.attributelayer;

public class AttributeLayerSettings {

    public int priority;
    public Weight weight;
    public boolean enabled;
    public boolean transitionsPaused;

    public AttributeLayerSettings(int priority, Weight weight, boolean enabled, boolean transitionsPaused) {
        this.priority = priority;
        this.weight = weight;
        this.enabled = enabled;
        this.transitionsPaused = transitionsPaused;
    }

    public interface Weight {
    }

    public static class FloatWeight implements Weight {
        public float value;

        public FloatWeight(float value) {
            this.value = value;
        }
    }

    public static class StringWeight implements Weight {
        public String value;

        public StringWeight(String value) {
            this.value = value;
        }
    }
}
