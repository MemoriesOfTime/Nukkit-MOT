package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemCrafter extends StringItemBase {

    public ItemCrafter() {
        super(ItemNamespaceId.CRAFTER_NAMESPACE_ID, "Crafter");
        block = Block.get(Block.CRAFTER);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_50;
    }
}
