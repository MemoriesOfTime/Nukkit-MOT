package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockJungleSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemJungleSign extends ItemSign {

    public ItemJungleSign() {
        this(0, 1);
    }

    public ItemJungleSign(Integer meta) {
        this(meta, 1);
    }

    public ItemJungleSign(Integer meta, int count) {
        super(JUNGLE_SIGN, meta, count, "Jungle Sign", new BlockJungleSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_9_0;
    }
}
