package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.block.Block;
import cn.nukkit.network.protocol.ProtocolInfo;

public class ItemChiseledBookshelf extends StringItemBase {

    public ItemChiseledBookshelf() {
        super(ItemNamespaceId.CHISELED_BOOKSHELF_NAMESPACE_ID, "Chiseled Bookshelf");
        block = Block.get(Block.CHISELED_BOOKSHELF);
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_60;
    }
}
