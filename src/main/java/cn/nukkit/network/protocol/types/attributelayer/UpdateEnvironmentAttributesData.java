package cn.nukkit.network.protocol.types.attributelayer;

import java.util.List;

public class UpdateEnvironmentAttributesData implements AttributeLayerSyncPayload {

    public String layerName;
    public int dimension;
    public List<EnvironmentAttributeData> attributes;

    public UpdateEnvironmentAttributesData(String layerName, int dimension, List<EnvironmentAttributeData> attributes) {
        this.layerName = layerName;
        this.dimension = dimension;
        this.attributes = attributes;
    }
}
