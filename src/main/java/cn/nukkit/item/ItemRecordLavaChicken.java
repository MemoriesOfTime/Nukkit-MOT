package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordLavaChicken extends ItemRecord implements StringItem {

    public ItemRecordLavaChicken() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getNamespaceId() {
        return MUSIC_DISC_LAVA_CHICKEN;
    }

    @Override
    public String getNamespaceId(GameVersion protocolId) {
        return MUSIC_DISC_LAVA_CHICKEN;
    }

    @Override
    public String getSoundId() {
        return "record.lava_chicken";
    }

    @Override
    public String getDiscName() {
        return "Hyper Potions - Lava Chicken";
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_93;
    }
}
