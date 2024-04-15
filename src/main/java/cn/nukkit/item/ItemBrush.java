package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author glorydark
 */
public class ItemBrush extends StringItemBase {

    public ItemBrush() {
        super("minecraft:brush", "Brush");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_20_0;
    }
}