package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author PetteriM1
 */
public class ItemHeartOfTheSea extends Item {

    public ItemHeartOfTheSea() {
        this(0, 1);
    }

    public ItemHeartOfTheSea(Integer meta) {
        this(meta, 1);
    }

    public ItemHeartOfTheSea(Integer meta, int count) {
        super(HEART_OF_THE_SEA, meta, count, "Heart Of The Sea");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_4_0;
    }
}
