package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author MagicDroidX
 * Nukkit Project
 */
public class ItemBreezeRod extends StringItemBase {

    public ItemBreezeRod() {
        super(BREEZE_ROD, "Breeze Rod");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_0;
    }
}
