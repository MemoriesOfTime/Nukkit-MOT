package cn.nukkit.item;

import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorCherry extends StringItemBase {

    public ItemDoorCherry() {
        super(CHERRY_DOOR, "Cherry Door");
        block = Block.get(CHERRY_DOOR_BLOCK);
    }

    @Override
    public boolean isSupportedOn(int protocolId) {
        return protocolId >= ProtocolInfo.v1_20_0;
    }
}
