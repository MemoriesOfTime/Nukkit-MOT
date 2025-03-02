package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemRecordOtherside extends ItemRecord {

    public ItemRecordOtherside() {
        this(0, 1);
    }

    public ItemRecordOtherside(Integer meta) {
        this(meta, 1);
    }

    public ItemRecordOtherside(Integer meta, int count) {
        super(RECORD_OTHERSIDE, meta, count);
    }

    @Override
    public String getSoundId() {
        return "record.otherside";
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return  protocolId >= ProtocolInfo.v1_18_10_26;
    }
}