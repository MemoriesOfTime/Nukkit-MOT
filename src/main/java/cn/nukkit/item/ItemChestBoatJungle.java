package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatJungle extends ItemChestBoatBase {
    public ItemChestBoatJungle() {
        this(0, 1);
    }

    public ItemChestBoatJungle(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatJungle(Integer meta, int count) {
        this(JUNGLE_CHEST_BOAT, meta, count, "Jungle Chest Boat");
    }

    protected ItemChestBoatJungle(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 3;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_0_29;
    }
}