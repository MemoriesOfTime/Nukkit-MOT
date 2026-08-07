package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemBannerPatternGuster extends StringItemBase {
    public ItemBannerPatternGuster() {
        super(GUSTER_BANNER_PATTERN, "Guster Banner Pattern");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }
}