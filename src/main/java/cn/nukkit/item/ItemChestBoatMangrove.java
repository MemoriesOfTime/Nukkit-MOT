package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatMangrove extends ItemChestBoatBase {
    public ItemChestBoatMangrove() {
        this(0, 1);
    }

    public ItemChestBoatMangrove(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatMangrove(Integer meta, int count) {
        this(MANGROVE_CHEST_BOAT, meta, count, "Mangrove Chest Boat");
    }

    protected ItemChestBoatMangrove(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 6;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0_29;
    }
}
