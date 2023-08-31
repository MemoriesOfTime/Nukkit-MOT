package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author joserobjr
 * @since 2021-06-12
 */
public class ItemRawIron extends ItemRawMaterial {

    public ItemRawIron() {
        super("minecraft:raw_iron", "Raw Iron");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_17_0;
    }
}
