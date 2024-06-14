package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemScrapePotterySherd extends ItemPotterySherd {

    public ItemScrapePotterySherd() {
        super("minecraft:scrape_pottery_sherd", "Scrape Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}