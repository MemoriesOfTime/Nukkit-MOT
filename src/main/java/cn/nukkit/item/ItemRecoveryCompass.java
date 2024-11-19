package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecoveryCompass extends StringItemBase {
    public ItemRecoveryCompass() {
        super(RECOVERY_COMPASS, "Recovery Compass");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0;
    }
}
