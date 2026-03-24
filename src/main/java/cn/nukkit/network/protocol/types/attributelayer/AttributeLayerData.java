package cn.nukkit.network.protocol.types.attributelayer;

import java.util.List;

public class AttributeLayerData {

    public String layerName;
    public int dimension;
    public AttributeLayerSettings settings;
    public List<EnvironmentAttributeData> attributes;

    public AttributeLayerData(String layerName, int dimension, AttributeLayerSettings settings, List<EnvironmentAttributeData> attributes) {
        this.layerName = layerName;
        this.dimension = dimension;
        this.settings = settings;
        this.attributes = attributes;
    }
}
