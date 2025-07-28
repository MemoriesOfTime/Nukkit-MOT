package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemPhantomMembrane extends Item {

    public ItemPhantomMembrane() {
        this(0, 1);
    }

    public ItemPhantomMembrane(Integer meta) {
        this(meta, 1);
    }

    public ItemPhantomMembrane(Integer meta, int count) {
        super(PHANTOM_MEMBRANE, meta, count, "Phantom Membrane");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_6_0;
    }
}
