package cn.nukkit.item;

import cn.nukkit.block.BlockCampfire;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemCampfire extends Item {
    public ItemCampfire() {
        this(0, 1);
    }

    public ItemCampfire(final Integer meta) {
        this(meta, 1);
    }

    public ItemCampfire(final Integer meta, final int count) {
        super(CAMPFIRE, meta, count, "Campfire");
        block = new BlockCampfire();
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_10_0;
    }
}
