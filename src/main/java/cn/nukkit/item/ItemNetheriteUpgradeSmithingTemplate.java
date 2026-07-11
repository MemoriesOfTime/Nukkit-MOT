package cn.nukkit.item;

import cn.nukkit.GameVersion;
import cn.nukkit.network.protocol.ProtocolInfo;

/**
 * @author Glorydark
 */
public class ItemNetheriteUpgradeSmithingTemplate extends StringItemBase {

    public ItemNetheriteUpgradeSmithingTemplate() {
        super(NETHERITE_UPGRADE_SMITHING_TEMPLATE, "Netherite Upgrade Smithing Template");
    }

    @Override
    public boolean isSupportedOn(GameVersion protocolId) {
        return protocolId.getProtocol() >= ProtocolInfo.v1_19_80;
    }
}
