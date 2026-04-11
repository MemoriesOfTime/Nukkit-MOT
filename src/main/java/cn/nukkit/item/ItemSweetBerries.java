package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockSweetBerryBush;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemSweetBerries extends ItemEdible {

    public ItemSweetBerries() {
        this(0, 1);
    }

    public ItemSweetBerries(Integer meta) {
        this(meta, 1);
    }

    public ItemSweetBerries(Integer meta, int count) {
        super(SWEET_BERRIES, meta, count, "Sweet Berries");
        this.block = new BlockSweetBerryBush();
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_11_0;
    }
}
