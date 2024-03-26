package cn.nukkit.item;

import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemNetherSprouts extends Item {

    public ItemNetherSprouts() {
        this(0, 1);
    }

    public ItemNetherSprouts(Integer meta) {
        this(meta, 1);
    }

    public ItemNetherSprouts(Integer meta, int count) {
        super(NETHER_SPROUTS, 0, count, "Nether Sprouts");
        block = Block.get(NETHER_SPROUTS_BLOCK);
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_16_0;
    }
}
