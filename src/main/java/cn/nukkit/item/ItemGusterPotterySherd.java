package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemGusterPotterySherd extends ItemPotterySherd {

    public ItemGusterPotterySherd() {
        super("minecraft:guster_pottery_sherd", "Guster Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}