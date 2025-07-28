package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordPrecipice extends ItemRecord implements StringItem {
    public ItemRecordPrecipice() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getSoundId() {
        return "record.precipice";
    }

    @Override
    public String getNamespaceId() {
        return "minecraft:music_disc_precipice";
    }

    @Override
    public String getNamespaceId(GameVersion protocolId) {
        return MUSIC_DISC_PRECIPICE;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}