package cn.nukkit.item;

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
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_17_0;
    }
}
