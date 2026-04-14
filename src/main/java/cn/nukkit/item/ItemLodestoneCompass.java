package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemLodestoneCompass extends Item {

    public ItemLodestoneCompass() {
        this(0, 1);
    }

    public ItemLodestoneCompass(Integer meta) {
        this(meta, 1);
    }

    public ItemLodestoneCompass(Integer meta, int count) {
        super(LODESTONE_COMPASS, meta, count, "Lodestone Compass");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_16_0;
    }
}
