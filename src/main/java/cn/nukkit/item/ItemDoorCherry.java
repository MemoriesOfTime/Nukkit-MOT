package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorCherry extends StringItemBase {

    public ItemDoorCherry() {
        super(CHERRY_DOOR, "Cherry Door");
        block = Block.get(CHERRY_DOOR_BLOCK);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_0;
    }
}
