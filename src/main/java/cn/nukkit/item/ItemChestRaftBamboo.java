package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChestRaftBamboo extends ItemChestBoatBase {
    public ItemChestRaftBamboo() {
        this(0, 1);
    }

    public ItemChestRaftBamboo(Integer meta) {
        this(meta, 1);
    }

    public ItemChestRaftBamboo(Integer meta, int count) {
        this(BAMBOO_CHEST_RAFT, meta, count, "Bamboo Chest Raft");
    }

    protected ItemChestRaftBamboo(int id, Integer meta, int count, String name) {
        super(id, meta, count, name);
    }

    @Override
    public int getBoatId() {
        return 7;
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_50;
    }
}
