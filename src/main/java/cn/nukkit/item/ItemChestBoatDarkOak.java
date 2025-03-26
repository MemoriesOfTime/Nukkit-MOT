package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatDarkOak extends ItemChestBoatBase {
    public ItemChestBoatDarkOak() {
        this(0, 1);
    }

    public ItemChestBoatDarkOak(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatDarkOak(Integer meta, int count) {
        this(DARK_OAK_CHEST_BOAT, meta, count, "Dark Oak Chest Boat");
    }

    protected ItemChestBoatDarkOak(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 5;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0_29;
    }
}
