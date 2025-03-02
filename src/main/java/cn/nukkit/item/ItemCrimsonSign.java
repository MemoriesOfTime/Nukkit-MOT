package cn.nukkit.item;


import cn.nukkit.block.BlockCrimsonSignPost;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemCrimsonSign extends ItemSign {

    public ItemCrimsonSign() {
        this(0, 1);
    }

    public ItemCrimsonSign(Integer meta) {
        this(meta, 1);
    }

    public ItemCrimsonSign(Integer meta, int count) {
        super(CRIMSON_SIGN, meta, count, "Crimson Sign", new BlockCrimsonSignPost());
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
