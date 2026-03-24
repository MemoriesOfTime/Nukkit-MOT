package cn.nukkit.network.protocol.types.attributelayer;

import java.util.List;

public class RemoveEnvironmentAttributesData implements AttributeLayerSyncPayload {

    public String layerName;
    public int dimension;
    public List<String> attributes;

    public RemoveEnvironmentAttributesData(String layerName, int dimension, List<String> attributes) {
        this.layerName = layerName;
        this.dimension = dimension;
        this.attributes = attributes;
    }
}
