package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDiscFragment5 extends Item {

    public ItemDiscFragment5() {
        this(0, 1);
    }

    public ItemDiscFragment5(Integer meta) {
        this(meta, 1);
    }

    public ItemDiscFragment5(Integer meta, int count) {
        super(DISC_FRAGMENT_5, 0, count, "Disc Fragment");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0;
    }
}
