package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemIngotCopper extends StringItemBase {

    public ItemIngotCopper() {
        super("minecraft:copper_ingot", "Copper Ingot");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_17_0;
    }
}
