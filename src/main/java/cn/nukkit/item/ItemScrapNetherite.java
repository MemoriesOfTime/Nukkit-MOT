package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemScrapNetherite extends Item {

    public ItemScrapNetherite() {
        this(0, 1);
    }

    public ItemScrapNetherite(Integer meta) {
        this(meta, 1);
    }

    public ItemScrapNetherite(Integer meta, int count) {
        super(NETHERITE_SCRAP, 0, count, "Netherite Scrap");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
