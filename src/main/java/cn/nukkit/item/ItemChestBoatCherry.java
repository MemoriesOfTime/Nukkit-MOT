package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestBoatCherry extends ItemChestBoatBase {
    public ItemChestBoatCherry() {
        this(0, 1);
    }

    public ItemChestBoatCherry(Integer meta) {
        this(meta, 1);
    }

    public ItemChestBoatCherry(Integer meta, int count) {
        this(CHERRY_CHEST_BOAT, meta, count, "Cherry Chest Boat");
    }

    protected ItemChestBoatCherry(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 8;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_80;
    }
}
