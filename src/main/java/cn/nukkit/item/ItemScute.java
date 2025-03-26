package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemScute extends Item {

    public ItemScute() {
        this(0, 1);
    }

    public ItemScute(Integer meta) {
        this(meta, 1);
    }

    public ItemScute(Integer meta, int count) {
        super(SCUTE, meta, count, "Scute");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_5_0;
    }
}
