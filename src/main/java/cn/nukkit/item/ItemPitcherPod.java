package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemPitcherPod extends StringItemBase {

    public ItemPitcherPod() {
        super(PITCHER_POD, "Pitcher Pod");
        this.block = Block.get(Block.PITCHER_CROP);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_0;
    }
}
