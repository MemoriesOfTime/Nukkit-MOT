package cn.nukkit.item;

import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemGlowBerries extends ItemEdible {

    public ItemGlowBerries() {
        this(0, 1);
    }

    public ItemGlowBerries(Integer meta) {
        this(meta, 1);
    }

    public ItemGlowBerries(Integer meta, int count) {
        super(GLOW_BERRIES, 0, count, "Glow Berries");
        this.block = Block.get(CAVE_VINES);
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_17_0;
    }
}
