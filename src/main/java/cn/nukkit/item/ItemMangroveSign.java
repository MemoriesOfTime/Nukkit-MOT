package cn.nukkit.item;

import cn.nukkit.block.BlockMangroveSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemMangroveSign extends ItemSign {
    public ItemMangroveSign() {
        this(0, 1);
    }

    public ItemMangroveSign(Integer meta) {
        this(meta, 1);
    }

    public ItemMangroveSign(Integer meta, int count) {
        super(MANGROVE_SIGN, meta, count, "Mangrove Sign", new BlockMangroveSignPost());
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_19_0;
    }
}
