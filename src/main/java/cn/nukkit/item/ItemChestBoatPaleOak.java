package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatPaleOak extends ItemChestBoatBase {

    public ItemChestBoatPaleOak() {
        this(0, 1);
    }

    public ItemChestBoatPaleOak(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatPaleOak(Integer meta, int count) {
        super(PALE_OAK_CHEST_BOAT, meta, count, "Pale Oak Boat with Chest");
    }

    @Override
    public int getBoatId() {
        return 9;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_21_50;
    }
}