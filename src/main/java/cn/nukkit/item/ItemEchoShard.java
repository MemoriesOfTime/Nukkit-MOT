package cn.nukkit.item;

import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemEchoShard extends StringItemBase {
    public ItemEchoShard() {
        super("minecraft:echo_shard", "Echo Shard");
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0;
    }
}
