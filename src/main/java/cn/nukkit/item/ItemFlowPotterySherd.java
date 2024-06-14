package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemFlowPotterySherd extends ItemPotterySherd {

    public ItemFlowPotterySherd() {
        super("minecraft:flow_pottery_sherd", "Flow Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}