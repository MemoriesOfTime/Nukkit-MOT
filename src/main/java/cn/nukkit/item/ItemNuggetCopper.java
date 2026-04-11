package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemNuggetCopper extends StringItemBase {

    public ItemNuggetCopper() {
        super(COPPER_NUGGET, "Copper Nugget");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_100;
    }
}
