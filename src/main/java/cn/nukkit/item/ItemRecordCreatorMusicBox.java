package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordCreatorMusicBox extends ItemRecord implements StringItem {
    public ItemRecordCreatorMusicBox() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getSoundId() {
        return "record.creator_music_box";
    }

    @Override
    public String getNamespaceId() {
        return "minecraft:music_disc_creator_music_box";
    }

    @Override
    public String getNamespaceId(int protocolId) {
        return MUSIC_DISC_CREATOR_BOX;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_21_0;
    }
}