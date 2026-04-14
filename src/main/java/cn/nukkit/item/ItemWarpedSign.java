package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockWarpedSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemWarpedSign extends ItemSign {

    public ItemWarpedSign() {
        this(0, 1);
    }

    public ItemWarpedSign(Integer meta) {
        this(meta, 1);
    }

    public ItemWarpedSign(Integer meta, int count) {
        super(WARPED_SIGN, meta, count, "Warped Sign", new BlockWarpedSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_16_0;
    }
}
