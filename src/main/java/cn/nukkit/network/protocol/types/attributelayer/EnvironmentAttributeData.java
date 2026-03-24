package cn.nukkit.network.protocol.types.attributelayer;

public class EnvironmentAttributeData {

    public String attributeName;
    public AttributeData from;
    public AttributeData attribute;
    public AttributeData to;
    public int currentTransitionTicks;
    public int totalTransitionTicks;
    public CameraEase easing;

    public EnvironmentAttributeData(String attributeName, AttributeData from, AttributeData attribute, AttributeData to, int currentTransitionTicks, int totalTransitionTicks, CameraEase easing) {
        this.attributeName = attributeName;
        this.from = from;
        this.attribute = attribute;
        this.to = to;
        this.currentTransitionTicks = currentTransitionTicks;
        this.totalTransitionTicks = totalTransitionTicks;
        this.easing = easing;
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
}
