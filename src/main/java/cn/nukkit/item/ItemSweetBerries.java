package cn.nukkit.item;

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
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_11_0;
    }
}
