package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemDoorBamboo extends StringItemBase {

    public ItemDoorBamboo() {
        super(ItemNamespaceId.BAMBOO_DOOR_NAMESPACE_ID, "Bamboo Door");
        block = Block.get(Block.BAMBOO_DOOR);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_50;
    }
}
