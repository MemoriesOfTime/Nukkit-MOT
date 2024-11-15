package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemBannerPatternGuster extends StringItemBase {
    public ItemBannerPatternGuster() {
        super("minecraft:guster_banner_pattern", "Guster Banner Pattern");
    }

    @Override
    public int getMaxStackSize() {
        return 1;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}