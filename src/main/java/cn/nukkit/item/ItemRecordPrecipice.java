package cn.nukkit.item;

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
    public String getNamespaceId(int protocolId) {
        return "minecraft:music_disc_precipice";
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}