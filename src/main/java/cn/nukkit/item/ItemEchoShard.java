package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemEchoShard extends StringItemBase {
    public ItemEchoShard() {
        super(ECHO_SHARD, "Echo Shard");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_0_29;
    }
}
