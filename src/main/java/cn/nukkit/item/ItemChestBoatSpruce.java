package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatSpruce extends ItemChestBoatBase {
    public ItemChestBoatSpruce() {
        this(0, 1);
    }

    public ItemChestBoatSpruce(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatSpruce(Integer meta, int count) {
        this(SPRUCE_CHEST_BOAT, meta, count, "Spruce Chest Boat");
    }

    protected ItemChestBoatSpruce(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 1;
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0_29;
    }
}
