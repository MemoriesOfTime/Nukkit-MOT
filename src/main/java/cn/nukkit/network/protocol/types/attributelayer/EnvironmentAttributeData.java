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
        IN_OUT_CUBIC("in_out_cubic");

        private final String serializeName;

        CameraEase(String serializeName) {
            this.serializeName = serializeName;
        }

        public String getSerializeName() {
            return serializeName;
        }

        public static CameraEase fromName(String name) {
            for (CameraEase ease : values()) {
                if (ease.serializeName.equals(name)) {
                    return ease;
                }
            }
            return LINEAR;
        }
    }

    /**
     * v2192 噪声对齐（当前仅一个枚举值）/ noise alignment introduced in v2192 (single enum value for now)
     */
    public record NoiseAlignment(Type type, int value) {

        public enum Type {
            MIN_LOCAL_TRANSITION_END
        }
    }
}
