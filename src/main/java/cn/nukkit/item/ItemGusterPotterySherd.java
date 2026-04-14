package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemGusterPotterySherd extends ItemPotterySherd {

    public ItemGusterPotterySherd() {
        super(GUSTER_POTTERY_SHERD, "Guster Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }
}