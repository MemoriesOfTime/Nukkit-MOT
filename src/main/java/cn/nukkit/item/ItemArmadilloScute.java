package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemArmadilloScute extends StringItemBase {

    public ItemArmadilloScute() {
        super(ARMADILLO_SCUTE, "Armadillo Scute");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_60;
    }
}
