package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordCreator extends ItemRecord implements StringItem {
    public ItemRecordCreator() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getSoundId() {
        return "record.creator";
    }

    @Override
    public String getNamespaceId() {
        return "minecraft:music_disc_creator";
    }

    @Override
    public String getNamespaceId(int protocolId) {
        return MUSIC_DISC_CREATOR;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}
