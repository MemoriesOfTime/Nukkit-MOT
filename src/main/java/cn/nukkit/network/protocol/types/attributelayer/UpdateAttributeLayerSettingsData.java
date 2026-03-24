package cn.nukkit.network.protocol.types.attributelayer;

public class UpdateAttributeLayerSettingsData implements AttributeLayerSyncPayload {

    public String layerName;
    public int dimension;
    public AttributeLayerSettings settings;

    public UpdateAttributeLayerSettingsData(String layerName, int dimension, AttributeLayerSettings settings) {
        this.layerName = layerName;
        this.dimension = dimension;
        this.settings = settings;
    }
}
