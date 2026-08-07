package cn.nukkit.network.protocol.types.camera;

import java.util.HashMap;
import java.util.Map;

public enum CameraSplineType {

    CATMULL_ROM("catmullrom"),
    LINEAR("linear");

    private static final Map<String, CameraSplineType> serializeNames = new HashMap<>();
    static {
        for (CameraSplineType value : values()) {
            serializeNames.put(value.getSerializeName(), value);
        }
    }

    private final String serializeName;

    CameraSplineType(String serializeName) {
        this.serializeName = serializeName;
    }

    public String getSerializeName() {
        return this.serializeName;
    }

    public static CameraSplineType fromName(String serializeName) {
        return serializeNames.get(serializeName);
    }
}
