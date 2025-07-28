package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemBannerPatternFlow extends StringItemBase {
    public ItemBannerPatternFlow() {
        super(FLOW_BANNER_PATTERN, "Flow Banner Pattern");
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
