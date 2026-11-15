package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockDarkOakSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDarkOakSign extends ItemSign {

    public ItemDarkOakSign() {
        this(0, 1);
    }

    public ItemDarkOakSign(Integer meta) {
        this(meta, 1);
    }

    public ItemDarkOakSign(Integer meta, int count) {
        super(DARKOAK_SIGN, meta, count, "Dark Oak Sign", new BlockDarkOakSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_9_0;
    }
}
