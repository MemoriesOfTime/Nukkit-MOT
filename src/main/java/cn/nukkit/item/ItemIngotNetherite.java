package cn.nukkit.item;

import cn.nukkit.item.trim.ItemTrimMaterialType;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemIngotNetherite extends Item implements ItemTrimMaterial {

    public ItemIngotNetherite() {
        this(0, 1);
    }

    public ItemIngotNetherite(Integer meta) {
        this(meta, 1);
    }

    public ItemIngotNetherite(Integer meta, int count) {
        super(NETHERITE_INGOT, 0, count, "Netherite Ingot");
    }

    @Override
    public ItemTrimMaterialType getMaterial() {
        return ItemTrimMaterialType.MATERIAL_NETHERITE;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
