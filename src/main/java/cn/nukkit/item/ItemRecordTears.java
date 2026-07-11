package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordTears extends ItemRecord implements StringItem {

    public ItemRecordTears() {
        super(STRING_IDENTIFIED_ITEM, 0, 1);
    }

    @Override
    public String getNamespaceId() {
        return MUSIC_DISC_TEARS;
    }

    @Override
    public String getNamespaceId(GameVersion protocolId) {
        return MUSIC_DISC_TEARS;
    }

    @Override
    public String getSoundId() {
        return "record.tears";
    }

    @Override
    public String getDiscName() {
        return "Amos Roddy - Tears";
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_90;
    }
}
