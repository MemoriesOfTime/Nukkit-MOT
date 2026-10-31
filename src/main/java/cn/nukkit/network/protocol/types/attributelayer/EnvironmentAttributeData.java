package cn.nukkit.network.protocol.types.attributelayer;

public class EnvironmentAttributeData {

    public String attributeName;
    public AttributeData from;
    public AttributeData attribute;
    public AttributeData to;
    public int currentTransitionTicks;
    public int totalTransitionTicks;
    public CameraEase easing;
    public int localTransitionTicks;
    public boolean noiseTransition;
    public String clockName;
    public String noiseName;
    /**
     * v2192 尾部新增；服务器不使用噪声过渡，写默认值。
     * <p>
     * Trailing field added in v2192; unused server-side, written as default.
     */
    public NoiseAlignment noiseAlignment;

    public EnvironmentAttributeData(String attributeName, AttributeData from, AttributeData attribute, AttributeData to, int currentTransitionTicks, int totalTransitionTicks, CameraEase easing) {
        this(attributeName, from, attribute, to, currentTransitionTicks, totalTransitionTicks, easing, 0, false);
    }

    public EnvironmentAttributeData(String attributeName, AttributeData from, AttributeData attribute, AttributeData to, int currentTransitionTicks, int totalTransitionTicks, CameraEase easing, int localTransitionTicks, boolean noiseTransition) {
        this.attributeName = attributeName;
        this.from = from;
        this.attribute = attribute;
        this.to = to;
        this.currentTransitionTicks = currentTransitionTicks;
        this.totalTransitionTicks = totalTransitionTicks;
        this.easing = easing;
        this.localTransitionTicks = localTransitionTicks;
        this.noiseTransition = noiseTransition;
    }

    public enum CameraEase {
        LINEAR("linear"),
        SPRING("spring"),
        IN_QUAD("in_quad"),
        OUT_QUAD("out_quad"),
        IN_OUT_QUAD("in_out_quad"),
        IN_CUBIC("in_cubic"),
        OUT_CUBIC("out_cubic"),
        IN_OUT_CUBIC("in_out_cubic"),
        IN_QUART("in_quart"),
        OUT_QUART("out_quart"),
        IN_OUT_QUART("in_out_quart"),
        IN_QUINT("in_quint"),
        OUT_QUINT("out_quint"),
        IN_OUT_QUINT("in_out_quint"),
        IN_SINE("in_sine"),
        OUT_SINE("out_sine"),
        IN_OUT_SINE("in_out_sine"),
        IN_EXPO("in_expo"),
        OUT_EXPO("out_expo"),
        IN_OUT_EXPO("in_out_expo"),
        IN_CIRC("in_circ"),
        OUT_CIRC("out_circ"),
        IN_OUT_CIRC("in_out_circ"),
        IN_BOUNCE("in_bounce"),
        OUT_BOUNCE("out_bounce"),
        IN_OUT_BOUNCE("in_out_bounce"),
        IN_BACK("in_back"),
        OUT_BACK("out_back"),
        IN_OUT_BACK("in_out_back"),
        IN_ELASTIC("in_elastic"),
        OUT_ELASTIC("out_elastic"),
        IN_OUT_ELASTIC("in_out_elastic");

        private static final CameraEase[] VALUES = values();

        private final String serializeName;

        CameraEase(String serializeName) {
            this.serializeName = serializeName;
        }

        public String getSerializeName() {
            return serializeName;
        }

        public static CameraEase fromName(String name) {
            for (CameraEase ease : VALUES) {
                if (ease.serializeName.equals(name)) {
                    return ease;
                }
            }
            return LINEAR;
        }

        public static CameraEase byOrdinal(int ordinal) {
            return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : LINEAR;
        }
    }

    /**
     * v2192 噪声对齐（当前仅一个枚举值）/ noise alignment introduced in v2192 (single enum value for now)
     */
    public record NoiseAlignment(Type type, int value) {

        public enum Type {
            MIN_LOCAL_TRANSITION_END;

            private static final Type[] VALUES = values();

            public static Type byOrdinal(int ordinal) {
                return ordinal >= 0 && ordinal < VALUES.length ? VALUES[ordinal] : MIN_LOCAL_TRANSITION_END;
            }
        }
    }
}
