package cn.nukkit.network.protocol.types.attributelayer;

import java.util.List;

public class UpdateAttributeLayersData implements AttributeLayerSyncPayload {

    public List<AttributeLayerData> attributeLayers;

    public UpdateAttributeLayersData(List<AttributeLayerData> attributeLayers) {
        this.attributeLayers = attributeLayers;
    }
}
