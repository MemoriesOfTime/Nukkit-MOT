package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemTorchflowerSeeds extends StringItemBase {

    public ItemTorchflowerSeeds() {
        super(TORCHFLOWER_SEEDS, "Torchflower Seeds");
        this.block = Block.get(Block.TORCHFLOWER_CROP);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_70;
    }
}
