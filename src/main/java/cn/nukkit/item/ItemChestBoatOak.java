package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatOak extends ItemChestBoatBase {
    public ItemChestBoatOak() {
        this(0, 1);
    }

    public ItemChestBoatOak(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatOak(Integer meta, int count) {
        this(OAK_CHEST_BOAT, meta, count, "Oak Chest Boat");
    }

    protected ItemChestBoatOak(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 0;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0_29;
    }
}
