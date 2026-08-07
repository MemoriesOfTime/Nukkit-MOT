package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatBirch extends ItemChestBoatBase {
    public ItemChestBoatBirch() {
        this(0, 1);
    }

    public ItemChestBoatBirch(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatBirch(Integer meta, int count) {
        this(BIRCH_CHEST_BOAT, meta, count, "Birch Chest Boat");
    }

    protected ItemChestBoatBirch(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 2;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_0_29;
    }
}
