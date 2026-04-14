package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemFlowPotterySherd extends ItemPotterySherd {

    public ItemFlowPotterySherd() {
        super(FLOW_POTTERY_SHERD, "Flow Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }
}