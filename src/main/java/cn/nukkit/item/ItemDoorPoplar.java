package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.block.BlockID;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorPoplar extends StringItemBase {

    public ItemDoorPoplar() {
        super(POPLAR_DOOR_NAMESPACE_ID, "Poplar Door");
        block = Block.get(BlockID.POPLAR_DOOR);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_26_40;
    }
}
