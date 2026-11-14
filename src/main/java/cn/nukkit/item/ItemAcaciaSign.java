package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.BlockAcaciaSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemAcaciaSign extends ItemSign {

    public ItemAcaciaSign() {
        this(0, 1);
    }

    public ItemAcaciaSign(Integer meta) {
        this(meta, 1);
    }

    public ItemAcaciaSign(Integer meta, int count) {
        super(ACACIA_SIGN, meta, count, "Acacia Sign", new BlockAcaciaSignPost());
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_9_0;
    }
}
