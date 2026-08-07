package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author glorydark
 */
public class ItemBrush extends StringItemToolBase {

    public ItemBrush() {
        super("minecraft:brush", "Brush");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_20_0;
    }

    @Override
    public int getMaxDurability() {
        return ItemTool.DURABILITY_BRUSH;
    }
}