package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockBirchSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemBirchSign extends ItemSign {

    public ItemBirchSign() {
        this(0, 1);
    }

    public ItemBirchSign(Integer meta) {
        this(meta, 1);
    }

    public ItemBirchSign(Integer meta, int count) {
        super(BIRCH_SIGN, meta, count, "Birch Sign", new BlockBirchSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_9_0;
    }
}
