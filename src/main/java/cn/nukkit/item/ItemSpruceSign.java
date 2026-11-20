package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockSpruceSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemSpruceSign extends ItemSign {

    public ItemSpruceSign() {
        this(0, 1);
    }

    public ItemSpruceSign(Integer meta) {
        this(meta, 1);
    }

    public ItemSpruceSign(Integer meta, int count) {
        super(SPRUCE_SIGN, meta, count, "Spruce Sign", new BlockSpruceSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_9_0;
    }
}
