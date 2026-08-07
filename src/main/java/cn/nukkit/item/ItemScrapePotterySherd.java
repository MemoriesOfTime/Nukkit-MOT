package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemScrapePotterySherd extends ItemPotterySherd {

    public ItemScrapePotterySherd() {
        super(SCRAPE_POTTERY_SHERD, "Scrape Pottery Sherd");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }
}